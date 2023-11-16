package com.txtech.mds.server.proxy;

import com.txtech.mds.converter.MsgMdsDecoder;
import com.txtech.mds.converter.MsgMdsEncoder;
import com.txtech.mds.msg.MsgImageType;
import com.txtech.mds.msg.MsgObjectClassManager;
import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.msg.type.MsgMdsAttribute;

import java.nio.ByteBuffer;

public class ProxyMdsSerializer implements ISerializer<ByteBuffer, MsgBaseMessage> {
    private final String name;
    private final MsgMdsDecoder decoder;
    private final MsgMdsEncoder encoder;

    static {
        try {
            MsgObjectClassManager.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ProxyMdsSerializer(String name) {
        this.name = name;
        this.decoder = new MsgMdsDecoder();
        this.encoder = new MsgMdsEncoder();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public MsgBaseMessage decode(ByteBuffer rawValue) throws Exception {
        return decoder.decode(rawValue);
    }

    @Override
    public ByteBuffer encode(MsgBaseMessage value) throws Exception {
        return encoder.encode(new MsgMdsAttribute(MsgMdsAttribute.SourceType.Upstream, value.getImageType()), value);
    }

    @Override
    public Class<ByteBuffer> getEncodedClass() {
        return ByteBuffer.class;
    }

    @Override
    public Class<MsgBaseMessage> getDecodedClass() {
        return MsgBaseMessage.class;
    }
}
