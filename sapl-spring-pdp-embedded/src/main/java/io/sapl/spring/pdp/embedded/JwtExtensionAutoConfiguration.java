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
package io.sapl.spring.pdp.embedded;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.extension.jwt.JWTFunctionLibrary;
import io.sapl.extension.jwt.JWTPolicyInformationPoint;

@Configuration
@ConditionalOnClass(name = "io.sapl.extension.jwt.JWTFunctionLibrary")
public class JwtExtensionAutoConfiguration {

	@Bean
	public JWTFunctionLibrary jwtFunctionLibrary(ObjectMapper mapper) {
		return new JWTFunctionLibrary(mapper);
	}

	@Bean
	public JWTPolicyInformationPoint jwtPolicyInformationPoint(ObjectMapper mapper, WebClient.Builder builder) {
		return new JWTPolicyInformationPoint(builder);
	}

}
