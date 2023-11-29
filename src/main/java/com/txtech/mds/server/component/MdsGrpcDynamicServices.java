package com.txtech.mds.server.component;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.txtech.mds.server.pojo.GrpcService;
import com.txtech.mds.server.pojo.MethodHandler;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.ServerCalls;
import lombok.Getter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Inspired by: https://stackoverflow.com/questions/61133529/how-to-create-grpc-client-directly-from-protobuf-without-compiling-it-into-java/65641262#65641262
@Getter
public class MdsGrpcDynamicServices {
    private final Server server;

    public MdsGrpcDynamicServices(
            int port,
            Map<String, GrpcService> services,
            Map<String, Descriptors.FileDescriptor> serviceFileDescriptors,
            Map<String, Map<String, MethodHandler<Message, Message>>> methodHandlers) {
        ServerBuilder<?> serverBuilder = ServerBuilder.forPort(port);
        serverBuilder.addService(ProtoReflectionService.newInstance());
        for (Map.Entry<String, GrpcService> entry : services.entrySet()) {
            GrpcService service = entry.getValue();
            String interfaceClass = entry.getKey();

            Descriptors.ServiceDescriptor serviceProtoDescriptor = serviceFileDescriptors.get(interfaceClass).findServiceByName(service.getServiceName());

            List<MethodDescriptor<Message, Message>> methodDescriptors = serviceProtoDescriptor.getMethods().stream()
                    .map(method -> MethodDescriptor.newBuilder(ProtoUtils.marshaller(defaultInstance(method.getInputType())), ProtoUtils.marshaller(defaultInstance(method.getOutputType())))
                            .setSafe(false)
                            .setIdempotent(false)
                            .setSampledToLocalTracing(false)
                            .setType(getMethodType(method))
                            .setFullMethodName(method.getService().getFullName() + "/" + method.getName())
                            .build())
                    .collect(Collectors.toList());

            ServiceDescriptor.Builder serviceDescriptorBuilder = ServiceDescriptor.newBuilder(service.getServiceName())
                    .setSchemaDescriptor((ProtoFileDescriptorSupplier) serviceProtoDescriptor::getFile);
            methodDescriptors.forEach(serviceDescriptorBuilder::addMethod);
            ServiceDescriptor serviceDescriptor = serviceDescriptorBuilder.build();

            ServerServiceDefinition.Builder serviceDefinitionBuilder = ServerServiceDefinition.builder(serviceDescriptor);
            methodDescriptors.forEach(method -> {
                        if (method.getType() != MethodDescriptor.MethodType.UNARY) {
                            throw new UnsupportedOperationException("Do not support other type of method beside unary yet");
                        }
                        serviceDefinitionBuilder.addMethod(method, ServerCalls.asyncUnaryCall((req, observer) -> {
                            try {
                                observer.onNext(methodHandlers.get(service.getServiceName()).get(method.getBareMethodName()).apply(req));
                                observer.onCompleted();
                            } catch (Throwable e) {
                                observer.onError(e);
                            }
                        }));
                    }
            );

            serverBuilder.addService(serviceDefinitionBuilder.build());
        }
        this.server = serverBuilder.build();
    }

    private MethodDescriptor.MethodType getMethodType(Descriptors.MethodDescriptor method) {
        boolean isClientStreaming = method.isClientStreaming();
        boolean isServerStreaming = method.isServerStreaming();
        if (isServerStreaming && isClientStreaming) {
            return MethodDescriptor.MethodType.BIDI_STREAMING;
        } else if (isServerStreaming) {
            return MethodDescriptor.MethodType.SERVER_STREAMING;
        } else if (isClientStreaming) {
            return MethodDescriptor.MethodType.CLIENT_STREAMING;
        } else {
            return MethodDescriptor.MethodType.UNARY;
        }
    }

    public Message defaultInstance(Descriptors.Descriptor type) {
        return DynamicMessage.getDefaultInstance(type);
    }
}
