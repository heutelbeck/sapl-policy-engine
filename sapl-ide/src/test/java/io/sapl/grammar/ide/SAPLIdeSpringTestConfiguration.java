package io.sapl.grammar.ide;

import org.springframework.context.annotation.Bean;

import io.sapl.grammar.ide.spring.SAPLIdeSpringConfiguration;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;

public class SAPLIdeSpringTestConfiguration extends SAPLIdeSpringConfiguration {

	@Bean
	@Override
	public FunctionContext functionContext() {
		return new TestFunctionContext();
	}
	
	@Bean
	@Override
	public AttributeContext attributeContext() {
		return new TestAttributeContext();
	}
}
