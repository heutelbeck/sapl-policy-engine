/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

/**
 * Starts a server providing REST endpoints for a policy decision point. The server can be
 * connected via HTTPS and is secured using basic authentication. It can be configured
 * using the application.properties file under src/main/resources.
 */
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class PDPServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(PDPServerApplication.class, args);
	}

}
