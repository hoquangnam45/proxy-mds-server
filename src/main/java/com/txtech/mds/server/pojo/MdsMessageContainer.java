package com.txtech.mds.server.pojo;

import com.txtech.mds.msg.type.MsgBaseMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MdsMessageContainer<T extends MsgBaseMessage> {
    private T message;
    private MsgBaseMessageAttributes attributes;
}
