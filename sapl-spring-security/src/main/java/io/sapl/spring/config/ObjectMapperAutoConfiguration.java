package io.sapl.spring.config;

import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;

@Configuration
public class ObjectMapperAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Configuration
	public static class ModuleRegistrationConfiguration {
		@Autowired
		void configureObjectMapper(final ObjectMapper mapper) {
			var module = new SimpleModule();
			module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
			module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
			mapper.registerModule(module);
			mapper.registerModule(new Jdk8Module());
		}
	}

}
