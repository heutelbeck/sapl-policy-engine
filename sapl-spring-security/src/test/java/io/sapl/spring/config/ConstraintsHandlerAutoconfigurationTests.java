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
package io.sapl.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.server.reactive.ServerHttpRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;

class ConstraintsHandlerAutoconfigurationTests {

	@Test
	void whenRan_thenMapperIsAvailableAndModulesAreRegistered() {
		var contextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ConstraintsHandlerAutoconfiguration.class))
				.withBean(ObjectMapper.class, () -> {
					var mapper = new ObjectMapper();
					SimpleModule module = new SimpleModule();
					module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
					module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
					module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
					mapper.registerModule(module);
					return mapper;
				});
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(ConstraintEnforcementService.class);
		});
	}

}
