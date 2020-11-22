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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ComponentScan("io.sapl.spring")
@AutoConfigureAfter({ PDPAutoConfiguration.class })
public class DocumentationAutoConfiguration {

	@Bean
	public PolicyInformationPointsDocumentation pipDocumentation(AttributeContext attributeCtx) {
		log.info("Provisioning PIP Documentation Bean");
		for (var doc : attributeCtx.getDocumentation()) {
			log.debug("AttributeCtx contains: {}", doc.getName());
		}
		return new PolicyInformationPointsDocumentation(attributeCtx.getDocumentation());
	}

	@Bean
	public FunctionLibrariesDocumentation functionDocumentation(FunctionContext functionCtx) {
		log.info("Provisioning Function Libraries Documentation Bean");
		for (var doc : functionCtx.getDocumentation()) {
			log.debug("FunctionCtx contains: {}", doc.getName());
		}
		return new FunctionLibrariesDocumentation(functionCtx.getDocumentation());
	}
}
