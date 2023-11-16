package com.txtech.mds.server.proxy;

import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.msg.type.general.MsgHeartbeat;

public class ProxyMdsHeartbeater implements IHeartbeater<MsgBaseMessage> {
    @Override
    public MsgHeartbeat heartbeat() {
        return new MsgHeartbeat();
    }
}
