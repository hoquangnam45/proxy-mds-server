package com.txtech.mds.server.pojo;

import com.txtech.mds.server.constant.HandshakeStrategy;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MdsContextConfig {
    private String name;
    private int port;
    private Integer heartbeatIntervalInMs;
    private String version;
    private HandshakeStrategy handshakeStrategy;
    private GrpcConfig grpc;
}
