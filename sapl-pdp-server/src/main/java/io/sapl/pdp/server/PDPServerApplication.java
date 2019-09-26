package io.sapl.pdp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

/**
 * Starts a server providing REST endpoints for a policy decision point.
 * The server can be connected via HTTPS and is secured using basic authentication.
 * It can be configured using the application.properties file under src/main/resources.
 */
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class PDPServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(PDPServerApplication.class, args);
	}

}
