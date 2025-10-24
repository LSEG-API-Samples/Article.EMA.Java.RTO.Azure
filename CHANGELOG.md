# How to Deploy EMA RTO Application to Azure

- Last Update: October 2025
- Compiler: Java, Docker, and Maven
- Prerequisite: RTO Authentication Version 2 credential

Example Code Disclaimer:
ALL EXAMPLE CODE IS PROVIDED ON AN “AS IS” AND “AS AVAILABLE” BASIS FOR ILLUSTRATIVE PURPOSES ONLY. REFINITIV MAKES NO REPRESENTATIONS OR WARRANTIES OF ANY KIND, EXPRESS OR IMPLIED, AS TO THE OPERATION OF THE EXAMPLE CODE, OR THE INFORMATION, CONTENT, OR MATERIALS USED IN CONNECTION WITH THE EXAMPLE CODE. YOU EXPRESSLY AGREE THAT YOUR USE OF THE EXAMPLE CODE IS AT YOUR SOLE RISK.

## What I have changed from the original project

Let me start by explaining the updated that I have made to support the RTO connection.

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

**Last Updated:** October 2025

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.7</version>
  <relativePath/> <!-- lookup parent from repository -->
 </parent>
...
<properties>
  <java.version>11</java.version>
  <maven.test.skip>true</maven.test.skip>
  <rtsdk.version>3.9.1.1</rtsdk.version>
 </properties>
```

Lastly, I added a [dependency exclusions](https://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html#dependency-exclusions) on the EMA's slf4j-jdk14 library to force the application to use Spring's default [Logback](https://logback.qos.ch/) library instead.

```xml
<dependency>
  <groupId>com.refinitiv.ema</groupId>
  <artifactId>ema</artifactId>
  <version>${rtsdk.version}</version>
  <exclusions>
    <exclusion> 
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-jdk14</artifactId>
    </exclusion>
  </exclusions> 
</dependency>
```

That covers the pom.xml file changed part.

### application.properties file

Turning to the application configuration file. I have added the new ```MarketData.ConnectionMode``` configuration node to the ```application.properties``` file as follows to make the application supports both RTO and deployed RTDS connections:

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

Let’s leave the ```application.properties``` file there.

### Consumer.java file

Now, what about application source code updated. The next step is change a ```Consumer.java``` to loaded RTO *Client ID* and *Client Secret* credential from the Environment Variables or a ```.env``` file.

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

### MDController.java

**Last Updated:** October 2025.

I am adding the ```try catch``` on the ```onApplicationEvent``` callback method.

```java
@EventListener
	public void onApplicationEvent(ContextRefreshedEvent event) {
		try {
			LOG.info("Initialize the consumer and connect to market data system....");
			ommCons.initialize();
		} catch (Exception e) {
			LOG.error("Failed to initialize consumer: {}", e.getMessage(), e);
			// You might want to set a flag or take other action to indicate initialization failure
		}
	}	
```

That’s all I have to say about the application source code changed.

### EmaConfig.xml file

That brings us to the new project's configuration file, the EmaConfig.xml file. It gives us the easiest way to connect to RTO with the Service Discovery (dynamically gets RTO endpoints) is via the **Location** and **EnableSessionManagement** configurations of the newly added **EmaConfig.xml** file.

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

Note: The above example uses "ap-southeast" as an example RTO region.  You can optionally change this to test other endpoints within your region.  Please check with your LSEG representative. To retrieve a valid list of RTO endpoints based on your assigned tier and region, refer to the DNS Names within the Current Endpoints section outlined in the [Real-Time - Optimized Install and Config Guide](https://developers.lseg.com/en/api-catalog/real-time-opnsrc/rt-sdk-java/documentation#refinitiv-real-time-optimized-install-and-config-guide) document.

The **Consumer_RTDS** consumer node and **Channel_RTDS** channel node have been added to the EmaConfig.xml file for supporting both RTO and RTDS connections too.

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

Now, what about a Dockerfile? A Dockerfile has been updated to use [multi-stage build](https://docs.docker.com/guides/docker-concepts/building-images/multi-stage-builds) as follows:

```dockerfile
FROM --platform=linux/amd64 maven:3.9.6-eclipse-temurin-11-focal as builder
LABEL authors="Developer Relations"
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean -e -B package

#FROM openjdk:11-jre-slim-bullseye
FROM --platform=linux/amd64 eclipse-temurin:11-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/MDWebService-0.0.1-SNAPSHOT.jar .
COPY EmaConfig.xml .

# run MDWebService-0.0.1-SNAPSHOT.jar with CMD
CMD ["java", "-jar", "./MDWebService-0.0.1-SNAPSHOT.jar"]
```

I also add a ```.dockerignore``` file to not include some project files and directories into a container.

That covers all the changed I made on this project.

## How to run MDWebService with RTO connection

Please check the [README.md](./README.md) file.

## How to deploy to Azure Cloud Service

Please see more detail on the [AZURE.md](./AZURE.md) file.
