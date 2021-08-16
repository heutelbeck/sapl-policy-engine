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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.grammar.ide.contentassist.DefaultLibraryAttributeFinder;
import io.sapl.grammar.ide.contentassist.LibraryAttributeFinder;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pip.ClockPolicyInformationPoint;
import lombok.SneakyThrows;

/**
 * The @see io.sapl.grammar.ide.contentassist.SaplContentProposalProvider is
 * managed by the xText language server so this configuration is used to acquire
 * a Spring ApplicationContext that can be used to load beans in xText managed
 * classes. This configuration initializes the beans used for the auto
 * completion in a spring application context. Without these classes the auto
 * completion will not work. The FunctionContext and AttributeContext only have
 * to be created if no other context was created yet.
 */
@Configuration
public class SAPLIdeSpringConfiguration {

	@Bean
	public SpringDependencyResolver springDependencyResolver() {
		return new SpringDependencyResolver();
	}
	
	@Bean LibraryAttributeFinder libraryAttributeFinder() {
		return new DefaultLibraryAttributeFinder();
	}

	@Bean
	@SneakyThrows
	@ConditionalOnMissingBean
	public FunctionContext functionContext() {
		FunctionContext context = new AnnotationFunctionContext();
		context.loadLibrary(new FilterFunctionLibrary());
		context.loadLibrary(new StandardFunctionLibrary());
		context.loadLibrary(new TemporalFunctionLibrary());
		return context;
	}

	@Bean
	@SneakyThrows
	@ConditionalOnMissingBean
	public AttributeContext attributeContext() {
		AnnotationAttributeContext context = new AnnotationAttributeContext();
		context.loadPolicyInformationPoint(new ClockPolicyInformationPoint());
		return context;
	}
}
