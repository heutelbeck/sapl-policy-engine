package io.sapl.springdatar2dbc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;

@SpringBootApplication
public class SaplSpringDataR2dbcApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaplSpringDataR2dbcApplication.class, args);
    }

}
