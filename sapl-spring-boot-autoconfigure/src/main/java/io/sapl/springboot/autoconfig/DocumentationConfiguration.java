package io.sapl.springboot.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;
import io.sapl.spring.documentation.FunctionLibrariesDocumentation;
import io.sapl.spring.documentation.PolicyInformationPointsDocumentation;

@Configuration
@ComponentScan("io.sapl.spring")
@AutoConfigureAfter({ PDPAutoConfiguration.class })
public class DocumentationConfiguration {

	@Bean
	@ConditionalOnBean(EmbeddedPolicyDecisionPoint.class)
	public PolicyInformationPointsDocumentation pipDocumentation(EmbeddedPolicyDecisionPoint pdp) {
		return new PolicyInformationPointsDocumentation(pdp.getAttributeCtx().getDocumentation());
	}

	@Bean
	@ConditionalOnBean(EmbeddedPolicyDecisionPoint.class)
	public FunctionLibrariesDocumentation functionDocumentation(EmbeddedPolicyDecisionPoint pdp) {
		return new FunctionLibrariesDocumentation(pdp.getFunctionCtx().getDocumentation());
	}
}
