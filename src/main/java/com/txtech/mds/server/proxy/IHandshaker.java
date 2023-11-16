package com.txtech.mds.server.proxy;

import java.io.Serializable;

public interface IHandshaker<T> extends Serializable {
    T handshaking(String version);
    T accept();
    T timeout();
    T incorrectVersion();
    T denied();
}
