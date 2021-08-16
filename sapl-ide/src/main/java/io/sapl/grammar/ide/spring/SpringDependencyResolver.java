/**
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
package io.sapl.grammar.ide.spring;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is used to capture a Spring ApplicationContext for xText managed
 * classes to resolve bean dependencies. This class is both created by Spring
 * and Guice and uses the static applicationContext to resolve dependencies.
 */
@Slf4j
public class SpringDependencyResolver implements ApplicationContextAware {

	private static ApplicationContext applicationContext;

	/**
	 * Resolves a spring bean dependency in the current application context.
	 * 
	 * @param <T>   The type of the searched bean.
	 * @param clazz The class definition of the searched bean.
	 * @return Returns a bean that matches the provided class type.
	 */
	public <T extends Object> T resolve(Class<T> clazz) {
		ApplicationContext localContext = applicationContext;
		if (localContext == null) {
			throw new IllegalStateException("Spring ApplicationContext was not set");
		}
		return localContext.getBean(clazz);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		log.debug("Spring ApplicationContext set");
		SpringDependencyResolver.applicationContext = applicationContext;
	}
}
