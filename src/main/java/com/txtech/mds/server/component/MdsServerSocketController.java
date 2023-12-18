package com.txtech.mds.server.component;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.server.pojo.IPublisher;
import com.txtech.mds.server.pojo.MdsContext;
import com.txtech.mds.server.proxy.ProxyMdsHandshaker;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.*;

@Getter
public class MdsServerSocketController implements IPublisher<MsgBaseMessage> {
    private static final Logger logger = LoggerFactory.getLogger(MdsServerSocketController.class);
    private final ServerSocket serverSocket;
    private final Set<MdsSocketController> activeClients = Collections.synchronizedSet(new HashSet<>());
    private final JsonSchemaGenerator schemaGenerator;
    private final MdsContext mdsContext;
    private final Map<String, Map<String, ObjectNode>> jsonSchemas;
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
        this.jsonSchemas = mdsContext.getJsonSchemas();
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
            ProxyMdsHandshaker handshaker = mdsContext.getHandshaker();
            this.listenThread = new Thread(() -> {
                while (!stop) {
                    try {
                        Socket socket = serverSocket.accept();
                        socket.setKeepAlive(true);
                        logger.info(MessageFormat.format("New client connected to mds server at [{0} / {1} / {2}]", mdsContext.getName(), String.valueOf(mdsContext.getConfig().getPort()), mdsContext.getConfig().getHandshakeStrategy()));
                        MdsSocketController client = new MdsSocketController(socket, mdsContext);
                        switch (mdsContext.getHandshakeStrategy()) {
                            case HANDSHAKING: {
                                client.sendSynchronous(handshaker.handshaking(version));
                                activeClients.add(client);
                                client.registerOnStop(activeClients::remove);
                                client.start();
                                break;
                            }
                            case ACCEPT: {
                                client.sendSynchronous(handshaker.accept());
                                activeClients.add(client);
                                client.registerOnStop(activeClients::remove);
                                client.start();
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

    @Override
    public void publish(MsgBaseMessage payload) throws Exception {
        activeClients.forEach(client -> client.addToSendingQueue(payload));
    }
}
