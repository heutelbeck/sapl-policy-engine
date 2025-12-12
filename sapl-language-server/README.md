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

### Neovim (via nvim-lspconfig)

A complete Neovim configuration is available in `ide-support/nvim/`. It includes syntax highlighting,
LSP integration, and code completion support.

#### Fresh Install

Copy the entire `ide-support/nvim/` folder to your Neovim configuration directory:
- **Linux/macOS**: `~/.config/nvim/`
- **Windows**: `%LOCALAPPDATA%\nvim\`

Then edit `lua/sapl_lspconfig.lua` and update the path to your `sapl-language-server-4.0.0-SNAPSHOT.jar`.
On Windows, use forward slashes in the path (e.g., `C:/path/to/server.jar`).

On first launch, Neovim will automatically download the required plugins.

#### Basic Usage

| Key | Action |
|-----|--------|
| `i` | Enter Insert mode (type text) |
| `Esc` | Return to Normal mode |
| `Ctrl+Space` | Trigger completion |
| `Tab` / `Shift+Tab` | Navigate completion list |
| `Enter` | Accept completion |
| `:w` | Save file |
| `:q` | Quit |

#### Existing Configuration

If you already have a Neovim configuration, merge the contents of these files:
- `filetype.lua` - adds `.sapl` filetype detection
- `syntax/sapl.vim` - syntax highlighting rules
- `lua/sapl_lspconfig.lua` - registers the SAPL language server with nvim-lspconfig

The `init.lua` includes `nvim-cmp` for completion UI. If you use a different completion plugin,
adapt accordingly.

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
