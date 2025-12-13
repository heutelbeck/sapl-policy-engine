# SAPL Language Server

This project provides a Language Server Protocol (LSP) implementation for SAPL policy files,
enabling IDE support (content assist, diagnostics, hover) in any LSP-compatible editor.

## Prerequisites

The SAPL Language Server requires **Java 21** or later.

Recommended distributions:
- [Eclipse Temurin](https://adoptium.net/) (recommended)
- [Amazon Corretto](https://aws.amazon.com/corretto/)
- [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)

Verify your installation:
```shell
java -version
```

The output should indicate version 21 or higher.

## Building

After building the project, the language server is available as a fat jar:

```shell
java -jar target/sapl-language-server-${version}.jar
```

## Editor Configuration

### Eclipse

#### Step 1: Create an External Tools Configuration

1. Go to **Run** > **External Tools** > **External Tools Configurations...**
2. Right-click **Program** > **New Configuration**
3. Configure:
   - **Name**: e.g., "SAPL Language Server"
   - **Location**: `java` (or full path like `/usr/bin/java`)
   - **Arguments**: `-jar /path/to/sapl-language-server-${version}.jar`
4. Click **Apply** and close the dialog

#### Step 2: Create a Content Type

1. Navigate to **Window** > **Preferences** > **General** > **Content Types**
2. Select **Text** and click **Add Child...**
3. Enter a name, e.g., "SAPL Policy Language"
4. Click **Add...** under "File associations" and enter `*.sapl`
5. Click **Apply**

#### Step 3: Configure the Language Server

1. Still in **Preferences**, go to **Language Servers**
2. Click **Add...** and configure:
   - **Content Type**: Select "SAPL Policy Language"
   - **Launch Mode**: Select **Program**
   - **Launch Configuration**: Select "SAPL Language Server"
3. Click **OK**

#### Step 4: Setup Syntax Highlighting

Eclipse's Generic Code Editor requires a TextMate grammar for syntax highlighting.
The grammar file is located at `ide-support/textmate/sapl.tmLanguage.json`.

1. Register the TextMate grammar:
   - Go to **Window** > **Preferences** > **TextMate** > **Grammar**
   - Click **Add...**
   - Browse to `sapl-language-server/ide-support/textmate/sapl.tmLanguage.json`
   - Click **Apply**

2. Associate the grammar with the SAPL content type:
   - In **Preferences**, go to **General** > **Content Types**
   - Select your "SAPL Policy Language" content type
   - In the "Associated grammars" section, click **Add...**
   - Select "source.sapl" (the scope name from the TextMate grammar)
   - Click **Apply and Close**

#### Step 5: Restart Eclipse

Restart Eclipse for the changes to take effect.

### Visual Studio Code

#### Step 1: Install Syntax Highlighting

Copy the folder `ide-support/vscode/io-sapl.sapl-language-support-1.0.0` to your VS Code extensions directory:
- **Linux/macOS**: `~/.vscode/extensions/`
- **Windows**: `%USERPROFILE%\.vscode\extensions\`

Restart VS Code. Files with `.sapl` extension will now have syntax highlighting.

#### Step 2: Enable Language Server (Optional)

For content assist, diagnostics, and hover information, install the
[Generic LSP Client (v2)](https://marketplace.visualstudio.com/items?itemName=zsol.vscode-glspc)
extension and configure it:

1. Open Command Palette: **Ctrl+Shift+P** (Windows/Linux) or **Cmd+Shift+P** (macOS)
2. Type "Preferences: Open User Settings (JSON)" and press Enter
3. Add the following configuration:
```json
{
  "glspc.server.command": "java",
  "glspc.server.commandArguments": ["-jar", "/path/to/sapl-language-server-4.0.0-SNAPSHOT.jar"],
  "glspc.server.languageId": ["sapl"]
}
```

### IntelliJ IDEA

The **LSP4IJ** plugin provides Language Server Protocol support with integrated TextMate grammar handling.

#### Step 1: Install the LSP4IJ Plugin

1. Open **Settings** > **Plugins** > **Marketplace**
2. Search for "LSP4IJ" and install it
3. Restart IntelliJ IDEA

#### Step 2: Register the TextMate Bundle

LSP4IJ uses TextMate grammars for syntax highlighting. A pre-configured bundle is included.

1. Open **Settings** > **Languages & Frameworks** > **TextMate Bundles**
2. Click the **+** button and select the `ide-support/textmate` folder
3. Click **OK** to add the bundle

#### Step 3: Configure the Language Server

1. Open **Settings** > **Languages & Frameworks** > **Language Servers**
2. Click the **+** button to add a new server
3. Configure:
   - **Name**: SAPL
   - **Command**: `java -jar /path/to/sapl-language-server-4.0.0-SNAPSHOT.jar`
   - **Mappings** Click **+**** File name pattern: `*.sapl` Language Id: `SAPL`
4. Click **OK**

#### Step 4: Restart IntelliJ IDEA

Restart IntelliJ for all changes to take effect. Open a `.sapl` file to verify syntax highlighting and language server features (content assist, diagnostics, hover).

### Neovim

Two configuration variants are provided in `ide-support/`:

| Variant | Directory | Features |
|---------|-----------|----------|
| **Full LSP** | `nvim-lsp/` | Content assist, diagnostics, hover, semantic highlighting via LSP |
| **Syntax Only** | `nvim-highlighting-only/` | Vim syntax highlighting without LSP dependencies |

Choose based on your needs: the LSP variant provides full IDE features but requires Java and the language server JAR; the syntax-only variant provides highlighting with zero external dependencies.

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
├── filetype.lua          # Registers .sapl extension
└── lua/
    └── sapl_lspconfig.lua  # LSP server registration (update JAR path here)
```

**Integration into Existing Config:**

Extract the relevant parts:

1. **Required:** Copy `lua/sapl_lspconfig.lua` to your Lua path
2. **Required:** Edit `sapl_lspconfig.lua` and update the JAR path on line 19:
   ```lua
   cmd = { 'java', '-jar', '/path/to/sapl-language-server-4.0.0-SNAPSHOT.jar' },
   ```
3. **Required:** Add filetype registration from `filetype.lua`
4. **Optional:** Add semantic highlight colors from `init.lua` (the `setup_semantic_highlights()` function)
5. **Optional:** Configure noice.nvim for proper markdown rendering in completion popups

**Standalone Testing:**

Before testing, update the JAR path in `lua/sapl_lspconfig.lua` (line 19).

Test in isolation without affecting your main setup:

```shell
# Linux/macOS
NVIM_APPNAME=sapl-test nvim -u ide-support/nvim-lsp/init.lua test.sapl

# Windows (PowerShell)
$env:NVIM_APPNAME="sapl-test"; nvim -u ide-support\nvim-lsp\init.lua test.sapl
```

Plugins install to `~/.local/share/sapl-test/` (Linux/macOS) or `%LOCALAPPDATA%\sapl-test\` (Windows).

**Notes:**
- On Windows, use forward slashes in paths (e.g., `C:/path/to/server.jar`)
- The LSP disables vim syntax highlighting to avoid conflicts with semantic tokens
- noice.nvim changes Neovim's UI significantly; you may only want to take it as inspiration on how to integrate it in your own custom setup

#### Option 2: Syntax Highlighting Only (`nvim-highlighting-only/`)

The `ide-support/nvim-highlighting-only/` directory provides standalone syntax highlighting without LSP dependencies. Use this when you want basic highlighting but don't need IDE features like completion or diagnostics.

**Features:**
- Vim syntax highlighting
- Filetype detection
- Zero external dependencies (no Java, no plugins required)

**File Structure:**
```
nvim-highlighting-only/
├── init.lua        # Basic editor settings
├── filetype.lua    # Registers .sapl extension
└── syntax/
    └── sapl.vim    # Syntax highlighting rules and colors
```

**Integration into Existing Config:**

1. Copy `syntax/sapl.vim` to `~/.config/nvim/syntax/` (or your runtime path)
2. Add filetype registration from `filetype.lua` to your config

**Standalone Testing:**

```shell
# Linux/macOS
NVIM_APPNAME=sapl-syntax nvim -u ide-support/nvim-highlighting-only/init.lua test.sapl

# Windows (PowerShell)
$env:NVIM_APPNAME="sapl-syntax"; nvim -u ide-support\nvim-highlighting-only\init.lua test.sapl
```

### Kate

It is required to add a syntax highlighting file for sapl files to Kate before configuring the LSP connection.
A file with the following content needs to be added as a xml file to the configuration directory.
For Linux, it is usually`$HOME/.local/share/org.kde.syntax-highlighting/syntax/` and for Windows
`%USERPROFILE%\AppData\Local\org.kde.syntax-highlighting\syntax`.
Details can be found at [Working with Syntax Highlighting](https://docs.kde.org/stable5/en/kate/katepart/highlight.html).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE language SYSTEM "language.dtd">
<language
    name="SAPL"
    version="1"
    kateversion="5.0"
    section="Other"
    extensions="*.sapl" />
```

After that it is possible to configure the LSP-Client for SAPL files in the settings of Kate by adding the following
configuration in
<kbd>Settings</kbd> > <kbd>Configure Kate...</kbd> > <kbd>LSP Client</kbd> > <kbd>User Server Settings</kbd>:

```json
{
  "servers": {
    "sapl": {
      "command": ["java", "-jar", "/path/to/your/downloaded/sapl-language-server-4.0.0-SNAPSHOT.jar"],
      "url": "https://github.com/heutelbeck/sapl-policy-engine"
    }
  }
}
```
