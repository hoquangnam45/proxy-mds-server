package com.txtech.mds.server.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Enum;
import com.google.protobuf.EnumValue;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import com.google.protobuf.Type;
import com.google.protobuf.UnknownFieldSet;
import com.txtech.mds.msg.type.MsgBaseMessage;
import lombok.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MdsMessageContainer<T extends MsgBaseMessage> {
    private T message;
    private MsgBaseMessageAttributes attributes;
}
