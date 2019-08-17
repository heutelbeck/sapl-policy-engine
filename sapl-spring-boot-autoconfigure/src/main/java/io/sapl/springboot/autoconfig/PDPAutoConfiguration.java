package io.sapl.springboot.autoconfig;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.PDPConfigurationException;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint.Builder;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import io.sapl.spring.PolicyEnforcementFilterPEP;
import io.sapl.spring.SAPLProperties;
import io.sapl.spring.SAPLProperties.Remote;
import io.sapl.spring.constraints.ConstraintHandlerService;
import lombok.extern.slf4j.Slf4j;

/**
 * This automatic configuration will provide you several beans to deal with SAPL by
 * default. <br/>
 * If you do not change it, the default configuration (see {@link SAPLProperties}) will
 * configure an {@link EmbeddedPolicyDecisionPoint} for you. <br/>
 * <br/>
 * <h2>Configure an EmbeddedPolicyDecisionPoint</h2> To have a bean instance of an
 * {@link EmbeddedPolicyDecisionPoint} just activate it in your
 * <i>application.properties</i>-file (or whatever spring supported way to provide
 * properties you wish to use. c.f. <a href=
 * "https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html">Spring
 * Boot Documentation on config parameters</a>) <br/>
 * Do not forget to provide the minimal required files in your policy path! Example
 * Snippet from .properties:<br/>
 * <code>
 * io.sapl.type=embedded
 * <br/>
 * io.sapl.embedded.policy-path=classpath:path/to/policies
 * </code> <br/>
 * <br/>
 * <h2>Configure a RemotePolicyDecisionPoint</h2> To have a bean instance of a
 * {@link RemotePolicyDecisionPoint} just activate it in your
 * <i>application.properties</i>-file (or whatever spring supported way to provide
 * properties you wish to use. <br/>
 * c.f. <a href=
 * "https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html">Spring
 * Boot Documentation on config parameters</a>) <br/>
 * Example Snippet from .properties: <br/>
 * <code>
 * io.sapl.type=remote<br/>
 * io.sapl.remote.host=myhost.example.io<br/>
 * io.sapl.remote.port=8443<br/>
 * io.sapl.remote.key=username<br/>
 * io.sapl.remote.secret=password
 * </code> <br/>
 * Provide the host without a protocol. It will always be assumed to be https <br/>
 * <br/>
 * <h2>Using a policy information point</h2> If your EmbeddedPolicyDecisionPoint shall use
 * one or more PolicyInformationPoints, you can achieve this by ... ... instances you want
 * to use as PolicyInformationPoints need to implement the
 * {@link PolicyInformationPoint}-interface. <br/>
 * <br/>
 * <h2>The PolicyEnforcementFilter</h2> If activated through the following property, a
 * bean of type {@link PolicyEnforcementFilterPEP} will be defined. You can use it to
 * extends the spring-security filterchain. See
 * {@link #policyEnforcementFilter(PolicyDecisionPoint, ConstraintHandlerService, ObjectMapper)}<br/>
 * <code>
 * io.sapl.policyEnforcementFilter=true
 * </code> <br/>
 *
 * @see SAPLProperties
 */
@Slf4j
@Configuration
@ComponentScan("io.sapl.spring")
@EnableConfigurationProperties(SAPLProperties.class)
@AutoConfigureAfter({ FunctionLibrariesAutoConfiguration.class,
		PolicyInformationPointsAutoConfiguration.class })
public class PDPAutoConfiguration {

	private final SAPLProperties pdpProperties;

	private final Collection<Object> policyInformationPoints;

	private final Collection<Object> functionLibraries;

	public PDPAutoConfiguration(SAPLProperties pdpProperties,
			ConfigurableApplicationContext applicationContext) {
		this.pdpProperties = pdpProperties;
		policyInformationPoints = applicationContext
				.getBeansWithAnnotation(PolicyInformationPoint.class).values();
		functionLibraries = applicationContext
				.getBeansWithAnnotation(FunctionLibrary.class).values();
	}

	@Bean
	@ConditionalOnMissingBean
	public PolicyDecisionPoint policyDecisionPoint()
			throws AttributeException, FunctionException, IOException, URISyntaxException,
			PDPConfigurationException, PolicyEvaluationException {
		if (pdpProperties.getPdpType() == SAPLProperties.PDPType.REMOTE) {
			return remotePolicyDecisionPoint();
		}
		return embeddedPolicyDecisionPoint();
	}

	private PolicyDecisionPoint embeddedPolicyDecisionPoint()
			throws AttributeException, FunctionException, IOException, URISyntaxException,
			PDPConfigurationException, PolicyEvaluationException {
		final EmbeddedPolicyDecisionPoint.Builder builder = EmbeddedPolicyDecisionPoint
				.builder();
		if (pdpProperties.getPdpConfigType() == SAPLProperties.PDPConfigType.FILESYSTEM) {
			final String configPath = pdpProperties.getFilesystem().getConfigPath();
			LOGGER.info("using monitored config file from the filesystem: {}",
					configPath);
			builder.withFilesystemPDPConfigurationProvider(configPath);
		}
		else {
			final String configPath = pdpProperties.getResources().getConfigPath();
			LOGGER.info("using config file from bundled resource at: {}", configPath);
			builder.withResourcePDPConfigurationProvider(configPath);
		}

		final Builder.IndexType indexType = getIndexType();
		if (pdpProperties.getPrpType() == SAPLProperties.PRPType.FILESYSTEM) {
			final String policiesPath = pdpProperties.getFilesystem().getPoliciesPath();
			LOGGER.info(
					"creating embedded PDP with {} index sourcing and monitoring access policies from the filesystem: {}",
					indexType, policiesPath);
			builder.withFilesystemPolicyRetrievalPoint(policiesPath, indexType);
		}
		else {
			final String policiesPath = pdpProperties.getResources().getPoliciesPath();
			LOGGER.info(
					"creating embedded PDP with {} index sourcing access policies from bundled resources at: {}",
					indexType, policiesPath);
			builder.withResourcePolicyRetrievalPoint(policiesPath, indexType);
		}

		return bindComponentsToPDP(builder).build();
	}

	private Builder.IndexType getIndexType() {
		if (pdpProperties.getIndex() == SAPLProperties.PRPIndexType.FAST) {
			return Builder.IndexType.FAST;
		}
		return Builder.IndexType.SIMPLE;
	}

	private Builder bindComponentsToPDP(Builder builder) throws AttributeException {
		for (Object entry : policyInformationPoints) {
			LOGGER.debug("binding PIP to PDP: {}", entry.getClass().getSimpleName());
			try {
				builder.withPolicyInformationPoint(entry);
			}
			catch (SecurityException | IllegalArgumentException | AttributeException e) {
				throw new AttributeException(e);
			}
		}
		for (Object entry : functionLibraries) {
			LOGGER.debug("binding FunctionLibrary to PDP: {}",
					entry.getClass().getSimpleName());
			try {
				builder.withFunctionLibrary(entry);
			}
			catch (SecurityException | IllegalArgumentException | FunctionException e) {
				throw new AttributeException(e);
			}
		}

		return builder;
	}

	private PolicyDecisionPoint remotePolicyDecisionPoint() {
		Remote remoteProps = pdpProperties.getRemote();
		String host = remoteProps.getHost();
		int port = remoteProps.getPort();
		String key = remoteProps.getKey();
		String secret = remoteProps.getSecret();
		return new RemotePolicyDecisionPoint(host, port, key, secret);
	}

	@Bean
	@ConditionalOnProperty("io.sapl.policyEnforcementFilter")
	public PolicyEnforcementFilterPEP policyEnforcementFilter(PolicyDecisionPoint pdp,
			ConstraintHandlerService constraintHandlers, ObjectMapper mapper) {
		LOGGER.debug(
				"no Bean of type PolicyEnforcementFilter defined. Will create default Bean of {}",
				PolicyEnforcementFilterPEP.class);
		return new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
	}

}
