package com.txtech.mds.server.pojo;

public interface ConsumerEx<T> {
    void accept(T data) throws Exception;
}
