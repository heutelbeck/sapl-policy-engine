package io.sapl.grammar.ide.spring;

import org.springframework.context.ApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpringDependencyResolver {

	static ApplicationContext applicationContext;

	public SpringDependencyResolver() {
		System.out.println("SpringContext Constructor");
	}

	public <T extends Object> T resolve(Class<T> clazz) {
		ApplicationContext localContext = applicationContext;
		if (localContext == null) {
			throw new IllegalStateException("Spring ApplicationContext was not set");
		}
		return localContext.getBean(clazz);
	}
}
