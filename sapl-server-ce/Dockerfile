FROM adoptopenjdk/openjdk11:alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
VOLUME /tmp
VOLUME /pdp/lib
VOLUME /etc/config
WORKDIR /pdp
ARG JAR_FILE
ADD ${JAR_FILE} /pdp/pdp.jar
EXPOSE 8080
ENTRYPOINT ["java","-Dspring.profiles.active=k8s","-Dloader.path=file:/pdp/lib","-Djava.security.egd=file:/dev/./urandom", "-jar","/pdp/pdp.jar"]
