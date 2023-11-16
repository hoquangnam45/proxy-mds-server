package com.txtech.mds.server.pojo;

import com.txtech.mds.msg.IMsgImageType;
import com.txtech.mds.msg.MsgExchangeID;
import com.txtech.mds.msg.MsgMessageType;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MsgBaseMessageAttributes {
    private boolean broadcast;
    private IMsgImageType.Type type;
    private boolean first;
    private boolean last;
    private boolean multicast;
    private String snapshotKey;
    private MsgMessageType snapshotType;
    private MsgExchangeID snapshotExchangeID;
}
