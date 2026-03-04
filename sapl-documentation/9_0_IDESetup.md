---
layout: default
title: IDE Setup
nav_order: 900
has_children: false
---

## IDE and Editor Setup

The SAPL language server provides IDE support for `.sapl` policy files and `.sapltest` test files in any LSP-compatible editor. Features include semantic highlighting, syntax error diagnostics, semantic validation, context-aware content assist, document formatting, document symbols (outline), folding ranges, selection ranges, hover documentation, and variable rename.

### Obtaining the Language Server

The language server is available as a standalone JAR (requires Java 21+) or as a pre-built native binary (no Java required).

Pre-built native binaries are available from [GitHub Releases](https://github.com/heutelbeck/sapl-policy-engine/releases/tag/snapshot):

| Platform       | Binary                                   |
|----------------|------------------------------------------|
| Linux x86_64   | `sapl-language-server-linux-amd64`       |
| macOS ARM64    | `sapl-language-server-macos-arm64`       |
| Windows x86_64 | `sapl-language-server-windows-amd64.exe` |

To build the standalone JAR from source:

```shell
cd sapl-language-server && mvn install -am -DskipTests
```

This produces `target/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar`.

### Visual Studio Code

#### Step 1: Install Syntax Highlighting

Copy the [`ide-support/vscode/io-sapl.sapl-language-support-1.0.0`](https://github.com/heutelbeck/sapl-policy-engine/tree/main/sapl-language-server/ide-support/vscode/io-sapl.sapl-language-support-1.0.0) directory to your VS Code extensions directory:

- **Linux/macOS**: `~/.vscode/extensions/`
- **Windows**: `%USERPROFILE%\.vscode\extensions\`

#### Step 2: Enable Language Server

Install [Generic LSP Client (v2)](https://marketplace.visualstudio.com/items?itemName=zsol.vscode-glspc) and add the following to your VS Code settings:

```json
{
  "glspc.server.command": "java",
  "glspc.server.commandArguments": ["-jar", "/path/to/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar"],
  "glspc.server.languageId": ["sapl", "sapltest"]
}
```

### IntelliJ IDEA

#### Step 1: Install LSP4IJ

**Settings** > **Plugins** > **Marketplace** > Search "LSP4IJ" > Install

#### Step 2: Register TextMate Bundle

**Settings** > **Languages & Frameworks** > **TextMate Bundles** > **+** > Select the [`ide-support/textmate`](https://github.com/heutelbeck/sapl-policy-engine/tree/main/sapl-language-server/ide-support/textmate) directory

#### Step 3: Configure Language Server

**Settings** > **Languages & Frameworks** > **Language Servers** > **+**

- **Name**: SAPL
- **Command**: `java -jar /path/to/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar`
- **Mappings**:
  - File pattern `*.sapl`, Language Id `SAPL`
  - File pattern `*.sapltest`, Language Id `SAPLTest`

### Neovim

Three configuration variants are provided in the [`ide-support/`](https://github.com/heutelbeck/sapl-policy-engine/tree/main/sapl-language-server/ide-support) directory of the `sapl-language-server` module:

| Variant         | Directory                                                                                                                                       | Features                                                            |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| **Full LSP**    | [`nvim-lsp/`](https://github.com/heutelbeck/sapl-policy-engine/tree/main/sapl-language-server/ide-support/nvim-lsp)                             | Content assist, diagnostics, semantic highlighting via LSP          |
| **Syntax Only** | [`nvim-highlighting-only/`](https://github.com/heutelbeck/sapl-policy-engine/tree/main/sapl-language-server/ide-support/nvim-highlighting-only) | Vim syntax highlighting without LSP dependencies                    |
| **NixOS**       | [`nixos/`](https://github.com/heutelbeck/sapl-policy-engine/tree/main/sapl-language-server/ide-support/nixos)                                   | Home Manager module with LSP, completion, and semantic highlighting |

#### Full LSP Integration

The [`ide-support/nvim-lsp/`](https://github.com/heutelbeck/sapl-policy-engine/tree/main/sapl-language-server/ide-support/nvim-lsp) directory contains a reference configuration:

```
nvim-lsp/
├── init.lua              # Plugin setup (lazy.nvim, nvim-cmp, noice.nvim, semantic colors)
├── filetype.lua          # Registers .sapl and .sapltest extensions
└── lua/
    └── sapl_lspconfig.lua  # LSP server registration (update JAR path here)
```

To integrate into an existing config:

1. Copy `lua/sapl_lspconfig.lua` to your Lua path
2. Edit `sapl_lspconfig.lua` and update the JAR path on line 19:
   ```lua
   cmd = { 'java', '-jar', '/path/to/sapl-language-server-4.0.0-SNAPSHOT-standalone.jar' },
   ```
3. Add filetype registration from `filetype.lua`
4. Optionally add semantic highlight colors from `init.lua` (the `setup_semantic_highlights()` function)

To test in isolation without affecting your main config:

```shell
NVIM_APPNAME=sapl-test nvim -u ide-support/nvim-lsp/init.lua test.sapl
```

If highlighting or completion is not working, check `:LspInfo` to verify the server is attached, `:set ft?` to confirm filetype detection shows `sapl` or `sapltest`, and `:LspLog` for errors.

#### Syntax Highlighting Only

For lightweight syntax highlighting without LSP dependencies:

1. Copy [`syntax/sapl.vim`](https://github.com/heutelbeck/sapl-policy-engine/blob/main/sapl-language-server/ide-support/nvim-highlighting-only/syntax/sapl.vim) to `~/.config/nvim/syntax/`
2. Add filetype registration from [`filetype.lua`](https://github.com/heutelbeck/sapl-policy-engine/blob/main/sapl-language-server/ide-support/nvim-highlighting-only/filetype.lua)

This variant covers `.sapl` files only. For `.sapltest` highlighting, use the full LSP integration.

#### NixOS / Home Manager

A Nix module is provided at [`ide-support/nixos/sapl-nvim.nix`](https://github.com/heutelbeck/sapl-policy-engine/blob/main/sapl-language-server/ide-support/nixos/sapl-nvim.nix). It fetches the language server snapshot binary from GitHub Releases and configures LSP, completion, and semantic highlighting automatically.

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

Browse to [`ide-support/textmate/sapl.tmLanguage.json`](https://github.com/heutelbeck/sapl-policy-engine/blob/main/sapl-language-server/ide-support/textmate/sapl.tmLanguage.json).

### Kate

Add a syntax highlighting definition to Kate's configuration directory:

- **Linux**: `$HOME/.local/share/org.kde.syntax-highlighting/syntax/`
- **Windows**: `%USERPROFILE%\AppData\Local\org.kde.syntax-highlighting\syntax`

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

Then configure the LSP client in **Settings** > **Configure Kate...** > **LSP Client** > **User Server Settings**:

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

### Features

#### Semantic Tokens

The language server provides semantic token highlighting for both file types.

**SAPL files:**

| Token Type  | Highlighted Elements                                                                  |
|-------------|---------------------------------------------------------------------------------------|
| `keyword`   | SAPL keywords (`import`, `policy`, `var`, etc.)                                       |
| `macro`     | Entitlements and combining algorithms (`permit`, `deny`, `first`, `priority`, etc.)   |
| `operator`  | Operators (`\|\|`, `&&`, `==`, etc.)                                                  |
| `string`    | String literals                                                                       |
| `number`    | Numeric literals                                                                      |
| `comment`   | Single-line (`//`) and block (`/* */`) comments                                       |
| `variable`  | Identifiers and variables                                                             |
| `parameter` | Authorization subscription elements (`subject`, `action`, `resource`, `environment`)  |
| `function`  | Function names                                                                        |
| `property`  | Attribute names                                                                       |

**SAPLTest files:**

| Token Type  | Highlighted Elements                                                                    |
|-------------|-----------------------------------------------------------------------------------------|
| `keyword`   | Test structure keywords (`requirement`, `scenario`, `given`, `when`, `then`, `expect`)  |
| `macro`     | Decision types (`permit`, `deny`, `indeterminate`, `not-applicable`)                    |
| `operator`  | Test operators and matchers                                                             |
| `string`    | String literals and identifiers                                                         |
| `number`    | Numeric literals                                                                        |
| `comment`   | Single-line and block comments                                                          |
| `type`      | Type matchers (`null`, `text`, `number`, `boolean`, `array`, `object`)                  |
| `function`  | Mock function definitions                                                               |

#### Validation

**SAPL files** are validated for syntax errors (ANTLR parse errors) and semantic rules such as attribute access being forbidden in target and schema expressions.

**SAPLTest files** are validated for syntax errors, duplicate requirement names, and duplicate scenario names within a requirement.

#### Content Assist

**SAPL files**: context-aware completion for keywords, functions (from registered libraries), attributes (from registered PIPs), variables (subscription elements, environment variables), and import statements.

**SAPLTest files**: completion for test structure keywords, decision keywords, mock keywords, import keywords, PDP configuration keywords, and matcher/type keywords.

#### Document Formatting

The language server formats SAPL and SAPLTest documents according to standard conventions. Formatting is only applied when the document has no parse errors.

**SAPL files**: imports are sorted alphabetically, policies are separated by blank lines, expressions are indented consistently, and long lines are wrapped at operators.

**SAPLTest files**: requirements and scenarios are formatted with consistent indentation, given/when/expect/then blocks are properly aligned.

#### Document Symbols

The language server provides document symbols for the editor outline view.

**SAPL files**: policy sets, policies, and variable definitions appear in the outline.

**SAPLTest files**: requirements and scenarios appear in the outline, with scenarios nested under their requirement.

#### Folding Ranges

Collapsible regions are provided for multi-line blocks.

**SAPL files**: policy sets, policies, and block comments can be collapsed.

**SAPLTest files**: requirements, scenarios, and block comments can be collapsed.

#### Selection Ranges

Smart expand/shrink selection follows the AST structure, expanding from the innermost expression to enclosing statements, policies, and the full document.

#### Hover

**SAPL files**: hovering over function calls and attribute references shows documentation from registered function libraries and policy information points.

#### Rename

**SAPL files**: variable definitions declared with `var` can be renamed. The rename operation updates the definition and all references within the variable's scope. Set-level variables are renamed across all policies in the set. Policy-level variables are renamed within their defining policy, affecting only statements that follow the definition.
