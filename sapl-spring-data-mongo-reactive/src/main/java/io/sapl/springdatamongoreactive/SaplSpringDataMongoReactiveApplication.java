package io.sapl.springdatamongoreactive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;

@SpringBootApplication(exclude = MongoAutoConfiguration.class)
public class SaplSpringDataMongoReactiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaplSpringDataMongoReactiveApplication.class, args);
    }

}
