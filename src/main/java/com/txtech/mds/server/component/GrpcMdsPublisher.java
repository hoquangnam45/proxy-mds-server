package com.txtech.mds.server.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.txtech.mds.server.pojo.IPublisher;
import com.txtech.mds.server.pojo.MdsPayload;

public class GrpcMdsPublisher implements IPublisher<MdsPayload<Message>> {
    private final JsonFormat.Printer jsonPrinter;
    private final ObjectMapper objectMapper;
    private final IPublisher<MdsPayload<JsonNode>> jsonPublisher;

    public GrpcMdsPublisher(
            JsonFormat.Printer jsonPrinter,
            ObjectMapper objectMapper,
            IPublisher<MdsPayload<JsonNode>> jsonPublisher) {
        this.jsonPrinter = jsonPrinter;
        this.objectMapper = objectMapper;
        this.jsonPublisher = jsonPublisher;
    }

    @Override
    public void publish(MdsPayload<Message> message) throws Exception {
        jsonPublisher.publish(new MdsPayload<>(
                message.getInterfaceClass(),
                message.getImplementedClass(),
                objectMapper.readTree(jsonPrinter.print(message.getPayload()))));
    }
}
