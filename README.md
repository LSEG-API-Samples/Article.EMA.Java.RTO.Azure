# How to Deploy EMA RTO Application to Azure

- Last Update: May 2024
- Compiler: Java, Docker, and Maven
- Prerequisite: RTO Authentication Version 2 credential

Example Code Disclaimer:
ALL EXAMPLE CODE IS PROVIDED ON AN “AS IS” AND “AS AVAILABLE” BASIS FOR ILLUSTRATIVE PURPOSES ONLY. REFINITIV MAKES NO REPRESENTATIONS OR WARRANTIES OF ANY KIND, EXPRESS OR IMPLIED, AS TO THE OPERATION OF THE EXAMPLE CODE, OR THE INFORMATION, CONTENT, OR MATERIALS USED IN CONNECTION WITH THE EXAMPLE CODE. YOU EXPRESSLY AGREE THAT YOUR USE OF THE EXAMPLE CODE IS AT YOUR SOLE RISK.

## Introduction

This repository is forked from my colleague [How to build a scalable web service for stock prices](https://github.com/LSEG-API-Samples/Article.RTSDK.Java.MDWebService) with the following goals:

1. Update my colleague's Spring Boot MDWebService to support the Real-Time Optimized (RTO **ELEKTRON_DD** service) with the Authentication Version 2 (aka Customer Identity and Access Management - CIAM, or *Service Account*).
2. Use the RTO version MDWebService as a base application for deploying to [Azure Container Instances](https://azure.microsoft.com/en-us/products/container-instances).

For more detail about the original project information, please check the following resources:

- The original project [README file](https://github.com/LSEG-API-Samples/Article.RTSDK.Java.MDWebService/blob/main/README.md).
- [How to build a scalable web service for stock prices](https://developers.lseg.com/en/article-catalog/article/scalable-web-service-for-stock) article.
- [How to deploy a web service on AWS](https://developers.lseg.com/en/article-catalog/article/how-to-deply-a-web-service-on-aws) article.

For further details about Migrating a EMA Java API application to Authentication Version 2, please check out the following resource:

- [EMA Java API: Refinitiv Real-Time Optimized Version 2 Authentication Migration Guide](https://developers.lseg.com/en/article-catalog/article/ema-java-api-real-time-optimized-version-2-authentication-migration-guide) article.
- [Account authorization V1 to V2 migration cheat sheet](https://developers.lseg.com/en/article-catalog/article/account-authorization-v1-to-v2-migration-cheat-sheet) article.
- [Getting Started with Version 2 Authentication for Real-Time - Optimized: Overview](https://developers.lseg.com/en/article-catalog/article/getting-started-with-version-2-authentication-for-refinitiv-real) article.

## Prerequisite

This project requires the following dependencies software and libraries.

1. Oracle 1.11 & 1.17 or OpenJDK 11.
2. [Apache Maven](https://maven.apache.org/) project management and comprehension tool.
3. Internet connection.  
4. Access to the Real-Time Optimized (**ELEKTRON_DD** service) with Authentication Version 2 (aka Customer Identity and Access Management - CIAM, or *Service Account*). The Service Account is Client ID and Client Secret.
5. [Docker Desktop](https://www.docker.com/products/docker-desktop/) application.
6. [Docker Hub](https://hub.docker.com/) repository account.
7. [Microsoft Azure](https://azure.microsoft.com/en-us/) account.

Please contact your LSEG representative to help you to access the RTO Service account and services.

## What I have changed from the original project

### pom.xml file

Firstly, I added [spring-dotenv](https://github.com/paulschwarz/spring-dotenv) dependencies to the project to load the RTO Authentication Version 2 credential from the Environment Variable or a ```.env``` file (see [configuration in the environment](https://12factor.net/config) for more detail).

The ```pom.xml``` file:

```xml
<dependency>
 <groupId>me.paulschwarz</groupId>
 <artifactId>spring-dotenv</artifactId>
 <version>4.0.0</version>
</dependency>
```

Next, I also updated the version of RTSDK and [Spring Boot](https://spring.io/projects/spring-boot/) in the ```pom.xml``` file as follows:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>2.7.18</version>
  <relativePath/> <!-- lookup parent from repository -->
 </parent>
...
<properties>
  <java.version>11</java.version>
  <maven.test.skip>true</maven.test.skip>
  <rtsdk.version>3.8.0.0</rtsdk.version>
 </properties>
```

### application.properties file

To make the application supports both deployed RTDS and RTO, I have added the new ```MarketData.ConnectionMode``` configuration node to the ```application.properties``` file as follows:

```ini
#Choose connection mode RTDS or RTO
MarketData.ConnectionMode=RTO
#MarketData.ConnectionMode=RTDS
```

This new configuration also need a code changed on a ```Consumer.java``` file:

```java
@Service
public class Consumer {
 // market data configuration values read from application.properties

 @Value("${MarketData.ConnectionMode}")
 private String connectionMode;
 ...
}
```

### Consumer.java file

The next step is change a ```Consumer.java``` to loaded RTO *Client ID* and *Client Secret* credential from the Environment Variables or a ```.env``` file.

```java
@Service
public class Consumer {
 ...
 @Value("${CLIENT_ID}")
 private String client_id;

 @Value("${CLIENT_SECRET}")
 private String client_secret;
 ...
}
```

Then changed the code to initialize the ```OmmConsumer``` object based on a ```MarketData.ConnectionMode``` configuration.

```java
public void initialize() {
 ...
 if (connectionMode.equals("RTDS")){
   LOG.info("Starting OMMConsumer with following parameters: ");
   ....

   // initialize the OMM consumer to RTDS
   consumer  = EmaFactory.createOmmConsumer(EmaFactory.createOmmConsumerConfig()
   .host(hostName + ":" + port)
   .username(userName));
  } else if (connectionMode.equals("RTO")){
   LOG.info("Starting OMMConsumer connecting to RTO with following parameters: ");
   ...

   // initialize the OMM consumer to RTO
   consumer  = EmaFactory.createOmmConsumer(EmaFactory.createOmmConsumerConfig()
   .consumerName("Consumer_RTO")
   .clientId(client_id)
   .clientSecret(client_secret));
  }
 ...
}
```

I am also added ```.env``` (.gitignore) and ```.env.example``` files for set the Authentication Version 2 locally as follows:

```ini
CLIENT_ID=<Your Auth V2 Client-ID>
CLIENT_SECRET=<Your Auth V2 Client-Secret>
```

### EmaConfig.xml file

The easiest way to connect to RTO with the Service Discovery (dynamically gets RTO endpoints) is via the **Location** and **EnableSessionManagement** configurations of the newly added **EmaConfig.xml** file.

- The **Location** configuration specifies the cloud location of the RTO endpoint to which the RTSDK API establishes a connection.
- The **EnableSessionManagement** configuration specifies whether the channel manages the authentication token (obtain and refresh) on behalf of the user.

The content in the EMA configuration file (**EmaConfig.xml**) looks like this:

```xml
<ConsumerGroup>
 <!-- DefaultConsumer parameter defines which consumer configuration is used by OmmConsumer -->
 <!-- if application does not specify it through OmmConsumerConfig::consumerName() -->
 <!-- first consumer on the ConsumerList is a DefaultConsumer if this parameter is not specified	-->
 <ConsumerList>
  <Consumer>
   <Name value="Consumer_RTO"/>
   <Channel value="Channel_RTO"/>
   <Dictionary value="Dictionary_1"/>
   <MaxDispatchCountApiThread value="6500"/>
   <MaxDispatchCountUserThread value="6500"/>
   <XmlTraceToStdout value="0"/>
  </Consumer>
 </ConsumerList>
</ConsumerGroup>
...
<ChannelGroup>
 <ChannelList>
  <Channel>
   <Name value="Channel_RTO"/>
   <ChannelType value="ChannelType::RSSL_ENCRYPTED"/>
   <CompressionType value="CompressionType::None"/>
   <GuaranteedOutputBuffers value="5000"/>
   <!-- EMA discovers a host and a port from RDP service discovery for the specified location
				 when both of them are not set and the session management is enable. -->
   <Location value="ap-southeast"/>
   <EnableSessionManagement value="1"/>
   <ObjectName value=""/>
  </Channel>
 </ChannelList>
</ChannelGroup>
```

Note: The above example uses "ap-southeast" as an example RTO region.  You can optionally change this to test other endpoints within your region.  Please check with your LSEG representative. To retrieve a valid list of RTO endpoints based on your assigned tier and region, refer to the DNS Names within the Current Endpoints section outlined in the [Real-Time - Optimized Install and Config Guide](https://developers.lseg.com/en/api-catalog/refinitiv-real-time-opnsrc/rt-sdk-java/documentation#refinitiv-real-time-optimized-install-and-config-guide) document.

To support both RTO and RTDS connection, the **Consumer_RTDS** consumer node and **Channel_RTDS** channel node have been added to the EmaConfig.xml file too.

```xml
<ConsumerGroup>
 <!-- DefaultConsumer parameter defines which consumer configuration is used by OmmConsumer -->
 <!-- if application does not specify it through OmmConsumerConfig::consumerName() -->
 <!-- first consumer on the ConsumerList is a DefaultConsumer if this parameter is not specified	-->
 <DefaultConsumer value="Consumer_RTDS"/>
 <ConsumerList>
  <Consumer>
   <Name value="Consumer_RTDS"/>
   <Channel value="Channel_RTDS"/>
   <Dictionary value="Dictionary_1"/>
   <XmlTraceToStdout value="0"/>
  </Consumer>
 </ConsumerList>
</ConsumerGroup>


<ChannelGroup>
 <ChannelList>
  <Channel>
   <Name value="Channel_RTDS"/>
   <ChannelType value="ChannelType::RSSL_SOCKET"/>
   <CompressionType value="CompressionType::None"/>
   <GuaranteedOutputBuffers value="5000"/>
   <ConnectionPingTimeout value="30000"/>
   <TcpNodelay value="1"/>
   <Host value="localhost"/>
   <Port value="14002"/>
  </Channel>
 </ChannelList>
</ChannelGroup>
```

### Dockerfile file

A Dockerfile has been updated to use [multi-stage build](https://docs.docker.com/guides/docker-concepts/building-images/multi-stage-builds) as follows:

```dockerfile
FROM maven:3.9.6-eclipse-temurin-11-focal as builder
LABEL authors="Developer Relations"
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean -e -B package

FROM openjdk:11-jre-slim-bullseye
WORKDIR /app
COPY --from=builder /app/target/MDWebService-0.0.1-SNAPSHOT.jar .
COPY EmaConfig.xml .

# run MDWebService-0.0.1-SNAPSHOT.jar with CMD
CMD ["java", "-jar", "./MDWebService-0.0.1-SNAPSHOT.jar"]
```

I also add a ```.dockerignore``` file to not include some project files and directories into a container.

## How to run MDWebService with RTO connection

Firstly, set a ```application.properties``` file to connect to RTO as follows:

```ini
#Choose connection mode RTDS or RTO
MarketData.ConnectionMode=RTO
```

Next, create a ```.env``` file with the Authentication Version 2 credential like the following format:

```ini
#Authentication V2
CLIENT_ID=<Your Auth V2 Client-ID>
CLIENT_SECRET=<Your Auth V2 Client-Secret>
```

Finally, set the prefer RTO endpoint region in the **Location** configuration node of the ```EmaConfig.xml``` file.

```xml
<ChannelGroup>
 <ChannelList>
  <Channel>
   <Name value="Channel_RTO"/>
   <ChannelType value="ChannelType::RSSL_ENCRYPTED"/>
   <CompressionType value="CompressionType::None"/>
   <GuaranteedOutputBuffers value="5000"/>
   <!-- EMA discovers a host and a port from RDP service discovery for the specified location
				 when both of them are not set and the session management is enable. -->
   <Location value="ap-southeast"/>
   <EnableSessionManagement value="1"/>
   <ObjectName value=""/>
  </Channel>
 </ChannelList>
</ChannelGroup>
```

### Run with Java Locally

Once the development environment has Maven setup, use the following command to compile and package the file as a single executable jar file. (Note the use of Maven wrapper here):

```bash
mvnw clean package
```
Once the compilation is successful and a jar file has been created in the target directory, use either of the following commands to run the application locally:

```bash
mvnw spring-boot:run
#or
java -jar target\MDWebService-0.0.1-SNAPSHOT.jar
```

[tbd]
