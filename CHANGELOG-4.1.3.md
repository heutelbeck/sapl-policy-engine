# SAPL 4.1.3

This release ships the container images of SAPL Node and the SAPL Playground with a Java runtime that fixes CVE-2026-114938 ("Java4Shell"). The code is unchanged from 4.1.2.

The vulnerability is a heap overflow in the Base64 decoder of OpenJDK 17.0.6 and later, 21, 25 and 27 on x86-64 processors with AVX-512 VBMI. An attacker who can send Base64 text to a Java application may crash it or run code in it. SAPL Node decodes the HTTP Basic credentials of every request before it authenticates them, and the SAPL Playground decodes every permalink, so both were reachable without credentials. The images of 4.1.2 and earlier carry an affected runtime. The 4.1.3 images carry BellSoft Liberica JRE 25.0.5.1 (SAPL Node) and 21.0.13.1 (SAPL Playground), which contain the fix.

The native binaries, the deb, rpm and Arch packages and the Nix packages of SAPL Node and the SAPL Language Server are not affected. They are built with GraalVM Native Image 25.0, which does not use the vulnerable decoder.

If you run the SAPL Node JAR, or embed the SAPL engine in your own application, the Java runtime is yours. Update it to 17.0.21.1, 21.0.13.1, 25.0.5.1 or 27.0.1.1 or later. Until you can, start the JVM with `-XX:-UseBASE64Intrinsics`, for example through `JAVA_TOOL_OPTIONS`, which disables the vulnerable decoder.
