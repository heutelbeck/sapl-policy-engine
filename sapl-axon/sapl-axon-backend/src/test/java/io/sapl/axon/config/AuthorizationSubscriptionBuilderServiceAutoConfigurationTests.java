/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.axon.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.sapl.axon.utilities.AuthorizationSubscriptionBuilderService;

public class AuthorizationSubscriptionBuilderServiceAutoConfigurationTests {

	@Test
	void whenRan_thenConfigurationIsAvailable() {
		var logger = (Logger) LoggerFactory.getLogger("org.springframework.beans.factory.support.DefaultListableBeanFactory");
		logger.setLevel(Level.ERROR);
		var contextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(AuthorizationSubscriptionBuilderServiceAutoConfiguration.class))
				.withBean(ObjectMapper.class, () -> {
					var mapper = new ObjectMapper();
					SimpleModule module = new SimpleModule();
					mapper.registerModule(module);
					return mapper;
				});
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(AuthorizationSubscriptionBuilderService.class);
		});
	}
	
}
