package io.sapl.springboot.starter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import io.sapl.spring.PIPProvider;
import io.sapl.spring.PolicyEnforcementFilter;
import io.sapl.spring.SAPLAuthorizator;
import io.sapl.spring.SAPLPermissionEvaluator;
import io.sapl.spring.marshall.advice.AdviceHandlerService;
import io.sapl.spring.marshall.advice.SimpleAdviceHandlerService;
import io.sapl.spring.marshall.mapper.SaplMapper;
import io.sapl.spring.marshall.mapper.SimpleSaplMapper;
import io.sapl.spring.marshall.obligation.Obligation;
import io.sapl.spring.marshall.obligation.ObligationHandler;
import io.sapl.spring.marshall.obligation.ObligationHandlerService;
import io.sapl.spring.marshall.obligation.SimpleObligationHandlerService;
import io.sapl.springboot.starter.PDPProperties.Remote;
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
 * Do not forget to provide the minimal required files in your policy path! (at
 * least you need a <i>pdp.json</i> file) <br/>
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
 * {@link SAPLAuthorizator} on your own this will be done for you. The
 * SAPLAuthorizator-instance created by
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
 * {@link #policyEnforcementFilter(SAPLAuthorizator)} <br/>
 * <code>
 * pdp.policyEnforcementFilter=true
 * </code> <br/>
 * 
 * <br/>
 * <br/>
 * 
 * @see PDPProperties
 * @see SAPLAuthorizator
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(PDPProperties.class)
public class PDPAutoConfiguration {

	public static final String BEAN_NAME_PDP_REMOTE = "pdpRemote";

	public static final String BEAN_NAME_PDP_EMBEDDED = "pdpEmbedded";

	private static final String BEAN_NAME_OBLIGATION_HANDLER_DENY_ALL = "denyAllObligationHandler";

	@Autowired
	private PDPProperties pdpProperties;

	@Bean
	@ConditionalOnProperty(name = "pdp.type", havingValue = "EMBEDDED")
	public PolicyDecisionPoint pdpEmbedded(PIPProvider pipProvider)
			throws PolicyEvaluationException, AttributeException, FunctionException, IOException {
		log.debug("creating embedded PDP with Bean name {} and policy path {}", BEAN_NAME_PDP_EMBEDDED,
				pdpProperties.getEmbedded().getPolicyPath());

		EmbeddedPolicyDecisionPoint pdp = new EmbeddedPolicyDecisionPoint(pdpProperties.getEmbedded().getPolicyPath());
		log.debug("PIP-Provider has {} entries.", pipProvider.getPIPClasses().size());
		for (Class<?> clazz : pipProvider.getPIPClasses()) {
			log.debug("importAttributeFindersFromPackage: {}", clazz.getPackage().getName());
			pdp.importAttributeFindersFromPackage(clazz.getPackage().getName());
		}
		return pdp;
	}

	@Bean
	@ConditionalOnProperty(name = "pdp.type", havingValue = "REMOTE")
	public PolicyDecisionPoint pdpRemote() {
		Remote remoteProps = pdpProperties.getRemote();
		String host = remoteProps.getHost();
		int port = remoteProps.getPort();
		String key = remoteProps.getKey();
		String secret = remoteProps.getSecret();
		log.debug("creating remote PDP with Bean name {} and properties: \nhost {} \nport {} \nkey {} \nsecret {}",
				BEAN_NAME_PDP_REMOTE, host, port, key, "*******");
		return new RemotePolicyDecisionPoint(host, port, key, secret);
	}

	/**
	 * If no bean-definition of type {@link PIPProvider} exists, this method
	 * will define a PIPProvide-bean that allways delivers an enmpty list.
	 * 
	 * @return a PIPProvider
	 * @see PIPProvider
	 */
	@Bean
	@ConditionalOnMissingBean
	public PIPProvider processInformationPoints() {
		return Collections::emptyList;
	}

	/**
	 * 
	 * @param saplAuthorizer
	 *            - the SAPLAuthorizator to be used by the Filter
	 * @return a PolicyEnforcementFilter
	 */
	@Bean
	@ConditionalOnProperty("pdp.policyEnforcementFilter")
	public PolicyEnforcementFilter policyEnforcementFilter(SAPLAuthorizator saplAuthorizer) {
		log.debug("no Bean of type PolicyEnforcementFilter defined. Will create default Bean of {}",
				PolicyEnforcementFilter.class);
		return new PolicyEnforcementFilter(saplAuthorizer);
	}

	/**
	 * 
	 * @param pdp
	 *            - a PolicyDecisionPoint instance
	 * @param ohs
	 *            - a ObligationHandlerService instance
	 * @param ahs
	 *            - a AdviceHandlerService instance
	 * @param sm
	 *            - a SaplMapper instance
	 * @return a SAPLAuthorizator instance
	 */
	@Bean
	@ConditionalOnMissingBean
	public SAPLAuthorizator createSAPLAuthorizer(PolicyDecisionPoint pdp, ObligationHandlerService ohs,
			AdviceHandlerService ahs, SaplMapper sm) {
		log.debug("no Bean of type SAPLAuthorizator  defined. Will create default Bean of {}", SAPLAuthorizator.class);
		return new SAPLAuthorizator(pdp, ohs, ahs, sm);
	}

	/**
	 * 
	 * @param saplAuthorizer
	 *            - the SAPLAuthorizator to be used
	 * @return a SAPLPermissionEvaluator
	 */
	@Bean
	@ConditionalOnMissingBean
	public SAPLPermissionEvaluator createSAPLPermissionEvaluator(SAPLAuthorizator saplAuthorizer) {
		log.debug("no Bean of type SAPLPermissionEvaluator defined. Will create default Bean of {}",
				SAPLPermissionEvaluator.class);
		return new SAPLPermissionEvaluator(saplAuthorizer);
	}

	/**
	 * If no other bean-definition of type {@link SaplMapper} is provided, then
	 * this will provide a {@link SimpleSaplMapper}
	 * 
	 * @return an implementation of SaplMapper
	 */
	@Bean
	@ConditionalOnMissingBean
	public SaplMapper createSaplMapper() {
		log.debug("no Bean of type SaplMapper defined. Will create default Bean of {}", SimpleSaplMapper.class);
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
		log.debug("no Bean of type ObligationHandlerService defined. Will create default Bean of {}",
				SimpleObligationHandlerService.class);
		return new SimpleObligationHandlerService();
	}

	/**
	 * If no other bean-definition of type {@link AdviceHandlerService} is
	 * provided, then this will provide a {@link SimpleAdviceHandlerService}
	 * 
	 * @return an AdviceHandlerService
	 */
	@Bean
	@ConditionalOnMissingBean
	public AdviceHandlerService createDefaultAdviceHandlerService() {
		log.debug("no Bean of type AdviceHandlerService defined. Will create default Bean of {}",
				SimpleAdviceHandlerService.class);
		return new SimpleAdviceHandlerService();
	}

	/**
	 * If property <code>pdp.obligationsHandler.autoregister</code> is set to
	 * <b>true</b> this method will register all beans of type
	 * {@link ObligationHandler} to the Bean of type
	 * {@link ObligationHandlerService}
	 * 
	 * @param obligationHandlers
	 *            - List of all ObligationHandler-beans
	 * @param ohs
	 *            - ObligationHandlerService to register the handler with
	 * @return a CommandLineRunner object
	 * @see ObligationHandlerService#register(ObligationHandler)
	 * @see #createDefaultObligationHandlerService()
	 */
	@Bean
	public CommandLineRunner registerObligationHandlers(List<ObligationHandler> obligationHandlers,
			ObligationHandlerService ohs) {
		if (!pdpProperties.getObligationsHandler().isAutoregister()) {
			log.debug("Automatic registration of obligation handlers is deactivated.");
			return args -> {
				// NOP
			};
		}
		log.debug(
				"Automatic registration of obligation handlers is activated. {} beans of type ObligationHandler found, they will be reigistered at the ObligationHandlerService-bean",
				obligationHandlers.size());
		return args -> obligationHandlers.stream().forEach(ohs::register);

	}

	/**
	 * Returns an implementation of {@link ObligationHandler} does nothing but
	 * logg a message on logg-level warn, when
	 * {@link ObligationHandler#handleObligation(Obligation)} is called and
	 * allways returns <b>false</b> when
	 * {@link ObligationHandler#canHandle(Obligation)} ia called.
	 * 
	 * @return an ObligationHandler implementation
	 */
	@Bean(BEAN_NAME_OBLIGATION_HANDLER_DENY_ALL)
	@ConditionalOnMissingBean
	public ObligationHandler denyAllObligationHandler() {
		return new ObligationHandler() {

			@Override
			public void handleObligation(Obligation obligation) {
				log.warn(
						"using denyAllObligationHandler. If you want to handle Obligations register your own und probably unregister this one (Bean name: {})",
						BEAN_NAME_OBLIGATION_HANDLER_DENY_ALL);
			}

			@Override
			public boolean canHandle(Obligation obligation) {
				return false;
			}
		};
	}

}
