# SAPL Language Server

A Language Server Protocol (LSP) implementation for SAPL policy files (`.sapl`) and SAPLTest files (`.sapltest`).

For editor setup instructions and feature documentation, see the [IDE Setup](https://sapl.io/docs/latest/9_0_IDESetup/) chapter in the SAPL documentation.

## Building

Build both the library JAR and standalone executable:

```shell
cd sapl-language-server && mvn install -DskipTests
```

This produces two artifacts:
- `target/sapl-language-server-4.2.0-SNAPSHOT.jar` - Library JAR for Maven dependencies
- `target/sapl-language-server-4.2.0-SNAPSHOT-standalone.jar` - Executable JAR with all dependencies

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

**Reflection Configuration:**

The reflection metadata covers all model classes in the resolved LSP4J dependency, including client capabilities that the server does not use. `NativeReflectionMetadataTests` checks this coverage when LSP4J is upgraded. Keep fields and unsafe allocation registered for new Gson model types. The size-bounded function broker cache requires both Caffeine's `SSMS` cache factory and `PSMS` node factory.

Use the GraalVM tracing agent to capture additional metadata:

```shell
java -agentlib:native-image-agent=config-output-dir=native-config -jar target/sapl-language-server-4.2.0-SNAPSHOT-standalone.jar
```

Merge additional entries into `src/main/resources/META-INF/native-image/io.sapl/sapl-language-server/`; preserve registrations for unobserved client capabilities.

Run the native stdio regression checks after building:

```shell
python3 src/test/python/native_lsp_smoke.py target/sapl-language-server
```

These checks send both minimal failing capability payloads and Neovim 0.12's full default capabilities, then exercise keyword, function, and attribute hover and function completion. The capabilities fixture was generated with `vim.lsp.protocol.make_client_capabilities()` in Neovim 0.12.5. The native build workflow runs these checks on Linux amd64/arm64, Windows amd64, and macOS arm64 before uploading binaries.

## Running

### Standard I/O Mode (Default)

Most LSP clients use stdio for communication:

```shell
java -jar sapl-language-server-4.2.0-SNAPSHOT-standalone.jar
```

### Socket Mode

For debugging or testing, use socket mode:

```shell
java -jar sapl-language-server-4.2.0-SNAPSHOT-standalone.jar --socket --port=5007
```

### Debug Logging

```shell
java -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG -jar sapl-language-server-4.2.0-SNAPSHOT-standalone.jar
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
