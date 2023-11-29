package com.txtech.mds.server.pojo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GrpcMethod {
    private final String methodName;
    private final String inputType;
    private final String outputType;
    private final Object context;
}
