package io.sapl.springboot.autoconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.interpreter.DefaultSAPLInterpreter;

@Configuration
public class InterpreterConfiguration {

	@Bean
	public SAPLInterpreter parser() {
		return new DefaultSAPLInterpreter();
	}

}
