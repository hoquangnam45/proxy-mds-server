package com.txtech.mds.server.proxy;

import java.io.Serializable;

public interface ISerializer<T, V> extends Serializable
{
	String getName();
	V decode(T rawValue) throws Exception;
	T encode(V value) throws Exception;
	Class<V> getDecodedClass();
	Class<T> getEncodedClass();
}