# SAPL Clusterfuzzlite

This folder is used to integrate SAPL with Project [Clusterfuzzlite](https://google.github.io/clusterfuzzlite/).

However, this integration is not used because **Clusterfuzzlite uses** Docker images, which use **JDK15**.

**SAPL requires** at least **JDK21**.

For this reason, the project uses its own tests using JUnit. (which uses libfuzzer
under the hood as well as Clusterfuzzlite does). For more information on the fuzzing method used, see here [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer)

