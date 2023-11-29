package com.txtech.mds.server.pojo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Getter
public class GrpcService {
    private final String serviceName;
    private final List<GrpcMethod> methods;
}
