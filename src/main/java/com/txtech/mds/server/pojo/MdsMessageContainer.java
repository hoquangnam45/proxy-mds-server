package com.txtech.mds.server.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.txtech.mds.msg.type.MsgBaseMessage;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MdsMessageContainer<T extends MsgBaseMessage> {
    private T message;
    private MsgBaseMessageAttributes attributes;
}
