package io.sapl.spring;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Response;
import io.sapl.spring.marshall.Action;
import io.sapl.spring.marshall.Resource;
import io.sapl.spring.marshall.Subject;
import io.sapl.spring.marshall.advice.Advice;
import io.sapl.spring.marshall.advice.AdviceHandlerService;
import io.sapl.spring.marshall.mapper.SaplMapper;
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

	public boolean authorize(Subject subject, Action action, Resource resource) {
		LOGGER.trace("Entering hasPermission(Subject subject, Action action, Resource resource)...");
		Response response = runPolicyCheck(subject.getAsJson(), action.getAsJson(), resource.getAsJson());
		LOGGER.debug("Response decision ist: {}", response.getDecision());
		return response.getDecision() == Decision.PERMIT;
	}

	public Response getResponse(Subject subject, Action action, Resource resource) {
		LOGGER.trace("Entering getResponse...");
		Response response = runPolicyCheck(subject.getAsJson(), action.getAsJson(), resource.getAsJson());
		return response;
	}

	protected Response runPolicyCheck(Object subject, Object action, Object resource) {
		LOGGER.trace("Entering runPolicyCheck...");
		Object mappedSubject = sm.map(subject);
		Object mappedAction = sm.map(action);
		Object mappedResource = sm.map(resource);

		LOGGER.debug("These are the parameters: \n  subject:{} \n  action:{} \n  resource:{}", mappedSubject, mappedAction,
				mappedResource);

		Response response = pdp.decide(mappedSubject, mappedAction, mappedResource);

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
				response = new Response(Decision.DENY, null, null, null);
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
