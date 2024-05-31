# How to Deploy EMA RTO Application to Azure

- Last Update: May 2024
- Compiler: Java, Docker, and Maven
- Prerequisite: RTO Authentication Version 2 credential

Example Code Disclaimer:
ALL EXAMPLE CODE IS PROVIDED ON AN “AS IS” AND “AS AVAILABLE” BASIS FOR ILLUSTRATIVE PURPOSES ONLY. REFINITIV MAKES NO REPRESENTATIONS OR WARRANTIES OF ANY KIND, EXPRESS OR IMPLIED, AS TO THE OPERATION OF THE EXAMPLE CODE, OR THE INFORMATION, CONTENT, OR MATERIALS USED IN CONNECTION WITH THE EXAMPLE CODE. YOU EXPRESSLY AGREE THAT YOUR USE OF THE EXAMPLE CODE IS AT YOUR SOLE RISK.

## Introduction

This repository is forked from my colleague [How to build a scalable web service for stock prices](https://github.com/LSEG-API-Samples/Article.RTSDK.Java.MDWebService) with the following goals:

1. Update my colleague's Spring Boot MDWebService to support the Real-Time Optimized (RTO **ELEKTRON_DD** service only) with the Authentication Version 2 (aka Customer Identity and Access Management - CIAM, or *Service Account*).
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
4. Access to the Real-Time Optimized (**Not Wealth products**) with Authentication Version 2 (aka Customer Identity and Access Management - CIAM, or *Service Account*). The Service Account is Client ID and Client Secret.
5. [Docker Desktop](https://www.docker.com/products/docker-desktop/) application.
6. [Docker Hub](https://hub.docker.com/) repository account.
7. [Microsoft Azure](https://azure.microsoft.com/en-us/) account.

Please contact your LSEG representative to help you to access the RTO Service account and services.

## What I have changed from the original project

Please see more detail on the [CHANGELOG.md](./CHANGELOG.md) file.

## How to run MDWebService with RTO connection

Firstly, set a ```application.properties``` file to connect to RTO as follows:

```ini
#Choose connection mode RTDS or RTO
MarketData.ConnectionMode=RTO
```

Next, create a ```.env``` file in a ```MDWebService``` folder with the Authentication Version 2 credential like the following format:

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

![figure-1](pics/rto_service_up.png "connected to RTO")

The *ChannelUp* message from EMA informs the application that it has successfully connected to the RTO server on port 14002.

Once the application is running and successfully connected to the market data system, navigating to the web URL of [http://server:port/quotes/JPY=,THB=,SGD=](http://server:port/quotes/JPY=,THB=,SGD=) should produce a JSON response like this:

![figure-2](pics/result_mdwebservice.png "Run result on the web browsers")

Or you can run the HTTP request on the Postman.

![figure-3](pics/result_postman.png "Run result on Postman")

### Run with Docker

Firstly, build the container image by issuing the command (Dockerfile should be available in this *MDWebService*):

```bash
docker build -t mdwebservice-rto .
```

This will compile the image for our container. The newly created image can be seen with the *docker images* command:

**Note**: Please note that the Real-Time SDK isn't qualified on the Docker platform. If you find any problems while running it on the Docker platform, the issues must be replicated on bare metal machines before contacting the helpdesk/Real-Time APIs support.

This will compile the image for our container. The newly created image can be seen with the ```docker images``` command:

![figure-4](pics/docker_images.png "docker build result")

Then created image can be run using the command:

```bash
docker run -it --rm -p 8080:8080 --env-file .env mdwebservice-rto
```

Here, we have run the container to connect to RTO. The container also binds the port 8080 used by Tomcat on to our local machine. The application can be tested by navigating to the 8080 port as done previously.

![figure-5](pics/run_docker.png "run docker result")

After successful test, stop the running container using the docker stop command:

```bash
docker ps -a
docker container stop <container_id>
```

## How to run MDWebService with RTDS connection

Firstly, set a ```application.properties``` file to connect to RTDS as follows:

```ini
#Choose connection mode RTDS or RTO
MarketData.ConnectionMode=RTDS
```

Next, follow the steps on the [original project](https://github.com/LSEG-API-Samples/Article.RTSDK.Java.MDWebService).

## How to deploy to Azure Cloud Service

Normally, there are various ways to deployed an application on the cloud service like create a Virtual Machine (VM) on the cloud, or use the Serverless or Platform as a Service (PaaS) to run application's source code on the cloud, or use the fully managed container orchestration service to run an application's container. It is based on your preference of manageability, business logic and cost to choose which way is suitable for you.

The VM way is supported by all major Cloud services like the [Azure Virtual Machine](https://azure.microsoft.com/en-us/products/virtual-machines), [AWS EC2](https://aws.amazon.com/ec2/), and [Google Cloud Compute Engine](https://cloud.google.com/products/compute/). However, it requires a lot of manual work such as manage and set up the machine, install all application's dependencies, and run an application by yourself manually. This is the reason our article does not choose the VM way.

Since our MDWebService application already have been containerize, this article chooses the fully managed container orchestration service way.

Azure provides various services for supporting different requirements,cost, and your application's preference. You can find more detail about the comparisons of all services that supports containerized applications on Azure on [Comparing Container Apps with other Azure container options](https://learn.microsoft.com/en-us/azure/container-apps/compare-options) and [Difference between Azure Container Instances and Azure Container Apps - serverfault](https://serverfault.com/questions/1083358/difference-between-azure-container-instances-and-azure-container-apps) websites. I am choosing the [Azure Container Instances service](https://azure.microsoft.com/en-us/products/container-instances) because the MDWebService application just need only a single isolate container to run and no need to scale it up yet. <--- May be change in the future.

### Container Registry

Azure also has the [Container Registry Service](https://azure.microsoft.com/en-us/products/container-registry) repository for storing and managing container images and artifacts with a fully managed environment. This repository requires the [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/) tool to push/pull an application image to Azure container registry. However, our beloved ZScaler somehow blocks this Azure CLI tool to login to Azure, so I need to find other ways.

Fortunately, the service supports [Docker Hub registry](https://hub.docker.com/) repository, so you can push the MDWebService-RTO image to Docker Hub repository, and set Azure to pull image from Docker Hub and run containers on Azure.

### Push Image to Docker Hub

The steps to push an MDWebService application image to Docker Hub repository are as follows.

Firstly, log in to [Docker Hub registry](https://hub.docker.com/) website, and select the **Create repository** button.

![figure-6](pics/docker_hub_1.png)

Next, input **Repository Name** as *mdwebservice-rto*, input a short description of your image, and choose **Public Visibility**. And then click the **Create** button.

![figure-7](pics/docker_hub_2.png)

Once the repository is created successfully, the page redirects you to your repository page which contains commands to [push](https://docs.docker.com/reference/cli/docker/image/push/) your local image to this repository.

![figure-8](pics/docker_hub_3.png)

Moving on to the next step to push image, open a command prompt and run the following command to log in to Docker Hub via the CLI.

```bash
docker login -u YOUR-USER-NAME
```

![figure-9](pics/docker_push_1.png)

Then input your Docker Hub account credential (*password* or *token*), then press the Enter button

![figure-10](pics/docker_push_2.png)

Once you have logged in to Docker Hub on the CLI, use the [docker tag]((https://docs.docker.com/reference/cli/docker/image/tag/)) command to give the *mdwebservice-rto* image a new name.

```bash
docker tag mdwebservice-rto wasinwrefinitiv/mdwebservice-rto
```

![figure-11](pics/docker_push_3.png)

Once the image has been tagged, run the docker push command. Please note that if you don't specify a tagname part, Docker uses a tag called latest.

```bash
docker push wasinwrefinitiv/mdwebservice-rto
```

![figure-12](pics/docker_push_4.png)

Then go back to Docker Hub website, the repository page shows your image detail. You can add the repository's category and overview based on your preference.

![figure-13](pics/docker_push_5.png)

You can find more detail about how to push an application image to Docker Hub on [Share the application](https://docs.docker.com/get-started/04_sharing_app/) document.

## Reference

- [Comparing Container Apps with other Azure container options](https://learn.microsoft.com/en-us/azure/container-apps/compare-options) article.
- [Difference between Azure Container Instances and Azure Container Apps - serverfault](https://serverfault.com/questions/1083358/difference-between-azure-container-instances-and-azure-container-apps) post.

[tbd]
