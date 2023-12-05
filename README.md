## What is this tool?
This tool is intended to help you test the behavior of your mds client during development process where access to the real mds server is not possible by simulating the mds server which the mds client can subscribe to and receive a mds message generated ad-hoc.

## How to use this tool?

1. Setting up environment
   - Protoc compiler
    ```bash
    sudo apt install protobuf-compiler
    ```
   - Grpc client: [warthog](https://github.com/Forest33/warthog) or any [grpc clients](https://github.com/grpc-ecosystem/awesome-grpc#tools) of your liking
   - [*Download the tool*](https://gitlab.tx-tech.com/internal-vn-tool/release-builder/-/package_files/26/download)

2. Run the mds server
    ```bash
    java -jar <path-to-jar>
    ```
    **NOTE:** The proxy mds server will use the latter config:
   - Rest port: 8080
   - Grpc port: 8889
   - Mds server port: 8888

3. **(Optional)** if you want to run this as source
   - Setup lombok
      - **IntelliJ**: Settings > Plugins > Marketplace > Search lombok then install
      - **Eclipse**: Help menu > Install new software > Add https://projectlombok.org/p2 > Install the Lombok plugin and restart Eclipse.
      - **Eclipse (snap)**: 
      - Copy eclipse.ini to your snap eclipse home folder
      ```bash
      cp /snap/eclipse/current/eclipse.ini ~/snap/eclipse/current/eclipse.ini
      ```
       - Add this line to your eclipse.ini then restart eclipse 
      ```text
      -javaagent:<path-to-your-lombok.jar>
       ```
      - Verify if lombok is installed successfully in _Help > About Eclipse IDE_ 

      ![eclipse_lombok.png](images%2Feclipse_lombok.png)

   - After set up lombok you can start your mds proxy server using eclipse or intellij

4. Start your mds client

5. Using grpc client to connect to mds server
![grpc_client_set_up_1.png](images%2Fgrpc_client_set_up_1.png)
![grpc_client_set_up_2.png](images%2Fgrpc_client_set_up_2.png)

6. Start publish your mds message to your mds client

![warthog_client.png](images%2Fwarthog_client.png)

The attributes will be used to generate the mvImageType field for mds message, based on the value set in the attributes it can either route your mds message to the listener or to a different handlers, you can use the default value in the image to route it to mds listener method

## FAQ

### How do I change the port of the mds proxy server?

This tool has 3 ports that is defined in the application.yml.

- **server.port**: Tomcat port used by spring, you can publish your mds message restfully using curl or postman without needing to install grpc client
- **mds.contexts[].port**: Mds proxy server port
- **mds.contexts[].grpc.port**: Port for grpc client

If you run this tool as a standalone jar, you can provide your own config.yml. Below is a template config.
```yaml
mds:
  contexts:
    - name: mds-proxy-1
      port: 8888
      heartbeatIntervalInMs: 55000
      version: 5.0
      handshakeStrategy: ACCEPT
      grpc:
        outputProtoDir: ./protos/mds-proxy-1/
        port: 8889
server:
  port: 9999
```

To use your config start it like this
```bash
java -jar <path-to-tool-jar> --spring.config.location=<path-to-your-config.yml>
```

### How do I make it to include new services or new message class?

The services are generated automatically based on the listener interface from MDSAPI, if you want to include your new listener method you can change the version of the MDSAPI to the version that you want to test.

![mds_api.png](images%2Fmds_api.png)

The schema for the mds messages are generated automatically from MDSMessage, to include the change do it like above, but with MDSMessage instead.

![mds_message.png](images%2Fmds_message.png)

For more convenient generation of mds messages, you can add both mds api and mds message project to your mds proxy server workspace so that everytime you make change, it will use latest change automatically. 

If you want to test new services and types with standalone jar, provide your own config with **overrideMdsPackages** defined in the context, like this:
```yaml
mds:
  contexts:
    - name: mds-proxy-1
      port: 8888
      heartbeatIntervalInMs: 55000
      version: 5.0
      handshakeStrategy: ACCEPT
      grpc:
        outputProtoDir: ./protos/mds-proxy-1/
        port: 8889
      overrideMdsPackages:
        - <path-to-mds-message-jar>
        - <path-to-mds-api-jar>
        - <path-to-mds-message-classes-folder>
        - <path-to-mds-api-classes-folder>
server:
  port: 9999
```
but please **discourage** from doing this way, as this is a workaround to quickly testing, using your pom file and rebuild the project is recommended over doing it like this

## How it works under-the-hood?

By default, this tool will use the com.txtech.mds.api.listener.MdsMarketDataListenerInterface from MDSAPI project to determined which grpc services need to be generated dynamically by filter for specific listener method signature (void method with 1 argument), the type of the argument will then be collected and then later be used to filter for implementation class as well as generate the service name.

After the listener method discovery process, it will scan through all the class in this specific package com.txtech.mds.msg.type and check for any concrete/non-abstract class that is a subclass of the interface collected in the previous steps to generate a json schema for that class.

That json schema will then be used to generate the proto schema then compiled to proto descriptors that will later be used to start the grpc services dynamically, as well as provided reflection for grpc client. Every time mds proxy server is run it will re-autogenerated all the services as well as the types all over again.