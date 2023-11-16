package com.txtech.mds.server.pojo;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class GenericResponse {
    private final String path;
    private final String msg;
    private final int status;
    private final Object details;
    private final LocalDateTime timestamp = LocalDateTime.now();

    public GenericResponse(int status, String path, String msg) {
        this.path = path;
        this.msg = msg;
        this.status = status;
        this.details = null;
    }
}
