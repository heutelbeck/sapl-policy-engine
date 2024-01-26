#This file is required to use the python scripts for local testing.
FROM gcr.io/oss-fuzz-base/base-builder-jvm

RUN apt-get update && apt-get install -y openjdk-17-jdk
RUN apt-get update && apt-get install -y maven

ENV MAVEN_HOME $SRC/maven/apache-maven-3.8.8/bin/mvn

ENV JAVA_HOME="$SRC/openjdk-17-jdk"
ENV PATH="$JAVA_HOME/bin:$SRC/protoc/bin:$PATH"
ENV JVM_LD_LIBRARY_PATH="/usr/lib/jvm/java-17-openjdk-amd64/lib/server"

ENV FUZZING_LANGUAGE=jvm

RUN git clone --depth 1 https://github.com/heutelbeck/sapl-policy-engine.git
WORKDIR sapl-policy-engine
COPY .clusterfuzzlite/build.sh $SRC/
COPY .clusterfuzzlite/*.java $SRC/
