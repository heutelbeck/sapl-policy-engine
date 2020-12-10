FROM adoptopenjdk/openjdk11:alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
VOLUME /tmp
VOLUME /pdp/settings
VOLUME /pdp/policies
VOLUME /pdp/pips
WORKDIR /pdp
ENV spring_profiles_active=docker
ARG JAR_FILE
ADD ${JAR_FILE} /pdp/pdp.jar
EXPOSE 8443
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom", "-Dloader.path=/pdp/pips/", "-jar","/pdp/pdp.jar", "org.springframework.boot.loader.PropertiesLauncher"]
