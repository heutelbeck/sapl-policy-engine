# SAPL Language Server

A Language Server Protocol (LSP) implementation for SAPL policy files (`.sapl`) and SAPLTest files (`.sapltest`). Provides IDE support (semantic highlighting, diagnostics, content assist) in any LSP-compatible editor.

## Supported File Types

| Extension | Language | Description                                       |
|-----------|----------|---------------------------------------------------|
| `.sapl` | SAPL | Streaming Attribute Policy Language policy definitions  |
| `.sapltest` | SAPLTest | Test specifications for SAPL policies           |

## Features

- Semantic token highlighting (keywords, operators, strings, functions, etc.)
- Syntax error diagnostics with semantic validation
- Content assist for keywords and policy/test structure
- Multi-grammar support (automatic detection by file extension)
- Lightweight (~6 MB JAR vs ~30 MB for the Spring Boot version)
- Fast startup (no Spring context initialization)

## Prerequisites

**Java 21** or later is required.

Verify your installation:
```shell
java -version
```

## Building

Build both the library JAR and standalone executable:

```shell
cd sapl-language-server
mvn install -DskipTests
```

This produces two artifacts:
- `target/sapl-language-server-4.0.0-SNAPSHOT.jar` - Library JAR for Maven dependencies
- `target/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar` - Executable JAR with all dependencies

### Native Image

Build a native executable using GraalVM for significantly faster startup:

**Prerequisites:**
- GraalVM 21+ with `native-image` installed
- Set `GRAALVM_HOME` environment variable
- On Windows: Visual Studio Build Tools with C++ workload

```shell
# Linux/macOS
GRAALVM_HOME=/path/to/graalvm mvn package -Pnative -DskipTests

# Windows (from x64 Native Tools Command Prompt for VS 2022)
set GRAALVM_HOME=C:\path\to\graalvm
mvn package -Pnative -DskipTests
```

This produces `target/sapl-language-server` (or `.exe` on Windows).

**Benefits:**
- Near-instant startup (no JVM warmup)
- Single executable, no Java runtime required
- Ideal for editor plugins requiring fast server startup

**Updating Reflection Configuration:**

If you encounter reflection errors at runtime, use the GraalVM tracing agent to capture required metadata:

```shell
java -agentlib:native-image-agent=security-output-dir=native-security \
     -jar target/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar
```

Then copy the generated configs to `src/main/resources/META-INF/native-image/io.sapl/sapl-language-server/`.

## Running

The language server can be run either as a standalone JAR (requires Java 21+) or as a pre-built native binary (no Java required).

Pre-built native binaries are available from [GitHub Releases](https://github.com/heutelbeck/sapl-policy-engine/releases/tag/snapshot):

| Platform | Binary                                         |
|----------|------------------------------------------------|
| Linux x86_64 | `sapl-language-server-linux-amd64`         |
| macOS ARM64 | `sapl-language-server-macos-arm64`          |
| Windows x86_64 | `sapl-language-server-windows-amd64.exe` |

### Standard I/O Mode (Default)

Most LSP clients use stdio for communication:

```shell
# Using the native binary
./sapl-language-server-linux-amd64

# Using the JAR
java -jar sapl-language-server-4.0.0-SNAPSHOT-standalone.jar
```

### Socket Mode

For debugging or testing, use socket mode:

```shell
# Using the native binary
./sapl-language-server-linux-amd64 --socket --port=5007

# Using the JAR
java -jar sapl-language-server-4.0.0-SNAPSHOT-standalone.jar --socket --port=5007
```

## Editor Configuration

### Neovim

Three configuration variants are provided in `ide-support/`:

| Variant | Directory | Features |
|---------|-----------|----------|
| **Full LSP** | `nvim-lsp/` | Content assist, diagnostics, hover, semantic highlighting via LSP |
| **Syntax Only** | `nvim-highlighting-only/` | Vim syntax highlighting without LSP dependencies |
| **NixOS** | `nixos/` | Home Manager module with LSP, completion, and semantic highlighting |

#### Option 1: Full LSP Integration (`nvim-lsp/`)

The `ide-support/nvim-lsp/` directory contains a reference configuration demonstrating full SAPL LSP integration with semantic token highlighting.

**Features:**
- LSP client registration via nvim-lspconfig
- Completion via nvim-cmp
- Semantic highlighting from the language server
- Markdown rendering in documentation popups via noice.nvim + treesitter
- Filetype detection

**File Structure:**
```
nvim-lsp/
├── init.lua              # Plugin setup (lazy.nvim, nvim-cmp, noice.nvim, semantic colors)
├── filetype.lua          # Registers .sapl and .sapltest extensions
└── lua/
    └── sapl_lspconfig.lua  # LSP server registration (update JAR path here)
```

**Integration into Existing Config:**

Extract the relevant parts:

1. **Required:** Copy `lua/sapl_lspconfig.lua` to your Lua path
2. **Required:** Edit `sapl_lspconfig.lua` and update the JAR path on line 19:
   ```lua
   cmd = { 'java', '-jar', '/path/to/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar' },
   ```
3. **Required:** Add filetype registration from `filetype.lua`
4. **Optional:** Add semantic highlight colors from `init.lua` (the `setup_semantic_highlights()` function)
5. **Optional:** Configure noice.nvim for proper markdown rendering in completion popups

**Standalone Testing:**

Before testing, update the JAR path in `lua/sapl_lspconfig.lua` (line 19).

Test in isolation without affecting your main setup:

```shell
# Linux/macOS - test with .sapl file
NVIM_APPNAME=sapl-test nvim -u ide-support/nvim-lsp/init.lua test.sapl

# Linux/macOS - test with .sapltest file
NVIM_APPNAME=sapl-test nvim -u ide-support/nvim-lsp/init.lua test.sapltest

# Windows (PowerShell) - test with .sapl file
$env:NVIM_APPNAME="sapl-test"; nvim -u ide-support\nvim-lsp\init.lua test.sapl

# Windows (PowerShell) - test with .sapltest file
$env:NVIM_APPNAME="sapl-test"; nvim -u ide-support\nvim-lsp\init.lua test.sapltest
```

Plugins install to `~/.local/share/sapl-test/` (Linux/macOS) or `%LOCALAPPDATA%\sapl-test\` (Windows).

**Notes:**
- On Windows, use forward slashes in paths (e.g., `C:/path/to/server.jar`)
- The LSP disables vim syntax highlighting to avoid conflicts with semantic tokens
- noice.nvim changes Neovim's UI significantly; you may only want to take it as inspiration

#### Option 2: Syntax Highlighting Only (`nvim-highlighting-only/`)

Standalone syntax highlighting without LSP dependencies.

**File Structure:**
```
nvim-highlighting-only/
├── init.lua        # Basic editor settings
├── filetype.lua    # Registers .sapl and .sapltest extensions
└── syntax/
    └── sapl.vim    # Syntax highlighting rules and colors
```

Note: This variant provides syntax highlighting only for `.sapl` files. For `.sapltest` files, use the full LSP integration which provides semantic highlighting via the language server.

**Integration:**

1. Copy `syntax/sapl.vim` to `~/.config/nvim/syntax/`
2. Add filetype registration from `filetype.lua` to your config

### Visual Studio Code

#### Step 1: Install Syntax Highlighting

Copy `ide-support/vscode/io-sapl.sapl-language-support-1.0.0` to your VS Code extensions directory:
- **Linux/macOS**: `~/.vscode/extensions/`
- **Windows**: `%USERPROFILE%\.vscode\extensions\`

#### Step 2: Enable Language Server

Install [Generic LSP Client (v2)](https://marketplace.visualstudio.com/items?itemName=zsol.vscode-glspc) and configure:

```json
{
  "glspc.server.command": "java",
  "glspc.server.commandArguments": ["-jar", "/path/to/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar"],
  "glspc.server.languageId": ["sapl", "sapltest"]
}
```

### IntelliJ IDEA

Use the **LSP4IJ** plugin for Language Server Protocol support.

#### Step 1: Install LSP4IJ

**Settings** > **Plugins** > **Marketplace** > Search "LSP4IJ" > Install

#### Step 2: Register TextMate Bundle

**Settings** > **Languages & Frameworks** > **TextMate Bundles** > **+** > Select `ide-support/textmate`

#### Step 3: Configure Language Server

**Settings** > **Languages & Frameworks** > **Language Servers** > **+**

- **Name**: SAPL
- **Command**: `java -jar /path/to/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar`
- **Mappings**:
  - File pattern `*.sapl`, Language Id `SAPL`
  - File pattern `*.sapltest`, Language Id `SAPLTest`

### Eclipse

#### Step 1: Create External Tools Configuration

**Run** > **External Tools** > **External Tools Configurations...**

- Right-click **Program** > **New Configuration**
- **Name**: SAPL Language Server
- **Location**: `java`
- **Arguments**: `-jar /path/to/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar`

#### Step 2: Create Content Type

**Window** > **Preferences** > **General** > **Content Types**

- Select **Text** > **Add Child...** > Name: "SAPL Policy Language"
- Add file associations: `*.sapl`, `*.sapltest`

#### Step 3: Configure Language Server

**Preferences** > **Language Servers** > **Add...**

- **Content Type**: SAPL Policy Language
- **Launch Mode**: Program
- **Launch Configuration**: SAPL Language Server

#### Step 4: Setup TextMate Grammar

**Window** > **Preferences** > **TextMate** > **Grammar** > **Add...**

Browse to `ide-support/textmate/sapl.tmLanguage.json`

### Kate

Add a syntax highlighting file to Kate's configuration directory:
- Linux: `$HOME/.local/share/org.kde.syntax-highlighting/syntax/`
- Windows: `%USERPROFILE%\AppData\Local\org.kde.syntax-highlighting\syntax`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE language SYSTEM "language.dtd">
<language
    name="SAPL"
    version="1"
    kateversion="5.0"
    section="Other"
    extensions="*.sapl;*.sapltest" />
```

Configure LSP in **Settings** > **Configure Kate...** > **LSP Client** > **User Server Settings**:

```json
{
  "servers": {
    "sapl": {
      "command": ["java", "-jar", "/path/to/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar"],
      "url": "https://github.com/heutelbeck/sapl-policy-engine",
      "highlightingModeRegex": "^SAPL.*$"
    }
  }
}
```

Note: Kate uses the syntax highlighting definition name for language matching. Both `.sapl` and `.sapltest` files are covered by the extensions in the syntax definition.

### NixOS / Home Manager

A Nix module for Neovim with SAPL LSP integration is provided in `ide-support/nixos/sapl-nvim.nix`. It fetches the language server snapshot binary from [GitHub Releases](https://github.com/heutelbeck/sapl-policy-engine/releases/tag/snapshot) and configures LSP, completion, and semantic highlighting.

## Troubleshooting

### Server doesn't start

1. Verify Java 21+ is installed: `java -version`
2. Check the JAR exists and is executable
3. Test manually:
   ```shell
   echo 'Content-Length: 119

   {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"capabilities":{},"processId":null,"rootUri":null}}' | java -jar sapl-language-server-4.0.0-SNAPSHOT-standalone.jar
   ```
   You should see a JSON response starting with `Content-Length:`.

### Enable debug logging

Set the system property to enable verbose logging:

```shell
java -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG -jar sapl-language-server-4.0.0-SNAPSHOT-standalone.jar
```

### Neovim shows no highlighting

1. Verify the JAR path in `sapl_lspconfig.lua` is correct
2. Check LSP is attached: `:LspInfo` when editing a `.sapl` or `.sapltest` file
3. Verify filetype detection: `:set ft?` should show `sapl` or `sapltest`
4. Check for errors: `:LspLog`

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

## Supported Features

### Semantic Tokens

**SAPL files (`.sapl`):**
- `keyword`: SAPL keywords (import, policy, var, etc.)
- `macro`: Entitlements and combining algorithms (permit, deny, first, priority, etc.)
- `operator`: Operators (||, &&, ==, etc.)
- `string`: String literals
- `number`: Numeric literals
- `comment`: Comments (// and /* */)
- `variable`: Identifiers and variables
- `parameter`: Authorization subscription elements (subject, action, resource, environment)
- `function`: Function names
- `property`: Attribute names

**SAPLTest files (`.sapltest`):**
- `keyword`: Test structure keywords (requirement, scenario, given, when, then, expect)
- `macro`: Decision types (permit, deny, indeterminate, not-applicable)
- `operator`: Test operators and matchers
- `string`: String literals and identifiers
- `number`: Numeric literals
- `comment`: Comments (// and /* */)
- `type`: Type matchers (null, text, number, boolean, array, object)
- `function`: Mock function definitions

### Validation

**SAPL files:**
- Syntax error detection (ANTLR parse errors)
- Semantic validation:
  - Attribute access forbidden in target/schema expressions

**SAPLTest files:**
- Syntax error detection (ANTLR parse errors)
- Semantic validation:
  - Duplicate requirement names
  - Duplicate scenario names within a requirement
  - Proper mock definition structure

### Completion

**SAPL files:**
- Keyword completion (context-aware)
- Function completion (from registered libraries)
- Attribute completion (from registered PIPs)
- Variable completion (subscription elements, environment variables)
- Import completion (library and function names)

**SAPLTest files:**
- Test structure keywords (requirement, scenario, given, when, then, expect)
- Decision keywords (permit, deny, indeterminate, not-applicable)
- Mock keywords (function, attribute, maps, to, emits, stream)
- Import keywords (pip, static-pip, function-library, static-function-library)
- PDP configuration keywords (pdp, variables, combining-algorithm)
- Matcher keywords (matching, any, equals, containing, with, where)
- Type matcher keywords (null, text, number, boolean, array, object)

