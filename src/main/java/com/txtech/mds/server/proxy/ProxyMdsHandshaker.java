package com.txtech.mds.server.proxy;

import com.txtech.mds.msg.element.MsgElementConnectResponse;
import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.msg.type.general.MsgHandShake;

import java.util.Optional;

public class ProxyMdsHandshaker {
    public MsgHandShake handshaking(String version) {
        return Optional.of(new MsgHandShake())
                .map(msg -> {
                    msg.setResponse(MsgElementConnectResponse.HandShaking);
                    msg.setVersion(version);
                    msg.setDetail("Proxy MDS Handshaking");
                    return msg;
                }).orElseThrow(() -> new IllegalStateException("Not possible to enter here"));
    }

    public MsgHandShake accept() {
        return Optional.of(new MsgHandShake())
                .map(msg -> {
                    msg.setResponse(MsgElementConnectResponse.Accepted);
                    return msg;
                }).orElseThrow(() -> new IllegalStateException("Not possible to enter here"));    }

    public MsgHandShake timeout() {
        return Optional.of(new MsgHandShake())
                .map(msg -> {
                    msg.setResponse(MsgElementConnectResponse.Timeout);
                    return msg;
                }).orElseThrow(() -> new IllegalStateException("Not possible to enter here"));
    }

    public MsgHandShake incorrectVersion() {
        return Optional.of(new MsgHandShake())
                .map(msg -> {
                    msg.setResponse(MsgElementConnectResponse.IncorrectVersion);
                    return msg;
                }).orElseThrow(() -> new IllegalStateException("Not possible to enter here"));
    }

    public MsgHandShake denied() {
        return Optional.of(new MsgHandShake())
                .map(msg -> {
                    msg.setResponse(MsgElementConnectResponse.Denied);
                    return msg;
                }).orElseThrow(() -> new IllegalStateException("Not possible to enter here"));    }
}
