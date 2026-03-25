# Changelog: EMA RTO Application on Azure

- **Last Updated:** March 2026
- **Compiler:** [Supported Java versions](https://developers.lseg.com/en/api-catalog/real-time-opnsrc/rt-sdk-java/documentation#api-compatibility-matrix), Maven, Docker
- **Prerequisites:** RTO Authentication Version 2 (or Version 1) credentials

> **Example Code Disclaimer:**
> ALL EXAMPLE CODE IS PROVIDED ON AN "AS IS" AND "AS AVAILABLE" BASIS FOR ILLUSTRATIVE PURPOSES ONLY. LSEG MAKES NO REPRESENTATIONS OR WARRANTIES OF ANY KIND, EXPRESS OR IMPLIED, AS TO THE OPERATION OF THE EXAMPLE CODE, OR THE INFORMATION, CONTENT, OR MATERIALS USED IN CONNECTION WITH THE EXAMPLE CODE. YOU EXPRESSLY AGREE THAT YOUR USE OF THE EXAMPLE CODE IS AT YOUR SOLE RISK.

---

## Summary of Changes

| Area | Change Type | Description |
|---|---|---|
| `pom.xml` | Dependency added | `spring-dotenv` for `.env` file support |
| `pom.xml` | Version update | `spring-boot-starter-parent` and `rtsdk` bumped |
| `pom.xml` | Exclusion added | EMA's `slf4j-jdk14` excluded to use Logback |
| `application.properties` | New properties | `ConnectionMode`, `RTOAuthenMode`, `Streaming` |
| `Consumer.java` (`AppClient`) | Updated | `handles` list, logging in all callbacks |
| `Consumer.java` (`Consumer`) | Updated | RTO/RTDS init, streaming + handle management |
| `MDController.java` | Bug fix | Wrapped `onApplicationEvent` in try/catch |
| `EmaConfig.xml` | New file | RTO Service Discovery and RTDS channel config |
| `Dockerfile` | Updated | Switched to multi-stage build |
| `.env` / `.env.example` | New files | Local credential configuration (`.env` is gitignored) |

---

## Detailed Changes

### `pom.xml`

#### New dependency: `spring-dotenv`

I added [spring-dotenv](https://github.com/paulschwarz/spring-dotenv) to load RTO credentials from environment variables or a `.env` file, following the [twelve-factor app config principle](https://12factor.net/config):

```xml
<dependency>
  <groupId>me.paulschwarz</groupId>
  <artifactId>spring-dotenv</artifactId>
  <version>4.0.0</version>
</dependency>
```

#### Version updates

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.12</version>
  <relativePath/>
</parent>
...
<properties>
  <java.version>11</java.version>
  <maven.test.skip>true</maven.test.skip>
  <rtsdk.version>3.9.2.0</rtsdk.version>
</properties>
```

#### Logging exclusion

EMA ships with `slf4j-jdk14`, which conflicts with Spring Boot's default Logback setup. I excluded it so the application uses Spring's Logback properly:

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

Application version set to:
```xml
<version>1.0-SNAPSHOT</version>
```

---

### `application.properties`

#### New: Connection mode

Added `MarketData.ConnectionMode` so the app supports both RTO (cloud) and RTDS (on-prem ADS) connections:

```ini
# Choose connection mode: RTDS or RTO
MarketData.ConnectionMode=RTO
#MarketData.ConnectionMode=RTDS
```

#### New: RTO authentication mode

Added `MarketData.RTOAuthenMode` to switch between Version 1 (Machine-ID) and Version 2 (Service Account) RTO authentication:

```ini
# RTO only — choose V1 (Machine-ID) or V2 (Service Account)
MarketData.RTOAuthenMode=V2
```

#### New: Streaming vs. snapshot

Added `MarketData.Streaming` to control whether the application requests a live stream or a one-time snapshot:

```ini
# true = streaming updates, false = snapshot (one-time image)
MarketData.Streaming=true
```

The corresponding Java field in `Consumer.java`:

```java
@Value("${MarketData.Streaming}")
private boolean streaming;
```

---

### `Consumer.java` — `AppClient` Class

#### Subscription handle tracking

To properly manage streaming subscriptions, the `AppClient` now maintains a `handles` list. Each handle is captured when a Refresh message arrives, so open streams can be unregistered before the next batch request:

```java
private List<Long> handles = new ArrayList<>();

public void onRefreshMsg(RefreshMsg refreshMsg, OmmConsumerEvent event) {
    try {
        LOG.info("Refresh: " + refreshMsg);
        handles.add(event.handle());
        ...
    }
}
```

#### Updated callbacks

All three message callbacks now log their messages. The `onUpdateMsg` callback (previously a no-op) now logs incoming update messages:

```java
public void onStatusMsg(StatusMsg statusMsg, OmmConsumerEvent event) {
    if (statusMsg.hasName()) {
        try {
            LOG.info("Status: " + statusMsg);
            ...
        }
    }
}

public void onUpdateMsg(UpdateMsg updateMsg, OmmConsumerEvent event) {
    try {
        LOG.info("Update: " + updateMsg);
        ...
    }
}
```

#### New: `getHandles()` and `setHandles()` accessors

Added getter and setter so the `Consumer` class can access and manage the handles list from outside `AppClient`:

```java
public List<Long> getHandles() {
    return handles;
}

public void setHandles(List<Long> handles) {
    this.handles = handles;
}
```

---

### `Consumer.java` — `Consumer` Class

#### RTO credential injection

Credentials are loaded from environment variables (or a `.env` file via `spring-dotenv`) rather than being hardcoded. This keeps secrets out of the codebase:

```java
// V2 Service Account
@Value("${CLIENT_ID}")
private String client_id;

@Value("${CLIENT_SECRET}")
private String client_secret;

// V1 Machine-ID
@Value("${RTO_MACHINE_ID}")
private String rto_machine_id;

@Value("${RTO_PASSWORD}")
private String rto_password;

@Value("${RTO_APPKEY}")
private String rto_appkey;
```

#### Updated `initialize()` method

The `initialize()` method now:
- Creates a shared `OmmConsumerConfig` object
- Branches on `MarketData.ConnectionMode` to configure RTDS or RTO
- For RTO, further branches on `MarketData.RTOAuthenMode` for V1 vs. V2 auth

```java
config = EmaFactory.createOmmConsumerConfig();

if (connectionMode.equals("RTDS")) {
    consumer = EmaFactory.createOmmConsumer(config.host(hostName + ":" + port).username(userName));

} else if (connectionMode.equals("RTO")) {
    if (rtoAuthenMode.equals("V1")) {
        config.consumerName("Consumer_RTO").username(rto_machine_id).password(rto_password).clientId(rto_appkey);
    } else if (rtoAuthenMode.equals("V2")) {
        config.consumerName("Consumer_RTO").clientId(client_id).clientSecret(client_secret);
    }
    consumer = EmaFactory.createOmmConsumer(config);
}
```

#### Updated `synchronousRequest()` method

Before each batch request, existing stream handles are unregistered to avoid conflicts with any open streaming subscriptions from a previous call:

```java
handles = appClient.getHandles();
if (handles != null && !handles.isEmpty()) {
    for (long handle : handles) {
        consumer.unregister(handle);
    }
    handles.clear();
}
consumer.registerClient(
    EmaFactory.createReqMsg().serviceName(serviceName).payload(eList).interestAfterRefresh(streaming),
    appClient,
    bRequest
);
```

#### New: `.env` and `.env.example` files

Added a `.env.example` template (and a `.env` file that is gitignored) for setting credentials locally:

```ini
# Authentication V2 (Service Account)
CLIENT_ID=<Your Auth V2 Client-ID>
CLIENT_SECRET=<Your Auth V2 Client-Secret>

# Authentication V1 (Machine-ID)
RTO_MACHINE_ID=<Your Auth V1 Machine-ID>
RTO_PASSWORD=<Your Auth V1 Password>
RTO_APPKEY=<Your Auth V1 AppKey>
```

---

### `MDController.java`

Wrapped the `onApplicationEvent` callback in a try/catch block. Without this, an exception during consumer initialization would propagate unhandled and crash the Spring context startup:

```java
@EventListener
public void onApplicationEvent(ContextRefreshedEvent event) {
    try {
        LOG.info("Initialize the consumer and connect to market data system....");
        ommCons.initialize();
    } catch (Exception e) {
        LOG.error("Failed to initialize consumer: {}", e.getMessage(), e);
    }
}
```

---

### `EmaConfig.xml` (New File)

This new configuration file is the easiest way to connect to RTO using **Service Discovery** — the RTSDK automatically resolves the RTO endpoint based on the configured region.

Key settings:
- **`Location`** — specifies the cloud region (e.g. `ap-southeast`). Check with your LSEG representative for valid values, or refer to the [RTO Install and Config Guide](https://developers.lseg.com/en/api-catalog/real-time-opnsrc/rt-sdk-java/documentation#refinitiv-real-time-optimized-install-and-config-guide).
- **`EnableSessionManagement`** — lets the RTSDK handle token acquisition and refresh automatically.

#### RTO consumer and channel (`Consumer_RTO` / `Channel_RTO`)

```xml
<Consumer>
  <Name value="Consumer_RTO"/>
  <Channel value="Channel_RTO"/>
  <Dictionary value="Dictionary_1"/>
  <MaxDispatchCountApiThread value="6500"/>
  <MaxDispatchCountUserThread value="6500"/>
  <XmlTraceToStdout value="0"/>
</Consumer>
...
<Channel>
  <Name value="Channel_RTO"/>
  <ChannelType value="ChannelType::RSSL_ENCRYPTED"/>
  <Location value="ap-southeast"/>
  <EnableSessionManagement value="1"/>
</Channel>
```

#### RTDS consumer and channel (`Consumer_RTDS` / `Channel_RTDS`)

Also added for completeness, so the same `EmaConfig.xml` covers both connection modes:

```xml
<Consumer>
  <Name value="Consumer_RTDS"/>
  <Channel value="Channel_RTDS"/>
  <Dictionary value="Dictionary_1"/>
  <XmlTraceToStdout value="0"/>
</Consumer>
...
<Channel>
  <Name value="Channel_RTDS"/>
  <ChannelType value="ChannelType::RSSL_SOCKET"/>
  <Host value="localhost"/>
  <Port value="14002"/>
</Channel>
```

---

### `Dockerfile`

Switched to a [multi-stage build](https://docs.docker.com/guides/docker-concepts/building-images/multi-stage-builds) to keep the final image lean — the Maven build stage is discarded and only the compiled JAR is carried into the runtime image:

```dockerfile
FROM --platform=linux/amd64 maven:3.9.6-eclipse-temurin-11-focal as builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean -e -B package

FROM --platform=linux/amd64 eclipse-temurin:11-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/MDWebService-0.0.1-SNAPSHOT.jar .
COPY EmaConfig.xml .
CMD ["java", "-jar", "./MDWebService-0.0.1-SNAPSHOT.jar"]
```

Also added a `.dockerignore` file to exclude unnecessary project files from the Docker build context.

---

That covers everything I changed in this project. For running and deployment instructions, see:

- [README.md](./README.md) — How to run with RTO connection
- [AZURE.md](./AZURE.md) — How to deploy to Azure Cloud Service