/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.server.ce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import com.vaadin.flow.spring.annotation.EnableVaadin;

/**
 * The Spring Boot application of SAPL PDP Server CE.
 */
@EnableVaadin("io.sapl.server.ce")
@ComponentScan("io.sapl.server") // Scan PDP Endpoint
@ComponentScan("io.sapl.grammar.ide.contentassist") // Scan Editor Backend
@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
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
