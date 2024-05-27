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

To make the application supports both deployed RTDS and RTO, I have added the new ```MarketData.ConnectionMode``` configuration to the ```application.properties``` file as follows:

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

[tbd]