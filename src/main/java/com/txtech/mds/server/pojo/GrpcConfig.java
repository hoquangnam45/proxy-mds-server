package com.txtech.mds.server.pojo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrpcConfig {
    private int port;
    private String outputProtoDir;
}
