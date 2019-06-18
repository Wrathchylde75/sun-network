package org.tron.service.eventactuator.sidechain;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Objects;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tron.client.SideChainGatewayApi;
import org.tron.common.exception.RpcConnectException;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.WalletUtil;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.Sidechain.EventMsg;
import org.tron.protos.Sidechain.EventMsg.EventType;
import org.tron.protos.Sidechain.TaskEnum;
import org.tron.protos.Sidechain.WithdrawTRC721Event;
import org.tron.service.check.TransactionExtensionCapsule;
import org.tron.service.eventactuator.Actuator;

@Slf4j(topic = "sideChainTask")
public class WithdrawTRC721Actuator extends Actuator {

  // "event WithdrawTRC721(address from, uint256 tokenId, address mainChainAddress, bytes memory userSign);"

  private WithdrawTRC721Event event;
  @Getter
  private EventType type = EventType.WITHDRAW_TRC721_EVENT;

  public WithdrawTRC721Actuator(String nonce, String from, String tokenId, String mainChainAddress,
      String userSign) {
    ByteString nonceBS = ByteString.copyFrom(ByteArray.fromString(nonce));
    ByteString fromBS = ByteString.copyFrom(WalletUtil.decodeFromBase58Check(from));
    ByteString tokenIdBS = ByteString.copyFrom(ByteArray.fromString(tokenId));
    ByteString mainChainAddressBS = ByteString
        .copyFrom(WalletUtil.decodeFromBase58Check(mainChainAddress));
    ByteString userSignBS = ByteString.copyFrom(ByteArray.fromHexString(userSign));
    this.event = WithdrawTRC721Event.newBuilder().setNonce(nonceBS).setFrom(fromBS)
        .setTokenId(tokenIdBS)
        .setMainchainAddress(mainChainAddressBS).setUserSign(userSignBS).build();
  }

  public WithdrawTRC721Actuator(EventMsg eventMsg) throws InvalidProtocolBufferException {
    this.event = eventMsg.getParameter().unpack(WithdrawTRC721Event.class);
  }

  @Override
  public TransactionExtensionCapsule createTransactionExtensionCapsule()
      throws RpcConnectException {
    if (Objects.nonNull(transactionExtensionCapsule)) {
      return this.transactionExtensionCapsule;
    }
    String nonceStr = event.getNonce().toStringUtf8();
    String fromStr = WalletUtil.encode58Check(event.getFrom().toByteArray());
    String tokenIdStr = event.getTokenId().toStringUtf8();
    String mainChainAddressStr = WalletUtil
        .encode58Check(event.getMainchainAddress().toByteArray());
    String userSignStr = ByteArray.toHexString(event.getUserSign().toByteArray());

    logger
        .info(
            "WithdrawTRC721Actuator, nonce: {}, from: {}, tokenId: {}, mainChainAddress: {}, userSign: {}",
            nonceStr, fromStr, tokenIdStr, mainChainAddressStr, userSignStr);
    Transaction tx = SideChainGatewayApi
        .withdrawTRC721Transaction(fromStr, mainChainAddressStr, tokenIdStr, userSignStr,
            nonceStr);
    this.transactionExtensionCapsule = new TransactionExtensionCapsule(TaskEnum.SIDE_CHAIN,
        nonceStr, tx);

    return this.transactionExtensionCapsule;
  }

  @Override
  public EventMsg getMessage() {
    return EventMsg.newBuilder().setParameter(Any.pack(this.event)).setType(getType()).build();
  }

  @Override
  public byte[] getKey() {
    return event.getNonce().toByteArray();
  }

  @Override
  public byte[] getNonce() {
    return event.getNonce().toByteArray();
  }
}
