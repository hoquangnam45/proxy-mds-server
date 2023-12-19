package com.txtech.mds.server.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.msg.type.fix.MsgFIXClosingPrice;
import com.txtech.mds.server.pojo.IPublisher;
import com.txtech.mds.server.pojo.MdsMessageContainer;
import com.txtech.mds.server.pojo.MdsPayload;
import com.txtech.mds.server.util.MdsAttributes;
import org.springframework.security.core.parameters.P;

import java.lang.reflect.Method;
import java.util.Map;

public class JsonMdsPublisher implements IPublisher<MdsPayload<JsonNode>> {
    private final ObjectMapper objectMapper;
    private final IPublisher<MsgBaseMessage> delegatePublisher;
    private final Map<String, Map<String, Class<?>>> schemaClasses;

    public JsonMdsPublisher(Map<String, Map<String, Class<?>>> schemaClasses, ObjectMapper objectMapper, IPublisher<MsgBaseMessage> delegatePublisher) {
        this.objectMapper = objectMapper;
        this.delegatePublisher = delegatePublisher;
        this.schemaClasses = schemaClasses;
    }

    @Override
    public void publish(MdsPayload<JsonNode> payload) throws Exception {
        String implementedClass = payload.getImplementedClass();
        String interfaceClass = payload.getInterfaceClass();
        JsonNode jsonData = payload.getPayload();
        Class<?> schemaClass = schemaClasses.get(interfaceClass).get(implementedClass);
        MdsMessageContainer<?> msgContainer = objectMapper.convertValue(jsonData, objectMapper.getTypeFactory().constructParametricType(MdsMessageContainer.class, schemaClass));

        // Run default side-effects
        Method setKeyMethod = getSetKeyMethod(msgContainer.getMessage().getClass());
        if (setKeyMethod == null) {
            throw new IllegalStateException("No set key method found. This should not happened unless the implemented class " + implementedClass + " does not extend from " + MsgBaseMessage.class);
        }
        setKeyMethod.setAccessible(true);
        setKeyMethod.invoke(msgContainer.getMessage());

        msgContainer.getMessage().setImageType(MdsAttributes.buildMsgImageType(msgContainer.getAttributes()));
        delegatePublisher.publish(msgContainer.getMessage());
    }

    private Method getSetKeyMethod(Class<?> cl) throws NoSuchMethodException {
        if (MsgBaseMessage.class.isAssignableFrom(cl)) {
            try {
                return cl.getDeclaredMethod("setKey");
            } catch (NoSuchMethodException e) {
                // Set key method is from parent class
                return getSetKeyMethod(cl.getSuperclass());
            }
        } else {
            return null;
        }
    }
}
