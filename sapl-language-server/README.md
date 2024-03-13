# SAPL Language server

This project contains a language server to provide generic IDE support for sapl policy files.
It provides features like content assist and highlighting of syntax errors.

The language server can be downloaded from the
[Sonatype Repository](https://s01.oss.sonatype.org/content/repositories/snapshots/io/sapl/sapl-language-server/3.0.0-SNAPSHOT/)
or is available under `target/sapl-language-server-${version}.jar` after compiling this project.
It is provided as a fat jar which can be started with java:
```shell
java -jar sapl-language-server-3.0.0-SNAPSHOT.jar
```

Please consult the documentation of your favored development environment to find out how the SAPL Language server can
be integrated there.

For users of the Eclipse IDE it is recommended to use the dedicated
[Sapl Eclipse plugin](https://marketplace.eclipse.org/content/sapl-eclipse-plug) instead.
