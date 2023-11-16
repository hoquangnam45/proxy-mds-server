package com.txtech.mds.server.pojo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.server.constant.HandshakeStrategy;
import com.txtech.mds.server.proxy.IHandshaker;
import com.txtech.mds.server.proxy.IHeartbeater;
import com.txtech.mds.server.proxy.ISerializer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.nio.ByteBuffer;
import java.util.Map;

@RequiredArgsConstructor
@Getter
public class MdsContext implements IContext<ByteBuffer, MsgBaseMessage> {
    private final String name;
    private final ISerializer<ByteBuffer, MsgBaseMessage> serializer;
    private final Map<String, Map<String, Class<? extends MsgBaseMessage>>> schemaClasses;
    private final HandshakeStrategy handshakeStrategy;
    private final IHandshaker<MsgBaseMessage> handshaker;
    private final IHeartbeater<MsgBaseMessage> heartbeater;
    private final Map<String, Map<String, JsonNode>> schemas;
    private final MdsContextConfig config;
    private final ObjectMapper objectMapper;
}
