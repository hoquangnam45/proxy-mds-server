package com.txtech.mds.server.component;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeBindings;
import com.txtech.mds.server.exception.MalformedMapJsonException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class MapDeserializer extends JsonDeserializer<Map<?, ?>> {
    private final MapType type;
    private final DeserializationConfig config;
    private final JsonDeserializer<?> deserializer;
    private final BeanDescription beanDesc;

    public MapDeserializer(DeserializationConfig config, MapType type, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
        this.config = config;
        this.type = type;
        this.beanDesc = beanDesc;
        this.deserializer = deserializer;
    }

    @Override
    public Map<?, ?> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        JsonNode node = jsonParser.readValueAsTree();
        switch (node.getNodeType()) {
            case MISSING:
            case NULL:
                return null;
            case ARRAY:
                return readFromStream(StreamSupport.stream(node.spliterator(), false)
                        .map(n -> (ObjectNode) n)
                        .peek(n -> Optional.ofNullable(n.get("key")).orElseThrow(() -> new RuntimeException(new MalformedMapJsonException("Map can't have null key"))))
                        .map(n -> new AbstractMap.SimpleEntry<>(n.get("key").asText(), n.get("value"))),
                        type,
                        deserializationContext);
            case OBJECT:
                return readFromStream(StreamSupport.stream(Spliterators.spliteratorUnknownSize(node.fields(), Spliterator.ORDERED), false),
                        type,
                        deserializationContext);
            default:
                return (Map<?, ?>) deserializer.deserialize(jsonParser, deserializationContext);
        }
    }

    private Map<Object, Object> readFromStream(Stream<Map.Entry<String, JsonNode>> stream, MapType mapType, DeserializationContext deserializationContext) throws IOException {
        Map<Object, Object> returnedMap = new LinkedHashMap<>();
        TypeBindings typeBindings = mapType.getBindings();
        List<JavaType> typeParameters = typeBindings.getTypeParameters();
        for (Map.Entry<String, JsonNode> entry : (Iterable<? extends Map.Entry<String, JsonNode>>) stream::iterator) {
            JavaType keyType = typeParameters.get(0);
            JavaType valueType = typeParameters.get(1);
            Object key = deserializationContext.readTreeAsValue(new TextNode(entry.getKey()), keyType);
            Object value = deserializationContext.readTreeAsValue(entry.getValue(), valueType);
            returnedMap.put(key, value);
        }
        return returnedMap;
    }
}
