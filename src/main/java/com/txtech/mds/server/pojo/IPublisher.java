package com.txtech.mds.server.pojo;

import com.fasterxml.jackson.databind.JsonNode;

public interface IPublisher<T> {
    void publish(T payload) throws Exception;
}
