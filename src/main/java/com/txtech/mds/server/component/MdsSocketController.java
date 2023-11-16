package com.txtech.mds.server.component;

import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.server.pojo.MdsContext;
import com.txtech.mds.server.proxy.ISerializer;
import lombok.Getter;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

@Getter
public class MdsSocketController implements AutoCloseable {
    private static final int DEFAULT_MAX_SENDING_QUEUE_SIZE = 50;
    private static final int DEFAULT_MAX_RECEIVING_QUEUE_SIZE = 50;
    private static final int DEFAULT_MAX_RECEIVING_QUEUE_BUFFER_SIZE = 5000;
    private final Queue<MsgBaseMessage> sendingQueue;
    private final Queue<MsgBaseMessage> receivingQueue;
    private final ByteBuffer receivingBuffer;
    private final Socket socket;
    private final MdsContext mdsContext;
    private final Thread sendingThread;
    private final Thread receivingThread;
    private final Object sendingLock = new Object();

    public MdsSocketController(Socket socket, MdsContext mdsContext) {
        this.socket = socket;
        this.mdsContext = mdsContext;
        this.receivingBuffer = ByteBuffer.allocateDirect(DEFAULT_MAX_RECEIVING_QUEUE_BUFFER_SIZE);
        receivingBuffer.order(ByteOrder.LITTLE_ENDIAN);
        this.receivingQueue = new ArrayBlockingQueue<>(DEFAULT_MAX_RECEIVING_QUEUE_SIZE);
        this.sendingQueue = new ArrayBlockingQueue<>(DEFAULT_MAX_SENDING_QUEUE_SIZE);
        this.sendingThread = new Thread(() -> {
            while (true) {
                try {
                    synchronized(sendingLock) {
                        if (!socket.isClosed() && socket.isConnected()) {
                            if (sendingQueue.isEmpty()) {
                                sendingQueue.add(mdsContext.getHeartbeater().heartbeat());
                            }
                            while (!sendingQueue.isEmpty()) {
                                sendSynchronous(sendingQueue.poll());
                            }
                        }
                        sendingLock.wait(mdsContext.getConfig().getHeartbeatIntervalInMs());
                    }
                } catch (Exception e) {
                    try {
                        stop();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    throw new RuntimeException(e);
                }
            }
        });
        this.receivingThread = new Thread(() -> {
            while (true) {
                try {
                    if (!socket.isClosed() && socket.isConnected()) {
                        readResponse(socket);
                        receivingBuffer.flip();
                        Optional.ofNullable(mdsContext.getSerializer().decode(receivingBuffer))
                                .ifPresent(this::addToReceivingQueue);
                        receivingBuffer.compact();
                    }
                } catch (Exception e) {
                    try {
                        stop();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public void start() {
        this.sendingThread.start();
        this.receivingThread.start();
    }

    public void sendSynchronous(MsgBaseMessage message) throws Exception {
        message.setMessageTime(System.nanoTime());
        writeToSocket(socket, mdsContext.getSerializer().encode(message));
    }

    public MsgBaseMessage receiveSynchronous() throws Exception {
        readResponse(socket);
        receivingBuffer.flip();
        MsgBaseMessage ret = mdsContext.getSerializer().decode(receivingBuffer);
        receivingBuffer.compact();
        return ret;
    }

    public void addToSendingQueue(MsgBaseMessage message) {
        synchronized (sendingLock) {
            this.sendingQueue.add(message);
            sendingLock.notify();
        }
    }

    protected void addToReceivingQueue(MsgBaseMessage message) {
        this.receivingQueue.add(message);
    }

    private void writeToSocket(Socket socket, ByteBuffer buffer) throws IOException {
        if (socket.isClosed() || !socket.isConnected()) {
            return;
        }
        OutputStream os = socket.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        byte[] rawBytes = new byte[buffer.remaining()];
        buffer.get(rawBytes);
        bos.write(rawBytes);
        bos.flush();
    }

    private void readResponse(Socket socket) throws Exception {
        InputStream is = socket.getInputStream();
        BufferedInputStream bis = new BufferedInputStream(is);
        while (bis.available() == 0) {
            Thread.sleep(1);
        }
        while(bis.available() > 0) {
            receivingBuffer.put((byte) bis.read());
        }
    }

    public boolean isStopped() {
        return receivingThread.isInterrupted() && sendingThread.isInterrupted() && !socket.isConnected() && !socket.isClosed();
    }

    public void stop() throws Exception {
        close();
    }

    @Override
    public void close() throws Exception {
        if (!socket.isClosed()) {
            socket.close();
        }
        receivingBuffer.clear();
        sendingQueue.clear();
        receivingQueue.clear();
        if (!sendingThread.isInterrupted()) {
            sendingThread.interrupt();
        }
        if (!receivingThread.isInterrupted()) {
            receivingThread.interrupt();
        }
    }
}
