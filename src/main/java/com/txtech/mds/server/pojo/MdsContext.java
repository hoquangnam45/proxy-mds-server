package com.txtech.mds.server.pojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Descriptors;
import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.server.constant.HandshakeStrategy;
import com.txtech.mds.server.proxy.ProxyMdsHandshaker;
import com.txtech.mds.server.proxy.ProxyMdsHeartbeater;
import com.txtech.mds.server.proxy.ProxyMdsSerializer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
@Getter
public class MdsContext {
    private final String name;
    private final ProxyMdsSerializer serializer;
    private final Map<String, Map<String, Class<? extends MsgBaseMessage>>> schemaClasses;
    private final HandshakeStrategy handshakeStrategy;
    private final ProxyMdsHandshaker handshaker;
    private final ProxyMdsHeartbeater heartbeater;
    private final Map<String, Map<String, ObjectNode>> jsonSchemas;
    private final Map<String, Descriptors.FileDescriptor> serviceFileDescriptors;
    private final MdsContextConfig config;
    private final ObjectMapper objectMapper;
    private final Map<String, GrpcService> grpcServices;
}
