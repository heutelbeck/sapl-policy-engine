# SAPL Language server

This project contains a language server to provide generic IDE support for sapl policy files.
It provides features like content assist and highlighting of syntax errors.

The Is available under `target/sapl-language-server-${version}.jar` after compiling this project.
It is provided as a fat jar which can be started with java:
```shell
java -jar sapl-language-server-3.0.0.jar
```

Please consult the documentation of your favored development environment to find out how the SAPL Language server can
be integrated there.

For users of the Eclipse IDE it is recommended to use the dedicated
[Sapl Eclipse plugin](https://marketplace.eclipse.org/content/sapl-eclipse-plug) instead.


## Configure SAPL Language server in Kate

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
      "command": ["java", "-jar", "/path/to/your/downloaded/sapl-language-server-3.0.0.jar"],
      "url": "https://github.com/heutelbeck/sapl-policy-engine"
    }
  }
}
```
