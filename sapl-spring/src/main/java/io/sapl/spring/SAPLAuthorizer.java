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
import io.sapl.spring.marshall.mapper.SaplRequestElement;
import io.sapl.spring.marshall.obligation.Obligation;
import io.sapl.spring.marshall.obligation.ObligationFailed;
import io.sapl.spring.marshall.obligation.ObligationHandlerService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SAPLAuthorizer {

	protected final PolicyDecisionPoint pdp;

	protected final ObligationHandlerService obs;

	protected final AdviceHandlerService ahs;

	protected final SaplMapper sm;

	@Autowired
	public SAPLAuthorizer(PolicyDecisionPoint pdp, ObligationHandlerService obs, AdviceHandlerService ahs,
						  SaplMapper sm) {
		this.pdp = pdp;
		this.obs = obs;
		this.ahs = ahs;
		this.sm = sm;
	}

	/**
	 * Convenience method calling
	 * {@link #wouldAuthorize(Object, Object, Object, Object) wouldAuthorize(subject, action, resource, null)}.
	 *
	 * @param subject
	 * 			an object representing the subject of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param action
	 * 			an object representing the action of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param resource
	 * 			an object representing the resource of the authorization request. May be mapped to an instance
	 * 	        of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 * 	        policy decision point.
	 * @return {@code true} if the authorization decision is {@link Decision#PERMIT}, {@code false} otherwise.
	 */
	public boolean wouldAuthorize(Object subject, Object action, Object resource) {
		return wouldAuthorize(subject, action, resource, null);
	}

	/**
	 * Convenience method calling
	 * {@link #authorize(Object, Object, Object, Object) authorize(subject, action, resource, null)}.
	 *
	 * @param subject
	 * 			an object representing the subject of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param action
	 * 			an object representing the action of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param resource
	 * 			an object representing the resource of the authorization request. May be mapped to an instance
	 * 	        of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 * 	        policy decision point.
	 * @return {@code true} if the final authorization decision is {@link Decision#PERMIT}, {@code false} otherwise.
	 */
	public boolean authorize(Object subject, Object action, Object resource) {
		return authorize(subject, action, resource, null);
	}

	/**
	 * Convenience method calling
	 * {@link #getResponse(Object, Object, Object, Object) getResponse(subject, action, resource, null)}.
	 *
	 * @param subject
	 * 			an object representing the subject of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param action
	 * 			an object representing the action of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param resource
	 * 			an object representing the resource of the authorization request. May be mapped to an instance
	 * 	        of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 * 	        policy decision point.
	 * @return the response containing the final authorization decision. This decision may be different from the
	 *         original decision (if for example the required obligations could not be fulfilled.
	 */
	public Response getResponse(Object subject, Object action, Object resource) {
		return getResponse(subject, action, resource, null);
	}

	/**
	 * Retrieves an authorization response from the policy decision point but does not handle any obligations
	 * or advice contained in the response. If the authorization decision is {@link Decision#PERMIT} {@code true}
	 * is returned, {@code false} otherwise. This method is meant to be used if the specified action shall not
	 * yet be executed, but the caller still has to know whether it could be executed (e.g. to decide whether
	 * a link or button triggering the action should be displayed/enabled).
	 * Thus a later call to {@link #authorize(Object, Object, Object, Object) authorize()} or
	 * {@link #getResponse(Object, Object, Object, Object) getResponse()} has to be executed prior to performing
	 * the specified action.
	 *
	 * @param subject
	 * 			an object representing the subject of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param action
	 * 			an object representing the action of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param resource
	 * 			an object representing the resource of the authorization request. May be mapped to an instance
	 * 	        of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 * 	        policy decision point.
	 * @param environment
	 * 			an object representing the environment of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @return {@code true} if the authorization decision is {@link Decision#PERMIT}, {@code false} otherwise.
	 */
	public boolean wouldAuthorize(Object subject, Object action, Object resource, Object environment) {
		LOGGER.trace("Entering wouldAuthorize...");
		Object mappedSubject = sm.map(subject, SaplRequestElement.SUBJECT);
		Object mappedAction = sm.map(action, SaplRequestElement.ACTION);
		Object mappedResource = sm.map(resource, SaplRequestElement.RESOURCE);
		Object mappedEnvironment = sm.map(environment, SaplRequestElement.ENVIRONMENT);

		Response response = pdp.decide(mappedSubject, mappedAction, mappedResource, mappedEnvironment);
		LOGGER.debug("Response decision is: {}", response.getDecision());

		return response.getDecision() == Decision.PERMIT;
	}

	/**
	 * Retrieves an authorization response from the policy decision point and handles the returned obligations
	 * and advice if needed. If the final authorization decision is {@link Decision#PERMIT} {@code true} is
	 * returned, {@code false} otherwise.
	 *
	 * @param subject
	 * 			an object representing the subject of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param action
	 * 			an object representing the action of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param resource
	 * 			an object representing the resource of the authorization request. May be mapped to an instance
	 * 	        of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 * 	        policy decision point.
	 * @param environment
	 * 			an object representing the environment of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @return {@code true} if the final authorization decision is {@link Decision#PERMIT}, {@code false} otherwise.
	 */
	public boolean authorize(Object subject, Object action, Object resource, Object environment) {
		LOGGER.trace("Entering authorize...");
		Response response = runPolicyCheck(subject, action, resource, environment);
		LOGGER.debug("Response decision is: {}", response.getDecision());
		return response.getDecision() == Decision.PERMIT;
	}

	/**
	 * Retrieves an authorization response from the policy decision point and handles the returned obligations
	 * and advice if needed. The response containing the final authorization decision is returned.
	 *
	 * @param subject
	 * 			an object representing the subject of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param action
	 * 			an object representing the action of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param resource
	 * 			an object representing the resource of the authorization request. May be mapped to an instance
	 * 	        of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 * 	        policy decision point.
	 * @param environment
	 * 			an object representing the environment of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @return the response containing the final authorization decision. This decision may be different from the
	 *         original decision (if for example the required obligations could not be fulfilled.
	 */
	public Response getResponse(Object subject, Object action, Object resource, Object environment) {
		LOGGER.trace("Entering getResponse...");
		Response response = runPolicyCheck(subject, action, resource, environment);
		return response;
	}

	/**
	 * Retrieves an authorization response from the policy decision point and handles the returned obligations
	 * and advice if needed. The response containing the final authorization decision is returned.
	 *
	 * @param subject
	 * 			an object representing the subject of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param action
	 * 			an object representing the action of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @param resource
	 * 			an object representing the resource of the authorization request. May be mapped to an instance
	 * 	        of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 * 	        policy decision point.
	 * @param environment
	 * 			an object representing the environment of the authorization request. May be mapped to an instance
	 *          of another class using an available {@link SaplMapper SaplMapper} before sending it to the
	 *          policy decision point.
	 * @return the response containing the final authorization decision. This decision may be different from the
	 *         original decision (if for example the required obligations could not be fulfilled.
	 */
	protected Response runPolicyCheck(Object subject, Object action, Object resource, Object environment) {
		LOGGER.trace("Entering runPolicyCheck...");
		Object mappedSubject = sm.map(subject, SaplRequestElement.SUBJECT);
		Object mappedAction = sm.map(action, SaplRequestElement.ACTION);
		Object mappedResource = sm.map(resource, SaplRequestElement.RESOURCE);
		Object mappedEnvironment = sm.map(environment, SaplRequestElement.ENVIRONMENT);

		LOGGER.debug(
				"-------------------------------------- Response ---------------------------------------------------");
		LOGGER.debug("These are the parameters: \n  subject: {} \n  action: {} \n  resource: {} \n  environment: {}",
				mappedSubject, mappedAction, mappedResource, mappedEnvironment);

		Response response = pdp.decide(mappedSubject, mappedAction, mappedResource, mappedEnvironment);

		LOGGER.debug("Here comes the response: {}", response);

		if (response.getObligation().isPresent()) {
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

		if (response.getAdvice().isPresent()) {
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
