package com.txtech.mds.server.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.txtech.mds.api.listener.MdsMarketDataListenerInterface;
import com.txtech.mds.server.pojo.MdsContext;
import com.txtech.mds.server.pojo.MdsMessageContainer;
import com.txtech.mds.server.util.ClassLoaders;
import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.server.config.MdsConfigProperties;
import com.txtech.mds.server.pojo.MdsContextConfig;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.text.MessageFormat;

import com.txtech.mds.server.proxy.ProxyMdsHandshaker;
import com.txtech.mds.server.proxy.ProxyMdsHeartbeater;
import com.txtech.mds.server.proxy.ProxyMdsSerializer;
import lombok.Getter;
import org.apache.commons.lang3 .ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Getter
public class MdsContextHolder {
    private static final String MESSAGE_TYPES_PACKAGE = "com.txtech.mds.msg.type";
    private static final Logger logger = LoggerFactory.getLogger(MdsContextHolder.class);
    private final Map<String, MdsContext> contexts = new Hashtable<>();
    private final Map<String, MdsServerSocketController> socketControllers = new Hashtable<>();
    private final Map<String, MdsContextConfig> allowedContexts;
    private final JsonSchemaGenerator schemaGenerator;

    @Autowired
    @SuppressWarnings("unchecked")
    public MdsContextHolder(
            MdsConfigProperties mdsConfig,
            ObjectMapperFactory objectMapperFactory,
            JsonSchemaGenerator schemaGenerator) throws IOException {
        this.allowedContexts = mdsConfig.getContexts().stream().collect(Collectors.toMap(MdsContextConfig::getName, it -> it));
        this.schemaGenerator = schemaGenerator;
        Map<Class<?>, Set<Class<?>>> messageTypes = ClassLoaders.findAllClassesUsingClassLoader(MESSAGE_TYPES_PACKAGE)
                .stream()
                .filter(c -> {
                    int modifiers = c.getModifiers();
                    return !Modifier.isAbstract(modifiers) && !Modifier.isInterface(modifiers) && MsgBaseMessage.class.isAssignableFrom(c);
                }).collect(Collectors.toMap(
                        it -> it,
                        it -> new HashSet<>(ClassUtils.getAllInterfaces(it))));
        Set<Class<?>> listenerTypes = Stream.of(MdsMarketDataListenerInterface.class.getDeclaredMethods())
                .filter(m -> m.getParameterCount() == 1)
                .filter(m -> m.getReturnType().equals(Void.TYPE))
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .map(m -> m.getParameterTypes()[0])
                .collect(Collectors.toSet());
        Map<Class<?>, Set<Class<? extends MsgBaseMessage>>> implementedClasses = messageTypes.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .filter(listenerTypes::contains)
                        .map(interfaceClass -> new AbstractMap.SimpleEntry<>(interfaceClass, e.getKey())))
                .collect(Collectors.groupingBy(
                        AbstractMap.SimpleEntry::getKey,
                        Collectors.mapping(it -> (Class<? extends MsgBaseMessage>) it.getValue(), Collectors.toSet())));
        if (implementedClasses.size() != listenerTypes.size()) {
            listenerTypes.stream()
                    .filter(type -> !implementedClasses.containsKey(type))
                    .map(type -> MessageFormat.format("Interface {0} exist in listener method of {1}, but no implementation class found", type.getName(), MdsMarketDataListenerInterface.class.getCanonicalName()))
                    .forEach(logger::warn);
        }
        Map<String, Map<String, Class<? extends MsgBaseMessage>>> schemaClasses = implementedClasses.entrySet().stream().collect(Collectors.toMap(
                e -> e.getKey().getName(),
                e -> e.getValue().stream().collect(Collectors.toMap(
                        Class::getName,
                        cl -> cl))));
        Map<String, Map<String, JsonNode>> schemas = schemaClasses.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        innerEntry -> schemaGenerator.getSchema(MdsMessageContainer.class, innerEntry.getValue())))));
        for (Map.Entry<String, MdsContextConfig> entry: allowedContexts.entrySet()) {
            String contextName = entry.getKey();
            MdsContextConfig config = entry.getValue();
            MdsContext context = new MdsContext(contextName, new ProxyMdsSerializer(contextName), schemaClasses, config.getHandshakeStrategy(), new ProxyMdsHandshaker(), new ProxyMdsHeartbeater(), schemas, config, objectMapperFactory.get());
            startContext(context);
            contexts.put(contextName, context);
        }
    }

    public void startContext(MdsContext mdsContext) throws IOException {
        if (Optional.ofNullable(socketControllers.get(mdsContext.getName()))
                .map(MdsServerSocketController::isStarted)
                .orElse(false)) {
            return;
        }

        // Start proxy mds socket at specified port
        ServerSocket serverSocket= new ServerSocket(mdsContext.getConfig().getPort());
        MdsServerSocketController mdsSocketController = new MdsServerSocketController(
                serverSocket,
                mdsContext,
                schemaGenerator,
                mdsContext.getConfig().getVersion());
        mdsSocketController.start();
        socketControllers.put(mdsContext.getName(), mdsSocketController);
    }
}
