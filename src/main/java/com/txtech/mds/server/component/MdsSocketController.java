package com.txtech.mds.server.component;

import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.server.pojo.ConsumerEx;
import com.txtech.mds.server.pojo.MdsContext;
import lombok.Getter;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    private boolean stop;
    private boolean start;
    private ConsumerEx<MdsSocketController> onStop;

    public MdsSocketController(Socket socket, MdsContext mdsContext) {
        this.stop = false;
        this.start = false;
        this.socket = socket;
        this.mdsContext = mdsContext;
        this.receivingBuffer = ByteBuffer.allocateDirect(DEFAULT_MAX_RECEIVING_QUEUE_BUFFER_SIZE);
        receivingBuffer.order(ByteOrder.LITTLE_ENDIAN);
        this.receivingQueue = new ArrayBlockingQueue<>(DEFAULT_MAX_RECEIVING_QUEUE_SIZE);
        this.sendingQueue = new ArrayBlockingQueue<>(DEFAULT_MAX_SENDING_QUEUE_SIZE);
        this.sendingThread = new Thread(() -> {
            try {
                while (!stop) {
                    synchronized(sendingLock) {
                        if (sendingQueue.isEmpty()) {
                            sendingQueue.add(mdsContext.getHeartbeater().heartbeat());
                        }
                        while (!sendingQueue.isEmpty()) {
                            sendSynchronous(sendingQueue.poll());
                        }
                        sendingLock.wait(mdsContext.getConfig().getHeartbeatIntervalInMs());
                    }
                }
            } catch (Exception e) {
                try {
                    stop();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                throw new RuntimeException(e);
            }
        });
        this.receivingThread = new Thread(() -> {
            try {
                while (!stop) {
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
        });
    }

    public void start() {
        if (start) {
            throw new IllegalStateException("This socket has been started, restarted is not allowed");
        }
        this.start = true;
        this.sendingThread.start();
        this.receivingThread.start();
    }

    public void registerOnStop(ConsumerEx<MdsSocketController> onStop) {
        this.onStop = onStop;
    }

    public void sendSynchronous(MsgBaseMessage message) throws Exception {
        if (stop || isStopped()) {
            throw new IllegalStateException("Socket has stopped");
        }
        message.setMessageTime(System.nanoTime());
        writeToSocket(socket, mdsContext.getSerializer().encode(message));
    }

    public MsgBaseMessage receiveSynchronous() throws Exception {
        if (stop || isStopped()) {
            throw new IllegalStateException("Socket has stopped");
        }
        readResponse(socket);
        receivingBuffer.flip();
        MsgBaseMessage ret = mdsContext.getSerializer().decode(receivingBuffer);
        receivingBuffer.compact();
        return ret;
    }

    public void addToSendingQueue(MsgBaseMessage message) {
        if (stop || isStopped()) {
            throw new IllegalStateException("Socket has stopped");
        }
        synchronized (sendingLock) {
            this.sendingQueue.add(message);
            sendingLock.notify();
        }
    }

    protected void addToReceivingQueue(MsgBaseMessage message) {
        this.receivingQueue.add(message);
    }

    private void writeToSocket(Socket socket, ByteBuffer buffer) throws IOException {
        OutputStream os = socket.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        byte[] rawBytes = new byte[buffer.remaining()];
        buffer.get(rawBytes);
        bos.write(rawBytes);
        bos.flush();
    }

    private synchronized void readResponse(Socket socket) throws Exception {
        InputStream is = socket.getInputStream();
        BufferedInputStream bis = new BufferedInputStream(is);
        while (bis.available() == 0) {
            try {
                wait(1);
            } catch (InterruptedException e) {
                // Reference: https://stackoverflow.com/questions/35474536/wait-is-always-throwing-interruptedexception
                /* perfectly normal for thread wait to have this exception */
            }
        }
        while(bis.available() > 0) {
            receivingBuffer.put((byte) bis.read());
        }
    }

    public boolean isStopped() {
        return receivingThread.isInterrupted() && sendingThread.isInterrupted() && !socket.isConnected() && !socket.isClosed();
    }

    public synchronized void stop() throws Exception {
        close();
    }

    @Override
    public synchronized void close() throws Exception {
        if (stop && isStopped()) {
            return;
        }
        while (!sendingThread.isInterrupted()) {
            sendingThread.interrupt();
        }
        while (!receivingThread.isInterrupted()) {
            receivingThread.interrupt();
        }
        while (!socket.isClosed()) {
            socket.close();
        }
        receivingBuffer.clear();
        sendingQueue.clear();
        receivingQueue.clear();
        if (onStop != null) {
            onStop.accept(this);
        }
        stop = true;
    }
}
