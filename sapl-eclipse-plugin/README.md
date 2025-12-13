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

Build the plugin from the repository root:

```shell
mvn clean install -Peclipse -DskipTests -DskipCore
```

Then install in Eclipse:

1. Open **Help** > **Install New Software...**
2. Click **Add...** > **Local...**
3. Browse to `sapl-eclipse-plugin/sapl-eclipse-repository/target/repository`
4. Select **SAPL** from the list
5. Complete the wizard, trust the artifacts when prompted, and restart Eclipse

### Reinstalling During Development

When reinstalling the plugin after rebuilding, Eclipse and p2 aggressively cache artifacts.
If you encounter errors like `ClassNotFoundException` or artifact resolution failures after reinstalling,
you must clear these caches before reinstalling:

**Option A: Use the cleanup script**

```shell
# Windows (PowerShell)
.\clean-caches.ps1

# Linux/macOS
./clean-caches.sh
```

**Option B: Manual cleanup**

1. Clear Tycho/Maven caches (before rebuilding):

```shell
rm -rf ~/.m2/repository/.cache
rm -rf ~/.m2/repository/.meta
rm -rf ~/.m2/repository/p2
rm -rf ~/.m2/repository/io/sapl/sapl-eclipse-thirdparty
rm -rf ~/.m2/repository/org/eclipse/lsp4j
```

2. Clear Eclipse p2 caches (before reinstalling in Eclipse):

```shell
rm -rf ~/.p2/pool/plugins/io.sapl*
rm -rf ~/.p2/pool/plugins/sapl*
rm -rf ~/.p2/pool/features/io.sapl*
rm -rf ~/.p2/org.eclipse.equinox.p2.repository
rm -rf ~/.p2/org.eclipse.equinox.p2.core
```

3. Start Eclipse with clean flag:

```shell
eclipse -clean
```

4. Reinstall from local repository

On Windows, replace `~` with `%USERPROFILE%` or `C:\Users\<username>`.

### Common Build/Install Failures

| Error | Cause | Solution |
|-------|-------|----------|
| `Ambiguous main artifact of the project for org.eclipse.lsp4j.jsonrpc` | Tycho p2 cache corruption with multiple artifact versions | Run `clean-caches` script, or add `-Dtycho.p2.transport.min-cache-minutes=0` to bypass cache |
| `ClassNotFoundException` after reinstall | Old plugin JARs cached in Eclipse p2 pool | Clear `~/.p2/pool/plugins/io.sapl*` and `sapl*`, restart Eclipse with `-clean` |
| `The artifact file for osgi.bundle,io.sapl.eclipse-thirdparty was not found` | p2 metadata references stale artifact | Clear all p2 caches and rebuild |
| `Unknown packaging: eclipse-plugin` | Running Maven without Tycho (missing `-Peclipse` profile) | Use `mvn ... -Peclipse` or build from repository root |

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
