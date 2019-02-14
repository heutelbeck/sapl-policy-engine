package io.sapl.springboot.autoconfig;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.advice.AdviceHandlerService;
import io.sapl.api.pdp.mapping.SaplMapper;
import io.sapl.api.pdp.obligation.Obligation;
import io.sapl.api.pdp.obligation.ObligationHandler;
import io.sapl.api.pdp.obligation.ObligationHandlerService;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint.Builder;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import io.sapl.pep.SAPLAuthorizer;
import io.sapl.pep.pdp.advice.SimpleAdviceHandlerService;
import io.sapl.pep.pdp.mapping.SimpleSaplMapper;
import io.sapl.pep.pdp.obligation.SimpleObligationHandlerService;
import io.sapl.spring.PolicyEnforcementFilter;
import io.sapl.spring.SAPLPermissionEvaluator;
import io.sapl.spring.annotation.PdpAuthorizeAspect;
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
 * {@link SAPLAuthorizer} on your own this will be done for you. The
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
 * property, a bean of type {@link PolicyEnforcementFilter} will be defined. You
 * can use it to extends the spring-security filterchain. See
 * {@link #policyEnforcementFilter(SAPLAuthorizer)} <br/>
 * <code>
 * pdp.policyEnforcementFilter=true
 * </code> <br/>
 * 
 * <br/>
 * <br/>
 * 
 * @see PDPProperties
 * @see SAPLAuthorizer
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(PDPProperties.class)
@AutoConfigureAfter({ FunctionLibrariesAutoConfiguration.class, PolicyInformationPointsAutoConfiguration.class })
public class PDPAutoConfiguration {

	private static final String BEAN_NAME_OBLIGATION_HANDLER_DENY_ALL = "denyAllObligationHandler";

	private final PDPProperties pdpProperties;
	private final Map<String, Object> policyInformationPoints;
	private final Map<String, Object> functionLibraries;

	public PDPAutoConfiguration(PDPProperties pdpProperties, ConfigurableApplicationContext applicationContext) {
		this.pdpProperties = pdpProperties;
		policyInformationPoints = applicationContext.getBeansWithAnnotation(PolicyInformationPoint.class);
		functionLibraries = applicationContext.getBeansWithAnnotation(FunctionLibrary.class);
	}

	@Bean
	@ConditionalOnProperty(name = "pdp.type", havingValue = "RESOURCES")
	public PolicyDecisionPoint pdpResources()
			throws AttributeException, FunctionException, IOException, URISyntaxException, PolicyEvaluationException {
		LOGGER.info("creating embedded PDP sourcing access policies from bundled resources at: {}",
				pdpProperties.getResources().getPoliciesPath());
		LOGGER.info("props: {}", pdpProperties);
		EmbeddedPolicyDecisionPoint.Builder builder = EmbeddedPolicyDecisionPoint.builder()
				.withResourcePolicyRetrievalPoint(pdpProperties.getResources().getPoliciesPath());

		return bindComponentsToPDP(builder).build();
	}

	@Bean
	@ConditionalOnProperty(name = "pdp.type", havingValue = "FILESYSTEM")
	public PolicyDecisionPoint pdpFilesystem()
			throws AttributeException, FunctionException, IOException, URISyntaxException, PolicyEvaluationException {
		LOGGER.info("creating embedded PDP sourcing and monitoring access policies from the filesystem: {}",
				pdpProperties.getFilesystem().getPoliciesPath());

		EmbeddedPolicyDecisionPoint.Builder builder = EmbeddedPolicyDecisionPoint.builder()
				.withFilesystemPolicyRetrievalPoint(pdpProperties.getResources().getPoliciesPath());

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

	@Bean
	@ConditionalOnProperty(name = "pdp.type", havingValue = "REMOTE")
	public PolicyDecisionPoint pdpRemote() {
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
	public PolicyEnforcementFilter policyEnforcementFilter(SAPLAuthorizer saplAuthorizer) {
		LOGGER.debug("no Bean of type PolicyEnforcementFilter defined. Will create default Bean of {}",
				PolicyEnforcementFilter.class);
		return new PolicyEnforcementFilter(saplAuthorizer);
	}

	/**
	 * 
	 * @param pdp - a PolicyDecisionPoint instance
	 * @param ohs - an ObligationHandlerService instance
	 * @param ahs - an AdviceHandlerService instance
	 * @param sm  - a SaplMapper instance
	 * @return a SAPLAuthorizer instance
	 */
	@Bean
	@ConditionalOnMissingBean
	public SAPLAuthorizer createSAPLAuthorizer(PolicyDecisionPoint pdp, ObligationHandlerService ohs,
			AdviceHandlerService ahs, SaplMapper sm) {
		LOGGER.debug("no Bean of type SAPLAuthorizer  defined. Will create default Bean of {}", SAPLAuthorizer.class);
		return new SAPLAuthorizer(pdp, ohs, ahs, sm);
	}

	/**
	 * 
	 * @param saplAuthorizer - the SAPLAuthorizer to be used
	 * @return a SAPLPermissionEvaluator
	 */
	@Bean
	@ConditionalOnMissingBean
	public SAPLPermissionEvaluator createSAPLPermissionEvaluator(SAPLAuthorizer saplAuthorizer) {
		LOGGER.debug("no Bean of type SAPLPermissionEvaluator defined. Will create default Bean of {}",
				SAPLPermissionEvaluator.class);
		return new SAPLPermissionEvaluator(saplAuthorizer);
	}

	/**
	 * If no other bean-definition of type {@link SaplMapper} is provided, then this
	 * will provide a {@link SimpleSaplMapper}
	 * 
	 * @return an implementation of SaplMapper
	 */
	@Bean
	@ConditionalOnMissingBean
	public SaplMapper createSaplMapper() {
		LOGGER.debug("no Bean of type SaplMapper defined. Will create default Bean of {}", SimpleSaplMapper.class);
		return new SimpleSaplMapper();
	}

	/**
	 * If no other bean-definition of type {@link ObligationHandlerService} is
	 * provided, then this will provide a {@link SimpleObligationHandlerService}
	 * 
	 * @return an ObligationHandlerService implementation
	 */
	@Bean
	@ConditionalOnMissingBean
	public ObligationHandlerService createDefaultObligationHandlerService() {
		LOGGER.debug("no Bean of type ObligationHandlerService defined. Will create default Bean of {}",
				SimpleObligationHandlerService.class);
		return new SimpleObligationHandlerService();
	}

	/**
	 * If no other bean-definition of type {@link AdviceHandlerService} is provided,
	 * then this will provide a {@link SimpleAdviceHandlerService}
	 * 
	 * @return an AdviceHandlerService
	 */
	@Bean
	@ConditionalOnMissingBean
	public AdviceHandlerService createDefaultAdviceHandlerService() {
		LOGGER.debug("no Bean of type AdviceHandlerService defined. Will create default Bean of {}",
				SimpleAdviceHandlerService.class);
		return new SimpleAdviceHandlerService();
	}

	/**
	 * If property <code>pdp.obligationsHandler.autoregister</code> is set to
	 * <b>true</b> this method will register all beans of type
	 * {@link ObligationHandler} to the Bean of type
	 * {@link ObligationHandlerService}
	 * 
	 * @param obligationHandlers - List of all ObligationHandler-beans
	 * @param ohs                - ObligationHandlerService to register the handler
	 *                           with
	 * @return a CommandLineRunner object
	 * @see ObligationHandlerService#register(ObligationHandler)
	 * @see #createDefaultObligationHandlerService()
	 */
	@Bean
	public CommandLineRunner registerObligationHandlers(List<ObligationHandler> obligationHandlers,
			ObligationHandlerService ohs) {
		if (!pdpProperties.getObligationsHandler().isAutoregister()) {
			LOGGER.debug("Automatic registration of obligation handlers is deactivated.");
			return args -> {
				// NOP
			};
		}
		LOGGER.debug(
				"Automatic registration of obligation handlers is activated. {} beans of type ObligationHandler found, they will be reigistered at the ObligationHandlerService-bean",
				obligationHandlers.size());
		return args -> obligationHandlers.stream().forEach(ohs::register);

	}

	/**
	 * Returns an implementation of {@link ObligationHandler} does nothing but logg
	 * a message on logg-level warn, when
	 * {@link ObligationHandler#handleObligation(Obligation)} is called and allways
	 * returns <b>false</b> when {@link ObligationHandler#canHandle(Obligation)} ia
	 * called.
	 * 
	 * @return an ObligationHandler implementation
	 */
	@Bean(BEAN_NAME_OBLIGATION_HANDLER_DENY_ALL)
	@ConditionalOnMissingBean
	public ObligationHandler denyAllObligationHandler() {
		return new ObligationHandler() {

			@Override
			public void handleObligation(Obligation obligation) {
				LOGGER.warn(
						"using denyAllObligationHandler. If you want to handle Obligations register your own und probably unregister this one (Bean name: {})",
						BEAN_NAME_OBLIGATION_HANDLER_DENY_ALL);
			}

			@Override
			public boolean canHandle(Obligation obligation) {
				return false;
			}
		};
	}

	@Bean
	PdpAuthorizeAspect pdpAuthorizeAspect(SAPLAuthorizer sapl) {
		return new PdpAuthorizeAspect(sapl);
	}
}
