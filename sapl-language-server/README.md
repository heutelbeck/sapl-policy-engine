# SAPL Language Server

A Language Server Protocol (LSP) implementation for SAPL policy files (`.sapl`) and SAPLTest files (`.sapltest`).

For editor setup instructions and feature documentation, see the [IDE Setup](https://sapl.io/docs/latest/9_0_IDESetup/) chapter in the SAPL documentation.

## Building

Build both the library JAR and standalone executable:

```shell
cd sapl-language-server && mvn install -DskipTests
```

This produces two artifacts:
- `target/sapl-language-server-4.0.0-SNAPSHOT.jar` - Library JAR for Maven dependencies
- `target/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar` - Executable JAR with all dependencies

### Native Image

Build a native executable using GraalVM for significantly faster startup.

**Prerequisites:**
- GraalVM 21+ with `native-image` installed
- Set `GRAALVM_HOME` environment variable
- On Windows: Visual Studio Build Tools with C++ workload

```shell
GRAALVM_HOME=/path/to/graalvm mvn package -Pnative -DskipTests
```

This produces `target/sapl-language-server` (or `.exe` on Windows).

**Updating Reflection Configuration:**

If you encounter reflection errors at runtime, use the GraalVM tracing agent to capture required metadata:

```shell
java -agentlib:native-image-agent=security-output-dir=native-security -jar target/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar
```

Then copy the generated configs to `src/main/resources/META-INF/native-image/io.sapl/sapl-language-server/`.

## Running

### Standard I/O Mode (Default)

Most LSP clients use stdio for communication:

```shell
java -jar sapl-language-server-4.0.0-SNAPSHOT-standalone.jar
```

### Socket Mode

For debugging or testing, use socket mode:

```shell
java -jar sapl-language-server-4.0.0-SNAPSHOT-standalone.jar --socket --port=5007
```

### Debug Logging

```shell
java -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG -jar sapl-language-server-4.0.0-SNAPSHOT-standalone.jar
```

## Embedding in Applications

For embedding in applications (e.g., Vaadin editors):

```java
import io.sapl.lsp.launcher.SAPLLanguageServerLauncher;

// Create an embedded server
var server = SAPLLanguageServerLauncher.createEmbeddedServer();

// Configure with PDP documentation
var configManager = server.getConfigurationManager();
configManager.createConfiguration("my-config", documentationBundle, variables);

// Connect to a client (implement your own transport)
server.connect(languageClient);
```
