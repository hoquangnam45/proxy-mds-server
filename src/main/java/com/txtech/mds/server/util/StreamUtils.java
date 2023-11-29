package com.txtech.mds.server.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamUtils {
    public static Stream<JsonNode> streamArrayNode(ArrayNode node) {
        return Optional.ofNullable(node)
                .map(n -> StreamSupport.stream(Spliterators.spliteratorUnknownSize(n.elements(), Spliterator.ORDERED), false))
                .orElseGet(Stream::empty);
    }

    public static Stream<Map.Entry<String, JsonNode>> streamObjectNode(ObjectNode node) {
        return Optional.ofNullable(node)
                .map(n -> StreamSupport.stream(Spliterators.spliteratorUnknownSize(n.fields(), Spliterator.ORDERED), false))
                .orElseGet(Stream::empty);
    }

    public static <T> Iterable<T> toIterable(Stream<T> stream) {
        return stream::iterator;
    }
}
