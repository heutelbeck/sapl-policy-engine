package io.sapl.grammar.ide.contentassist;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;

@ComponentScan
@Configuration
public class SAPLIdeSpringTestConfiguration {

	@Bean
	public FunctionContext functionContext() {
		return new TestFunctionContext();
	}

	@Bean
	public AttributeContext attributeContext() {
		return new TestAttributeContext();
	}

}
