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
