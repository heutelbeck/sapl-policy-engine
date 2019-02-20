package io.sapl.springboot.autoconfig;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint.Builder;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import io.sapl.spring.ConstraintHandlerService;
import io.sapl.spring.PolicyEnforcementFilterPEP;
import io.sapl.springboot.autoconfig.PDPProperties.Remote;
import lombok.extern.slf4j.Slf4j;

/**
 * This automatic configuration will provide you several beans to deal with SAPL
 * by default. <br/>
 * If you do not change it, the default configuration (see
 * {@link PDPProperties}) will configure an {@link EmbeddedPolicyDecisionPoint}
 * for you. <br/>
 * <br/>
 * <h2>Configure an EmbeddedPolicyDecisionPoint</h2> To have a bean instance of
 * an {@link EmbeddedPolicyDecisionPoint} just activate it in your
 * <i>application.properties</i>-file (or whatever spring supported way to
 * provide properties you wish to use. c. f. <a href=
 * "https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html">Spring
 * Boot Documentation on config parameters</a>) <br/>
 * Do not forget to provide the minimal required files in your policy path!
 * Example Snippet from .properties:<br/>
 * <code>
 * pdp.type=embedded
 * <br/>
 * pdp.embedded.policyPath=classpath:path/to/policies
 * </code> <br/>
 *
 *
 * <h2>Configure a RemotePolicyDecisionPoint</h2> To have a bean instance of a
 * {@link RemotePolicyDecisionPoint} just activate it in your
 * <i>application.properties</i>-file (or whatever spring supported way to
 * provide properties you wish to use. <br/>
 * c. f. <a href=
 * "https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html">Spring
 * Boot Documentation on config parameters</a>) <br/>
 * Example Snippet from .properties:<br/>
 * <code>
 * pdp.type=remote<br/>
 * pdp.remote.host=myhost.example.io<br/>
 * pdp.remote.port=8443<br/>
 * pdp.remote.secret=password<br/>
 * pdp.remote.key=username
 * </code> <br/>
 * Provide the host without a protocol. It will always be assumed to be
 * https<br/>
 * <br/>
 * <br/>
 * <h2>Using a policy information point</h2> If your EmbeddedPolicyDecisionPoint
 * shall use one or more PolicyInformationPoints, you can achieve this by
 * providing a bean of type {@link PIPProvider}. This bean must provide a
 * collection of all classes that instances you want to use as
 * PolicyInformationPoints. They need to implement the
 * {@link PolicyInformationPoint}-interface.<br/>
 * <br/>
 * If you do not define a bean of this type, this starter will provide a simple
 * implementation of {@link PIPProvider} that always returns an empty list.
 * 
 * <br/>
 * <br/>
 * <h2>The SaplAuthorizer</h2> If you do not define a Bean of type
 * {@link AdviceHandlerService} on your own this will be done for you. The
 * SAPLAuthorizer-instance created by
 * {@link #createSAPLAuthorizer(PolicyDecisionPoint, ObligationHandlerService, AdviceHandlerService, SaplMapper)}
 * will use beans of the following types. All of these are provided with a
 * default implementation if and only if you do not define beans of the types on
 * your own. <br/>
 * <ul>
 * <li>{@link PolicyDecisionPoint}: the embbeded- or remote-PDP you choosed to
 * use (see above)</li>
 * <li>{@link ObligationHandlerService}: see
 * {@link #createDefaultObligationHandlerService()}</li>
 * <li>{@link AdviceHandlerService}: see
 * {@link #createDefaultAdviceHandlerService()}</li>
 * <li>{@link SaplMapper}: see {@link #createSaplMapper()}</li>
 * </ul>
 * <br/>
 * <br/>
 * <h2>Registration of ObligationHandlers</h2> At startup all beans of type
 * {@link ObligationHandler} will be registered with your
 * {@link ObligationHandlerService}. See
 * {@link #registerObligationHandlers(List, ObligationHandlerService)} <br/>
 * This behavior can not be overridden. But you can make this method do nothing
 * with the following property set to false. That way you can handle the
 * ObligationHandler-registration completely on your own. <br/>
 * <code>pdp.obligationsHandler.autoregister=false</code> <br/>
 * <br/>
 * <h2>The default ObligationHandler</h2> If and only if you do not provide any
 * bean-definition of type {@link ObligationHandler} one bean will be defined by
 * {@link #denyAllObligationHandler()}. <br/>
 * <br/>
 * <h2>The PolicyEnforcementFilter</h2> If activated through the following
 * property, a bean of type {@link PolicyEnforcementFilterPEP} will be defined.
 * You can use it to extends the spring-security filterchain. See
 * {@link #policyEnforcementFilter(AdviceHandlerService)} <br/>
 * <code>
 * pdp.policyEnforcementFilter=true
 * </code> <br/>
 * 
 * <br/>
 * <br/>
 * 
 * @see PDPProperties
 * @see AdviceHandlerService
 */
@Slf4j
@Configuration
@ComponentScan("io.sapl.spring")
@EnableConfigurationProperties(PDPProperties.class)
@AutoConfigureAfter({ FunctionLibrariesAutoConfiguration.class, PolicyInformationPointsAutoConfiguration.class })
public class PDPAutoConfiguration {

	private final PDPProperties pdpProperties;
	private final Map<String, Object> policyInformationPoints;
	private final Map<String, Object> functionLibraries;

	public PDPAutoConfiguration(PDPProperties pdpProperties, ConfigurableApplicationContext applicationContext,
			ObjectMapper mapper) {
		this.pdpProperties = pdpProperties;
		policyInformationPoints = applicationContext.getBeansWithAnnotation(PolicyInformationPoint.class);
		functionLibraries = applicationContext.getBeansWithAnnotation(FunctionLibrary.class);
	}

	@Bean
	@ConditionalOnMissingBean
	public PolicyDecisionPoint policyDecisionPoint()
			throws AttributeException, FunctionException, IOException, URISyntaxException, PolicyEvaluationException {
		switch (pdpProperties.getType()) {
		case FILESYSTEM:
			return filesystemPolicyDecisionPoint();
		case REMOTE:
			return remotePolicyDecisionPoint();
		default:
			return resourcesPolicyDecisionPoint();
		}

	}

	@Service
	public static class PDPDestroyer {
		@Autowired
		Optional<EmbeddedPolicyDecisionPoint> pdp;

		@PreDestroy
		public void disposePdp() {
			pdp.ifPresent(EmbeddedPolicyDecisionPoint::dispose);
		}
	}

	private PolicyDecisionPoint resourcesPolicyDecisionPoint()
			throws AttributeException, FunctionException, IOException, URISyntaxException, PolicyEvaluationException {
		LOGGER.info("creating embedded PDP sourcing access policies from bundled resources at: {}",
				pdpProperties.getResources().getPoliciesPath());
		EmbeddedPolicyDecisionPoint.Builder builder = EmbeddedPolicyDecisionPoint.builder()
				.withResourcePolicyRetrievalPoint(pdpProperties.getResources().getPoliciesPath());

		return bindComponentsToPDP(builder).build();
	}

	private PolicyDecisionPoint filesystemPolicyDecisionPoint()
			throws AttributeException, FunctionException, IOException, URISyntaxException, PolicyEvaluationException {
		LOGGER.info("creating embedded PDP sourcing and monitoring access policies from the filesystem: {}",
				pdpProperties.getFilesystem().getPoliciesPath());

		EmbeddedPolicyDecisionPoint.Builder builder = EmbeddedPolicyDecisionPoint.builder()
				.withFilesystemPolicyRetrievalPoint(pdpProperties.getFilesystem().getPoliciesPath());

		return bindComponentsToPDP(builder).build();
	}

	private Builder bindComponentsToPDP(Builder builder) throws AttributeException {
		for (Entry<String, Object> entry : policyInformationPoints.entrySet()) {
			LOGGER.debug("binding PIP to PDP: {}", entry.getKey());
			try {
				builder.withPolicyInformationPoint(entry.getValue());
			} catch (SecurityException | IllegalArgumentException | AttributeException e) {
				throw new AttributeException(e);
			}
		}
		for (Entry<String, Object> entry : functionLibraries.entrySet()) {
			LOGGER.debug("binding FunctionLibrary to PDP: {}", entry.getKey());
			try {
				builder.withFunctionLibrary(entry.getValue());
			} catch (SecurityException | IllegalArgumentException | FunctionException e) {
				throw new AttributeException(e);
			}
		}

		return builder;
	}

	private PolicyDecisionPoint remotePolicyDecisionPoint() {
		Remote remoteProps = pdpProperties.getRemote();
		String host = remoteProps.getHost();
		int port = remoteProps.getPort();
		return new RemotePolicyDecisionPoint(host, port);
	}

	/**
	 * 
	 * @param saplAuthorizer - the SAPLAuthorizer to be used by the Filter
	 * @return a PolicyEnforcementFilter
	 */
	@Bean
	@ConditionalOnProperty("pdp.policyEnforcementFilter")
	public PolicyEnforcementFilterPEP policyEnforcementFilter(PolicyDecisionPoint pdp,
			ConstraintHandlerService constraintHandlers, ObjectMapper mapper) {
		LOGGER.debug("no Bean of type PolicyEnforcementFilter defined. Will create default Bean of {}",
				PolicyEnforcementFilterPEP.class);
		return new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
	}

}
