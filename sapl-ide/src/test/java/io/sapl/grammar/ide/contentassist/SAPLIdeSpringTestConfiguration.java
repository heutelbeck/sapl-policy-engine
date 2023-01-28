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
package io.sapl.grammar.ide.contentassist;

import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.grammar.ide.contentassist.filesystem.FileSystemVariablesAndCombinatorSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;

@ComponentScan
@Configuration
public class SAPLIdeSpringTestConfiguration {

	@Bean
	FunctionContext functionContext() {
		return new TestFunctionContext();
	}

	@Bean
	AttributeContext attributeContext() {
		return new TestAttributeContext();
	}

	@Bean
	public VariablesAndCombinatorSource variablesAndCombinatorSource() throws InitializationException {
		String configPath = "src/test/resources";
		return new FileSystemVariablesAndCombinatorSource(configPath);
	}

}
