package io.sapl.grammar.ide.spring;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpringContext implements ApplicationContextAware {
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		log.debug("Spring ApplicationContext set");
		SpringDependencyResolver.applicationContext = applicationContext;
	}
}