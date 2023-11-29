package com.txtech.mds.server.proxy;

import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.msg.type.general.MsgHeartbeat;

public class ProxyMdsHeartbeater {
    public MsgHeartbeat heartbeat() {
        return new MsgHeartbeat();
    }
}
