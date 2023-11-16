package com.txtech.mds.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MdsProxyServer
{
	public static void main(String[] args)
	{
		SpringApplication.run(MdsProxyServer.class, args).start();
	}
}