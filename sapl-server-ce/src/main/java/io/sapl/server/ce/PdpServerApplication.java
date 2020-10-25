package io.sapl.server.ce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import com.vaadin.flow.spring.annotation.EnableVaadin;

/**
 * The Spring Boot application of SAPL PDP Server CE.
 */
@EnableVaadin("io.sapl.server.ce")
@ComponentScan("io.sapl.server") // Scan PDP Endpoint
@SpringBootApplication(exclude = ErrorMvcAutoConfiguration.class)
public class PdpServerApplication {
	/**
	 * The main method.
	 * 
	 * @param args the arguments of the main method
	 */
	public static void main(String[] args) {
		SpringApplication.run(PdpServerApplication.class, args);
	}
}
