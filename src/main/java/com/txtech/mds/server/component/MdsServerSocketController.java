package com.txtech.mds.server.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.server.pojo.MdsContext;
import com.txtech.mds.server.pojo.MdsMessageContainer;
import com.txtech.mds.server.proxy.IHandshaker;
import com.txtech.mds.server.util.MdsAttributes;
import lombok.Getter;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.*;

@Getter
public class MdsServerSocketController {
    private final ServerSocket serverSocket;
    private final Set<MdsSocketController> activeClients = Collections.synchronizedSet(new HashSet<>());
    private final JsonSchemaGenerator schemaGenerator;
    private final MdsContext mdsContext;
    private final Map<String, Map<String, JsonNode>> jsonSchemas;
    private final String version;
    private Thread listenThread;
    private boolean stop = false;
    private boolean started = false;

    public MdsServerSocketController(
            ServerSocket serverSocket,
            MdsContext mdsContext,
            JsonSchemaGenerator schemaGenerator,
            String version) {
        this.version = version;
        this.serverSocket = serverSocket;
        this.mdsContext = mdsContext;
        this.schemaGenerator = schemaGenerator;
        this.jsonSchemas = mdsContext.getSchemas();
    }

    public void stop() throws Exception {
        this.stop = true;
        if (serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (!listenThread.isInterrupted()) {
            listenThread.interrupt();
        }
        for (MdsSocketController client : activeClients) {
            if (!client.isStopped()) {
                client.stop();
            }
        }
        activeClients.clear();
    }

    public void start() {
        if (stop) {
            throw new IllegalStateException("The socket has stopped, recreate the socket instead of restarting");
        }
        if (!started) {
            started = true;
            IHandshaker<MsgBaseMessage> handshaker = mdsContext.getHandshaker();
            this.listenThread = new Thread(() -> {
                while (!stop) {
                    try {
                        MdsSocketController client = new MdsSocketController(serverSocket.accept(), mdsContext);
                        switch (mdsContext.getHandshakeStrategy()) {
                            case HANDSHAKING: {
                                client.sendSynchronous(handshaker.handshaking(version));
                                MsgBaseMessage resp = client.receiveSynchronous();
                                client.start();
                                activeClients.add(client);
                                break;
                            }
                            case ACCEPT: {
                                client.sendSynchronous(handshaker.accept());
                                client.start();
                                activeClients.add(client);
                                break;
                            }
                            case DENIED: {
                                client.sendSynchronous(handshaker.denied());
                                client.close();
                                break;
                            }
                            case TIMEOUT: {
                                client.sendSynchronous(handshaker.timeout());
                                client.close();
                                break;
                            }
                            default:
                                throw new IllegalStateException("Not possible to enter here");
                        }
                    } catch (Exception e) {
                        try {
                            stop();
                        } catch (Exception ex) {
                            /* noop */
                        }
                        throw new RuntimeException(e);
                    }
                }
            });
            listenThread.start();
        }
    }

    public void publish(String schemaName, String subSchemaName, JsonNode data) throws IOException, InvocationTargetException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException {
        Class<? extends MsgBaseMessage> schemaClass = mdsContext.getSchemaClasses().get(schemaName).get(subSchemaName);
        ObjectMapper objectMapper = mdsContext.getObjectMapper();
        MdsMessageContainer<?> msgContainer = objectMapper.convertValue(data, objectMapper.getTypeFactory().constructParametricType(MdsMessageContainer.class, schemaClass));

        // Run default side-effects
        Method setKeyMethod = msgContainer.getMessage().getClass().getDeclaredMethod("setKey");
        setKeyMethod.setAccessible(true);
        setKeyMethod.invoke(msgContainer.getMessage());

        msgContainer.getMessage().setImageType(MdsAttributes.buildMsgImageType(msgContainer.getAttributes()));
        activeClients.forEach(client -> client.addToSendingQueue(msgContainer.getMessage()));
    }
}
