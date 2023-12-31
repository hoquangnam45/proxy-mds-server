package com.txtech.mds.server.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class MalformedMapJsonException extends JsonProcessingException {
    public MalformedMapJsonException(String msg) {
        super(msg);
    }
}
