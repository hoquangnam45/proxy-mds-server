package com.txtech.mds.server.component;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.txtech.mds.server.component.MapDeserializer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class ObjectMapperFactory implements Supplier<ObjectMapper> {
    @Override
    public ObjectMapper get() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        // Allow object mappers to deserialize to map type from array json node
        SimpleModule module = new SimpleModule();
        module.setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyMapDeserializer(DeserializationConfig config, MapType type, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
                return new MapDeserializer(config, type, beanDesc, deserializer);
            }
        });
        objectMapper.registerModule(module);
        return objectMapper;
    }
}
