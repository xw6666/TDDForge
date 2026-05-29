# OpenGiraffe Java

Auto R&D Task Orchestration Daemon built with Java 21 + Spring Boot 3 + MySQL 8.

## Prerequisites

- Java 21 (OpenJDK 21 recommended)
- Maven 3.8+
- MySQL 8 (for production; not required for basic startup verification)

## Build and Test

```bash
mvn clean test
```

## Run

```bash
mvn spring-boot:run
```

The application starts on port 8778 by default. Verify it is running:

```bash
curl http://localhost:8778/actuator/health
```

## Package Structure

```
com.opengiraffe
  config
  domain
  persistence
  opencode
  agent
  git
  orchestrator
  web
  service
  util
```
