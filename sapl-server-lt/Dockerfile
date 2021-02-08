FROM adoptopenjdk/openjdk11:alpine
LABEL org.opencontainers.image.source https://github.com/heutelbeck/sapl-policy-engine/sapl-server-lt
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
VOLUME /tmp
VOLUME /pdp/data
WORKDIR /pdp
ENV spring_profiles_active=docker
ARG JAR_FILE
ADD ${JAR_FILE} /pdp/pdp.jar
EXPOSE 8443
ENTRYPOINT ["java","-Dloader.path=file:/pdp/data/lib","-Djava.security.egd=file:/dev/./urandom", "-jar","/pdp/pdp.jar"]
