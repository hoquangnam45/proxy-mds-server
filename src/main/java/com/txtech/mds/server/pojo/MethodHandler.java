package com.txtech.mds.server.pojo;

import io.grpc.stub.StreamObserver;

public interface MethodHandler<T, V> {
    V apply(T arg) throws Throwable;
}
