package com.txtech.mds.server.util;

import com.txtech.mds.msg.IMsgImageType;
import com.txtech.mds.msg.MsgImageType;
import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.server.pojo.MsgBaseMessageAttributes;

public class MdsAttributes {
    public static MsgImageType buildMsgImageType(MsgBaseMessageAttributes attributes) {
        MsgImageType ret = new MsgImageType();
        if (attributes.isBroadcast()) {
            ret.setBroadcastMessage();
        }
        if (attributes.isLast()) {
            ret.setLastMessage();
        }
        if (attributes.isFirst()) {
            ret.setFirstMessage();
        }
        if (attributes.getType().equals(IMsgImageType.Type.Update)) {
            ret.setUpdate();
        } else {
            ret.setSnapshot(attributes.getSnapshotType(), attributes.getSnapshotExchangeID(), attributes.getSnapshotKey());
        }
        if (attributes.isMulticast()) {
            ret.setMulticastMessage();
        }
        return ret;
    }
}
