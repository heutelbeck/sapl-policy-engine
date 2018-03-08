package io.sapl.spring;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Response;
import io.sapl.spring.marshall.advice.Advice;
import io.sapl.spring.marshall.advice.AdviceHandlerService;
import io.sapl.spring.marshall.mapper.SaplMapper;
import io.sapl.spring.marshall.mapper.SaplRequestType;
import io.sapl.spring.marshall.obligation.Obligation;
import io.sapl.spring.marshall.obligation.ObligationFailed;
import io.sapl.spring.marshall.obligation.ObligationHandlerService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SAPLAuthorizator {

	protected final PolicyDecisionPoint pdp;

	protected final ObligationHandlerService obs;
	
	protected final AdviceHandlerService ahs;
	
	protected final SaplMapper sm;

	@Autowired
	public SAPLAuthorizator(PolicyDecisionPoint pdp, ObligationHandlerService obs, AdviceHandlerService ahs, SaplMapper sm) {
		this.pdp = pdp;
		this.obs = obs;
		this.ahs = ahs;
		this.sm = sm;
	}

	
	public boolean authorize(Object subject, Object action, Object resource) {
		return authorize(subject, action, resource, Optional.empty());
	}
	
	public Response getResponse(Object subject, Object action, Object resource) {
		return getResponse(subject, action, resource, Optional.empty());
	}
	
	
	public boolean authorize(Object subject, Object action, Object resource, Object environment) {
		LOGGER.trace("Entering hasPermission(Subject subject, Action action, Resource resource)...");
		Response response = runPolicyCheck(subject, action, resource, environment);
		LOGGER.debug("Response decision ist: {}", response.getDecision());
		return response.getDecision() == Decision.PERMIT;
	}

	public Response getResponse(Object subject, Object action, Object resource, Object environment) {
		LOGGER.trace("Entering getResponse...");
		Response response = runPolicyCheck(subject, action, resource, environment);
		return response;
	}

	protected Response runPolicyCheck(Object subject, Object action, Object resource, Object environment) {
		LOGGER.trace("Entering runPolicyCheck...");
		Object mappedSubject = sm.map(subject, SaplRequestType.SUBJECT);
		Object mappedAction = sm.map(action, SaplRequestType.ACTION);
		Object mappedResource = sm.map(resource, SaplRequestType.RESOURCE);
		Object mappedEnvironment = sm.map(environment, SaplRequestType.ENVIRONMENT);

		LOGGER.debug("These are the parameters: \n  subject:{} \n  action:{} \n  resource:{} \n environment:{}", mappedSubject, mappedAction,
				mappedResource, mappedEnvironment);

		Response response = pdp.decide(mappedSubject, mappedAction, mappedResource, mappedEnvironment);

		LOGGER.debug("Here comes the response: {}", response);
		
		if (response.getObligation().orElse(null) != null) {
			List<Obligation> obligationsList = Obligation.fromJson(response.getObligation().get());
			
			LOGGER.debug("Start handling obligations {}", obligationsList);
			try {
				for (Obligation o : obligationsList) {
					LOGGER.debug("Handling now {}", o);
					obs.handle(o);
				}
			} catch (ObligationFailed e) {
				response = new Response(Decision.DENY, Optional.empty(), Optional.empty(), Optional.empty());
			}
		}
		
		if (response.getAdvice().orElse(null) != null) {
			List<Advice> adviceList = Advice.fromJson(response.getAdvice().get());
			
			LOGGER.debug("Start handling advices {}", adviceList);
			for (Advice a : adviceList) {
				LOGGER.debug("Handling now {}", a);
				ahs.handle(a);
			}

		}

		return response;
	}

}
