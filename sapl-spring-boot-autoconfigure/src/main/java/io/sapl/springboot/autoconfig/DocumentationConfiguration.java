package io.sapl.springboot.autoconfig;

import java.util.ArrayList;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;
import io.sapl.spring.documentation.FunctionLibrariesDocumentation;
import io.sapl.spring.documentation.PolicyInformationPointsDocumentation;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ComponentScan("io.sapl.spring")
@AutoConfigureAfter({ PDPAutoConfiguration.class })
public class DocumentationConfiguration {

	@Bean
	public PolicyInformationPointsDocumentation pipDocumentation(PolicyDecisionPoint pdp) {
		log.info("Provisioning PIP Documentation Bean");
		if (pdp instanceof EmbeddedPolicyDecisionPoint) {
			EmbeddedPolicyDecisionPoint epdp = (EmbeddedPolicyDecisionPoint) pdp;
			return new PolicyInformationPointsDocumentation(epdp.getAttributeCtx().getDocumentation());
		}
		log.info("PIP Documentation will be empty for non-embedded PDP implementations.");
		return new PolicyInformationPointsDocumentation(new ArrayList<>());
	}

	@Bean
	public FunctionLibrariesDocumentation functionDocumentation(PolicyDecisionPoint pdp) {
		log.info("Provisioning Function Libraries Documentation Bean");
		if (pdp instanceof EmbeddedPolicyDecisionPoint) {
			EmbeddedPolicyDecisionPoint epdp = (EmbeddedPolicyDecisionPoint) pdp;
			return new FunctionLibrariesDocumentation(epdp.getFunctionCtx().getDocumentation());
		}
		log.info("Function Libraries Documentation will be empty for non-embedded PDP implementations.");
		return new FunctionLibrariesDocumentation(new ArrayList<>());
	}
}
