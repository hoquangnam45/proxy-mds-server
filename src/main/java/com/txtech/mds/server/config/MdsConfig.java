package com.txtech.mds.server.config;

import com.txtech.mds.server.component.ObjectMapperFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MdsConfig {
    public ObjectMapperFactory objectMapperFactory() {
        return new ObjectMapperFactory();
    }
}
