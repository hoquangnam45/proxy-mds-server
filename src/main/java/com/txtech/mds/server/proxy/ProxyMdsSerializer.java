package com.txtech.mds.server.proxy;

import com.txtech.mds.converter.MsgMdsDecoder;
import com.txtech.mds.converter.MsgMdsEncoder;
import com.txtech.mds.msg.MsgImageType;
import com.txtech.mds.msg.MsgObjectClassManager;
import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.msg.type.MsgMdsAttribute;
import lombok.Getter;

import java.nio.ByteBuffer;

public class ProxyMdsSerializer {
    @Getter
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

    public MsgBaseMessage decode(ByteBuffer rawValue) throws Exception {
        return decoder.decode(rawValue);
    }

    public ByteBuffer encode(MsgBaseMessage value) throws Exception {
        return encoder.encode(new MsgMdsAttribute(MsgMdsAttribute.SourceType.Upstream, value.getImageType()), value);
    }
}
