package com.txtech.mds.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MdsProxyServer
{
	// Approach 1: Json schema -> Message definition -> register to grpc service -> receive message -> print to json
	// publish json
	// Approach 2: Json schema -> proto file -> compile to proto descriptors file -> load to descriptor set
	// -> register to grpc service -> receive message -> print to json -> publish json
	// Result -> approach #2
	public static void main(String[] args)
	{
		SpringApplication.run(MdsProxyServer.class, args).start();
	}
}