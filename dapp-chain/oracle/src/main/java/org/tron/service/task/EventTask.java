package org.tron.service.task;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Arrays;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.tron.common.config.Args;
import org.tron.common.logger.LoggerOracle;
import org.tron.common.utils.ByteArray;
import org.tron.db.Manager;
import org.tron.db.NonceStore;
import org.tron.protos.Sidechain.NonceMsg;
import org.tron.protos.Sidechain.NonceMsg.NonceStatus;
import org.tron.service.eventactuator.Actuator;
import org.tron.service.eventactuator.EventActuatorFactory;
import org.tron.service.kafka.KfkConsumer;

@Slf4j(topic = "eventTask")
public class EventTask {

  private static final LoggerOracle loggerOracle = new LoggerOracle(logger);

  private KfkConsumer kfkConsumer;

  public EventTask() {
    Args args = Args.getInstance();
    this.kfkConsumer = new KfkConsumer(args.getMainchainKafka(),
        "Oracle_" + args.getOracleAddress(), Arrays.asList("contractevent"));
  }

  public void processEvent() {
    for (; ; ) {
      ConsumerRecords<String, String> record = this.kfkConsumer.getRecord();
      for (ConsumerRecord<String, String> key : record) {
        JSONObject obj = (JSONObject) JSONValue.parse(key.value());

        Actuator eventActuator = EventActuatorFactory.CreateActuator(obj);
        if (Objects.isNull(eventActuator)) {
          //Unrelated contract or event
          this.kfkConsumer.commit();
          continue;
        }

        byte[] nonceMsgBytes = NonceStore.getInstance().getData(eventActuator.getNonceKey());
        if (nonceMsgBytes == null) {
          // receive this nonce firstly
          Manager.getInstance().setProcessProcessing(eventActuator.getNonceKey(),
              eventActuator.getMessage().toByteArray());
          CreateTransactionTask.getInstance().submitCreate(eventActuator);
        } else {
          try {
            NonceMsg nonceMsg = NonceMsg.parseFrom(nonceMsgBytes);
            if (nonceMsg.getStatus() == NonceStatus.SUCCESS) {
              loggerOracle.info("the nonce {} has be processed successfully",
                  ByteArray.toStr(eventActuator.getNonce()));
            } else if (nonceMsg.getStatus() == NonceStatus.FAIL) {
              Manager.getInstance().setProcessProcessing(eventActuator.getNonceKey(),
                  eventActuator.getMessage().toByteArray());
              CreateTransactionTask.getInstance().submitCreate(eventActuator);
            } else {
              // processing
              if (System.currentTimeMillis() / 1000 >= nonceMsg.getNextProcessTimestamp()) {
                Manager.getInstance().setProcessProcessing(eventActuator.getNonceKey(),
                    eventActuator.getMessage().toByteArray());
                CreateTransactionTask.getInstance().submitCreate(eventActuator);
              } else {
                loggerOracle.info("the nonce {} is processing",
                    ByteArray.toStr(eventActuator.getNonce()));
              }
            }
          } catch (InvalidProtocolBufferException e) {
            loggerOracle.error("retry fail: {}", e.getMessage(), e);
          }
        }
        this.kfkConsumer.commit();
      }
    }
  }
}
