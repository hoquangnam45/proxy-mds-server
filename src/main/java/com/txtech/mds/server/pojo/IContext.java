package com.txtech.mds.server.pojo;

import com.fasterxml.jackson.databind.JsonNode;
import com.txtech.mds.server.constant.HandshakeStrategy;
import com.txtech.mds.server.proxy.IHandshaker;
import com.txtech.mds.server.proxy.IHeartbeater;
import com.txtech.mds.server.proxy.ISerializer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.util.Map;

public interface IContext<T, V> {
    ISerializer<T, V> getSerializer();
    Map<String, Map<String, Class<? extends V>>> getSchemaClasses();
    HandshakeStrategy getHandshakeStrategy();
    IHandshaker<V> getHandshaker();
    IHeartbeater<V> getHeartbeater();
    Map<String, Map<String, JsonNode>> getSchemas();
}
