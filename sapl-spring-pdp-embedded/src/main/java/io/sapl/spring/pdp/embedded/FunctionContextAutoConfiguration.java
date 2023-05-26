/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Collection;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import io.sapl.api.functions.FunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoConfiguration
@AutoConfigureAfter(FunctionLibrariesAutoConfiguration.class)
public class FunctionContextAutoConfiguration {

	private final Collection<Object> functionLibraries;

	public FunctionContextAutoConfiguration(ConfigurableApplicationContext applicationContext) {
		functionLibraries = applicationContext.getBeansWithAnnotation(FunctionLibrary.class).values();
	}

	@Bean
	@ConditionalOnMissingBean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	FunctionContext functionContext() throws InitializationException {
		var functionContext = new AnnotationFunctionContext();
		for (var library : functionLibraries) {
			log.trace("loading FunctionLibrary: {}", library.getClass().getSimpleName());
			functionContext.loadLibrary(library);
		}
		return functionContext;
	}

}
