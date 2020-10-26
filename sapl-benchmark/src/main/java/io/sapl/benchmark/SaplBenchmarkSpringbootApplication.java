package io.sapl.benchmark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"io.sapl"})
public class SaplBenchmarkSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaplBenchmarkSpringbootApplication.class, args);
    }

}
