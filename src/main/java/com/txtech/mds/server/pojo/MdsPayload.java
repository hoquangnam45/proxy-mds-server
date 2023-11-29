package com.txtech.mds.server.pojo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class MdsPayload<T> {
    private final String interfaceClass;
    private final String implementedClass;
    private final T payload;
}
