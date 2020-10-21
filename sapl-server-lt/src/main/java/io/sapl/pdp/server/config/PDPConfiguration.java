package io.sapl.pdp.server.config;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import javax.annotation.PostConstruct;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.PDPConfigurationException;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class PDPConfiguration {

	private final ConfigurableApplicationContext applicationContext;
	private final SAPLServerLTProperties pdpProperties;

	private Collection<Object> policyInformationPoints;
	private Collection<Object> functionLibraries;

	@PostConstruct
	public void init() {
		policyInformationPoints = applicationContext.getBeansWithAnnotation(PolicyInformationPoint.class).values();
		functionLibraries = applicationContext.getBeansWithAnnotation(FunctionLibrary.class).values();
		log.info("Found {} PIPs", policyInformationPoints.size());
		log.info("Found {} FunctionLibraries", functionLibraries.size());
	}

	@Bean
	public PolicyDecisionPoint policyDecisionPoint() throws FunctionException, AttributeException, IOException,
			URISyntaxException, PolicyEvaluationException, PDPConfigurationException {
		var builder = EmbeddedPolicyDecisionPoint.builder();
		var path = pdpProperties.getPath();
		log.info("using monitored config file and policies from the filesystem: {}", path);
		builder.withFilesystemPDPConfigurationProvider(path);
		builder.withFilesystemPolicyRetrievalPoint(path, Builder.IndexType.IMPROVED);
		return bindComponentsToPDP(builder).build();
	}

	private Builder bindComponentsToPDP(Builder builder) throws AttributeException, FunctionException {
		for (var entry : policyInformationPoints) {
			log.debug("binding PIP to PDP: {}", entry.getClass().getSimpleName());
			builder.withPolicyInformationPoint(entry);
		}
		for (var entry : functionLibraries) {
			log.debug("binding FunctionLibrary to PDP: {}", entry.getClass().getSimpleName());
			builder.withFunctionLibrary(entry);
		}
		return builder;
	}

}
