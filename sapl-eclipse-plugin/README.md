# SAPL Eclipse Plugin

Eclipse IDE support for SAPL policy files and SAPL test files.

## Features

- Syntax highlighting
- Content assist (code completion)
- Code formatting
- Outline view
- Code folding
- Real-time syntax error reporting
- Auto editing (bracket matching, indentation)

## Installation

### From Update Site

1. In Eclipse, open **Help** > **Install New Software...**
2. Add the SAPL update site: `https://sapl.io/eclipse/update/`
3. Select **SAPL** from the list
4. Complete the installation wizard and restart Eclipse

### From Local Build

Build the plugin first:

```shell
cd sapl-eclipse-plugin
mvn clean install
```

Then install in Eclipse:

1. Open **Help** > **Install New Software...**
2. Click **Add...** > **Local...**
3. Browse to `sapl-eclipse-repository/target/repository`
4. Select **SAPL** from the list
5. Complete the wizard, trust the artifacts when prompted, and restart Eclipse

## Module Structure

| Module | Description |
|--------|-------------|
| `sapl-eclipse-ui` | Core plugin for `.sapl` policy files |
| `sapl-test-eclipse-ui` | Plugin for `.sapltest` test files |
| `sapl-eclipse-feature` | Eclipse feature bundling the plugins |
| `sapl-eclipse-repository` | P2 update site for distribution |
| `sapl-eclipse-target` | Target platform definition |
| `sapl-eclipse-thirdparty` | Third-party dependencies bundle |

## Development

For plugin development, install the **M2E - PDE Integration** plugin in Eclipse:

1. Open **Help** > **Install New Software...**
2. Use update site: `https://download.eclipse.org/technology/m2e/releases/latest`
3. Install **M2E - PDE Integration**

This improves integration between Maven, Eclipse PDE, and Tycho.
