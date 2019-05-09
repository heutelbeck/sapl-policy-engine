package io.sapl.pdp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class PDPServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(PDPServerApplication.class, args);
	}

}
