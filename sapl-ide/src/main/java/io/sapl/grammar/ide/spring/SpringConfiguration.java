package io.sapl.grammar.ide.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringConfiguration {

	@Bean
	public SpringContext springContext() {
		return new SpringContext();
	}
}
