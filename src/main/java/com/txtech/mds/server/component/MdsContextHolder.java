package com.txtech.mds.server.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.SchemaKeyword;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.txtech.mds.api.listener.MdsMarketDataListenerInterface;
import com.txtech.mds.server.config.MdsConfigProperties;
import com.txtech.mds.server.pojo.GrpcConfig;
import com.txtech.mds.server.pojo.GrpcMethod;
import com.txtech.mds.server.pojo.GrpcService;
import com.txtech.mds.server.pojo.IPublisher;
import com.txtech.mds.server.pojo.MdsContext;
import com.txtech.mds.server.pojo.MdsContextConfig;
import com.txtech.mds.server.pojo.MdsMessageContainer;
import com.txtech.mds.server.pojo.MdsPayload;
import com.txtech.mds.server.pojo.MethodHandler;
import com.txtech.mds.server.proxy.ProxyMdsHandshaker;
import com.txtech.mds.server.proxy.ProxyMdsHeartbeater;
import com.txtech.mds.server.proxy.ProxyMdsSerializer;
import com.txtech.mds.server.util.ClassLoaders;
import com.txtech.mds.server.util.FileDescriptors;
import com.txtech.mds.server.util.StreamUtils;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.txtech.mds.server.util.StreamUtils.toIterable;

@Component
@Getter
public class MdsContextHolder {
    private static final String MESSAGE_TYPES_PACKAGE = "com.txtech.mds.msg.type";
    private static final Logger logger = LoggerFactory.getLogger(MdsContextHolder.class);
    private final Map<String, MdsContext> contexts;
    private final Map<String, MdsServerSocketController> socketControllers;
    private final Map<String, MdsContextConfig> allowedContexts;
    private final JsonSchemaGenerator jsonSchemaGenerator;
    private final ProtoSchemaGenerator protoSchemaGenerator;

    @Autowired
    @SuppressWarnings("unchecked")
    public MdsContextHolder(
            MdsConfigProperties mdsConfig,
            ObjectMapperFactory objectMapperFactory,
            JsonSchemaGenerator jsonSchemaGenerator,
            ProtoSchemaGenerator protoSchemaGenerator) throws IOException, InterruptedException {
        this.allowedContexts = mdsConfig.getContexts().stream().collect(Collectors.toMap(MdsContextConfig::getName, it -> it));

        // Clean-up outputProtoDir
        for (File outputProtoDir : toIterable(allowedContexts.values().stream()
                .map(MdsContextConfig::getGrpc)
                .map(GrpcConfig::getOutputProtoDir)
                .map(File::new)
                .filter(File::exists)
                .filter(File::canWrite))) {
            FileUtils.deleteDirectory(outputProtoDir);
        }

        ObjectMapper objectMapper = objectMapperFactory.get();

        this.jsonSchemaGenerator = jsonSchemaGenerator;
        this.protoSchemaGenerator = protoSchemaGenerator;
        this.socketControllers = new Hashtable<>();
        this.contexts = new Hashtable<>();

        // Build class loaders: context -> class loader
        Map<String, ClassLoader> classLoaderMap = new HashMap<>();
        for (MdsContextConfig contextConfig : mdsConfig.getContexts()) {
            // Why use this class loader
            // If using spring boot fat jar then the system class loader can't access to nested jar inside spring
            // boot executable jar, a recommended approach is to use spring class loader instead by using currentThread::getContextClassLoader
            // Reference: https://github.com/spring-projects/spring-boot/issues/4375
            classLoaderMap.put(contextConfig.getName(), Thread.currentThread().getContextClassLoader());
        }

        classLoaderMap.forEach((contextName, contextCl) -> {
            try {
                Class<?> msgBaseMessageClazz = contextCl.loadClass("com.txtech.mds.msg.type.MsgBaseMessage");
                // Build map: class -> interface class
                Map<Class<?>, Set<Class<?>>> messageTypes = ClassLoaders.findAllClassesUsingClassLoader(contextCl, MESSAGE_TYPES_PACKAGE)
                        .stream()
                        .filter(c -> {
                            int modifiers = c.getModifiers();
                            return !Modifier.isAbstract(modifiers) && !Modifier.isInterface(modifiers) && msgBaseMessageClazz.isAssignableFrom(c);
                        }).collect(Collectors.toMap(
                                it -> it,
                                it -> Stream.of(ClassUtils.getAllInterfacesForClass(it, contextCl)).collect(Collectors.toSet())));
                Class<?> listenerInterfaceClazz = contextCl.loadClass("com.txtech.mds.api.listener.MdsMarketDataListenerInterface");
                Set<Class<?>> listenerTypes = Stream.of(listenerInterfaceClazz.getDeclaredMethods())
                        .filter(m -> m.getParameterCount() == 1)
                        .filter(m -> m.getReturnType().equals(Void.TYPE))
                        .filter(m -> !Modifier.isStatic(m.getModifiers()))
                        .map(m -> m.getParameterTypes()[0])
                        .collect(Collectors.toSet());

                // build map: interface -> implemented classes
                Map<Class<?>, Set<Class<?>>> implementedClasses = messageTypes.entrySet().stream()
                        .flatMap(e -> e.getValue().stream()
                                .filter(listenerTypes::contains)
                                .map(interfaceClass -> new AbstractMap.SimpleEntry<>(interfaceClass, e.getKey())))
                        .collect(Collectors.groupingBy(
                                AbstractMap.SimpleEntry::getKey,
                                Collectors.mapping(it -> (Class<?>) it.getValue(), Collectors.toSet())));
                if (implementedClasses.size() != listenerTypes.size()) {
                    listenerTypes.stream()
                            .filter(type -> !implementedClasses.containsKey(type))
                            .map(type -> MessageFormat.format("Interface {0} used in receiver method of class {1}, but no implementation class for interface can be found", type.getName(), MdsMarketDataListenerInterface.class.getCanonicalName()))
                            .forEach(logger::warn);
                }

                // build map: interface class name -> implemented class name -> implemented class
                Map<String, Map<String, Class<?>>> schemaClasses = implementedClasses.entrySet().stream().collect(Collectors.toMap(
                        e -> e.getKey().getName(),
                        e -> e.getValue().stream().collect(Collectors.toMap(
                                Class::getName,
                                cl -> cl))));

                // Build json schema map: interface -> implemented class -> schema
                Map<String, Map<String, ObjectNode>> jsonSchemas = schemaClasses.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().entrySet().stream().collect(Collectors.toMap(
                                Map.Entry::getKey,
                                innerEntry -> (ObjectNode) jsonSchemaGenerator.getSchema(MdsMessageContainer.class, innerEntry.getValue())))));

                // Build proto type map: interface -> implemented class -> (entryType, scope/package, schemas)
                Map<String, Map<String, Triple<String, String, Map<String, String>>>> protoImplementedTypeSchemas = jsonSchemas.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().entrySet().stream().collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        innerEntry -> {
                                            try {
                                                ObjectNode definitionNode = (ObjectNode) innerEntry.getValue().get(jsonSchemaGenerator.toString(SchemaKeyword.TAG_DEFINITIONS));
                                                Map<String, ObjectNode> definitions = StreamUtils.streamObjectNode(definitionNode).collect(Collectors.toMap(
                                                        Map.Entry::getKey,
                                                        iinnerEntry -> (ObjectNode) iinnerEntry.getValue(),
                                                        (prev, next) -> {
                                                            throw new IllegalStateException("Json schema should not have duplicated definition");
                                                        },
                                                        LinkedHashMap::new
                                                ));
                                                String implementedClass = innerEntry.getKey();
                                                String scope = implementedClass;
                                                String entryType = "MdsMessageContainer";
                                                definitions.put(entryType, innerEntry.getValue());
                                                return Triple.of(entryType, scope, protoSchemaGenerator.generateTypeSchema(definitions));
                                            } catch (InvalidProtocolBufferException e) {
                                                throw new RuntimeException(e);
                                            }
                                        })
                                )
                        )
                );

                // Build proto service map: interface -> service
                Map<String, GrpcService> grpcServices = protoImplementedTypeSchemas.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            String interfaceClass = entry.getKey();
                            GrpcService grpcService = new GrpcService(getProtoServiceName(interfaceClass), new ArrayList<>());
                            entry.getValue().entrySet().stream()
                                    .map(innerEntry -> {
                                        String implementedClass = innerEntry.getKey();
                                        String scope = innerEntry.getValue().getMiddle();
                                        String entryType = innerEntry.getValue().getLeft();
                                        return new GrpcMethod(
                                                getPublishProtoMethodName(implementedClass),
                                                scope + "." + entryType,
                                                "google.protobuf.Empty",
                                                Pair.of(interfaceClass, implementedClass));
                                    })
                                    .forEach(grpcService.getMethods()::add);
                            return grpcService;
                        }
                ));

                Map<String, String> protoServiceSchemas = grpcServices.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> protoSchemaGenerator.generateProtoService(entry.getValue())));

                MdsContextConfig contextConfig = allowedContexts.get(contextName);
                // Write proto to output proto dir
                // Create proto type and service in output proto directory
                // interface -> (serviceProtoFile, serviceDescriptorFile, map: implemented class -> typeProtoFile)
                Map<String, Triple<File, File, Map<String, File>>> outputProtoDirMap = protoImplementedTypeSchemas.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            String interfaceClass = entry.getKey();
                            File servicesOutputDir = new File(Paths.get(contextConfig.getGrpc().getOutputProtoDir()).normalize().toFile(), "services");
                            File serviceOutputDir = new File(servicesOutputDir, interfaceClass);
                            File protoOutputDir = new File(serviceOutputDir, "proto");
                            File typesOutputDir = new File(protoOutputDir, "types");
                            File descriptorOutputDir = new File(serviceOutputDir, "descriptor");
                            if (!descriptorOutputDir.mkdirs() || !typesOutputDir.mkdirs()) {
                                throw new IllegalStateException("Output folder can not be created for " + interfaceClass);
                            }

                            try {
                                File outputServiceProtoFile = new File(protoOutputDir, interfaceClass + ".proto");
                                File outputServiceDescriptorFile = new File(descriptorOutputDir, interfaceClass + ".desc");

                                if (!outputServiceProtoFile.createNewFile() || !outputServiceDescriptorFile.createNewFile()) {
                                    throw new IllegalStateException("Not all output service file can be created " + interfaceClass);
                                }

                                Map<String, File> outputImplementedClassTypeProtoFileMap = new HashMap<>();
                                for (String implementedClass : entry.getValue().keySet()) {
                                    File outputImplementedClassTypeProtoFile = new File(typesOutputDir, implementedClass + ".proto");
                                    if (!outputImplementedClassTypeProtoFile.createNewFile()) {
                                        throw new IllegalStateException(MessageFormat.format("Output folder can not be created for {0}[{1}]", interfaceClass, implementedClass));
                                    }
                                    outputImplementedClassTypeProtoFileMap.put(implementedClass, outputImplementedClassTypeProtoFile);
                                }
                                return Triple.of(outputServiceProtoFile, outputServiceDescriptorFile, outputImplementedClassTypeProtoFileMap);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                ));

                outputProtoDirMap.forEach((interfaceClass, innerEntry) -> {
                    File protoServiceOutputFile = innerEntry.getLeft();
                    try {
                        List<String> importPaths = new ArrayList<>();
                        for (Map.Entry<String, File> protoImplementedClassTypeEntry : innerEntry.getRight().entrySet()) {
                            File protoImplementedClassType = protoImplementedClassTypeEntry.getValue();
                            String implementedClass = protoImplementedClassTypeEntry.getKey();
                            String scope = protoImplementedTypeSchemas
                                    .get(interfaceClass)
                                    .get(implementedClass)
                                    .getMiddle();
                            try (FileWriter fw = new FileWriter(protoImplementedClassType);
                                 BufferedWriter bw = new BufferedWriter(fw)) {
                                bw.write(String.join("\n\n",
                                        "syntax = \"proto3\";",
                                        MessageFormat.format("package {0};", scope),
                                        String.join("\n",
                                                "import public \"google/protobuf/empty.proto\";",
                                                "import public \"google/protobuf/any.proto\";"),
                                        String.join("\n\n", protoImplementedTypeSchemas
                                                .get(interfaceClass)
                                                .get(implementedClass)
                                                .getRight()
                                                .values()))
                                );
                            }

                            // Fix import path as windows will use backward slash while proto import use forward slash
                            importPaths.add(protoServiceOutputFile.toPath().getParent().relativize(protoImplementedClassType.toPath()).toString().replace("\\", "/"));
                        }

                        try (FileWriter fw = new FileWriter(protoServiceOutputFile);
                             BufferedWriter bw = new BufferedWriter(fw)) {
                            bw.write(String.join("\n\n",
                                    "syntax = \"proto3\";",
                                    importPaths.stream().map(path -> MessageFormat.format("import \"{0}\";", path)).collect(Collectors.joining("\n")),
                                    protoServiceSchemas.get(interfaceClass)));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                // Compile proto file to proto descriptor file using proto runner
                for (Triple<File, File, Map<String, File>> triple :outputProtoDirMap.values()) {
                    File serviceProtoFile = triple.getLeft();
                    File serviceDescriptorFile = triple.getMiddle();
                    if (new ProtoRunner(serviceDescriptorFile, serviceProtoFile).run() != 0) {
                        logger.error("Proto runner failed to process proto file " + serviceProtoFile);
                    }
                }

                // Load descriptor file and build map: interface -> service file descriptor
                Map<String, Descriptors.FileDescriptor> serviceFileDescriptors = outputProtoDirMap.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> {
                        File serviceDescriptorFile = entry.getValue().getMiddle();
                        File serviceProtoFile = entry.getValue().getLeft();
                        try (FileInputStream fis = new FileInputStream(serviceDescriptorFile);
                             BufferedInputStream bis = new BufferedInputStream(fis)) {
                            DescriptorProtos.FileDescriptorSet fileDescriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(bis);
                            return FileDescriptors.buildFrom(fileDescriptorSet, serviceProtoFile.getName());
                        } catch (IOException | Descriptors.DescriptorValidationException e) {
                            throw new RuntimeException(e);
                        }
                    }
                ));

                ///////////////////////////////////////////////////////////////
                startContext(contexts.computeIfAbsent(contextName, k -> new MdsContext(
                        contextName,
                        new ProxyMdsSerializer(contextName),
                        schemaClasses,
                        contextConfig.getHandshakeStrategy(),
                        new ProxyMdsHandshaker(),
                        new ProxyMdsHeartbeater(),
                        jsonSchemas,
                        serviceFileDescriptors,
                        contextConfig,
                        objectMapper,
                        grpcServices)));
            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private String getProtoServiceName(String interfaceClass) {
        return interfaceClass.substring(interfaceClass.lastIndexOf(".") + 1);
    }

    private String getProtoTypeName(String className) {
        return className.substring(className.lastIndexOf(".") + 1);
    }

    private String getPublishProtoMethodName(String className) {
        return "publish" + className.substring(className.lastIndexOf(".") + 1);
    }

    @SuppressWarnings("unchecked")
    public void startContext(MdsContext mdsContext) throws IOException, InterruptedException {
        if (Optional.ofNullable(socketControllers.get(mdsContext.getName()))
                .map(MdsServerSocketController::isStarted)
                .orElse(false)) {
            return;
        }

        // Start proxy mds socket at specified port
        ServerSocket serverSocket = new ServerSocket(mdsContext.getConfig().getPort());
        MdsServerSocketController mdsSocketController = new MdsServerSocketController(
                serverSocket,
                mdsContext,
                jsonSchemaGenerator,
                mdsContext.getConfig().getVersion());
        mdsSocketController.start();
        socketControllers.put(mdsContext.getName(), mdsSocketController);

        // Start grpc service
        // Build dynamic grpc service
        IPublisher<MdsPayload<Message>> publisher = new GrpcMdsPublisher(
                JsonFormat.printer(),
                mdsContext.getObjectMapper(),
                new JsonMdsPublisher(
                        mdsContext.getSchemaClasses(),
                        mdsContext.getObjectMapper(),
                        mdsSocketController));

        // Build handler map: service -> method -> handler
        Map<String, Map<String, MethodHandler<Message, Message>>> methodHandlers = mdsContext.getGrpcServices().values().stream().collect(Collectors.toMap(
                GrpcService::getServiceName,
                service -> service.getMethods().stream().collect(Collectors.toMap(
                        GrpcMethod::getMethodName,
                        method -> req -> {
                            Pair<String, String> context = (Pair<String, String>) method.getContext();
                            String interfaceClass = context.getKey();
                            String implementedClass = context.getValue();
                            publisher.publish(new MdsPayload<>(interfaceClass, implementedClass, req));
                            return Empty.getDefaultInstance();
                        }
                ))
        ));
        MdsGrpcDynamicServices mdsGrpcDynamicServices = new MdsGrpcDynamicServices(
                mdsContext.getConfig().getGrpc().getPort(),
                mdsContext.getGrpcServices(),
                mdsContext.getServiceFileDescriptors(),
                methodHandlers);
        mdsGrpcDynamicServices.getServer().start();
    }
}
