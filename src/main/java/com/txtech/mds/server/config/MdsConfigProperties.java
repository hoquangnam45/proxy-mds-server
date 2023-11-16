package com.txtech.mds.server.config;

import com.txtech.mds.server.pojo.MdsContextConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "mds")
@Getter
@Setter
public class MdsConfigProperties {
    private List<MdsContextConfig> contexts;
}
