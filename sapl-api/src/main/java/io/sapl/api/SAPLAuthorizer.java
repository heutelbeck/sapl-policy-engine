package io.sapl.api;

import java.util.List;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.advice.Advice;
import io.sapl.api.pdp.advice.AdviceHandlerService;
import io.sapl.api.pdp.mapping.SaplMapper;
import io.sapl.api.pdp.mapping.SaplRequestElement;
import io.sapl.api.pdp.multirequest.IdentifiableDecision;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiDecision;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;
import io.sapl.api.pdp.obligation.Obligation;
import io.sapl.api.pdp.obligation.ObligationFailed;
import io.sapl.api.pdp.obligation.ObligationHandlerService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class SAPLAuthorizer {

	private final PolicyDecisionPoint pdp;

	private final ObligationHandlerService obs;

	private final AdviceHandlerService ahs;

	private final SaplMapper sm;

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
	 */
	public boolean wouldAuthorize(Object subject, Object action, Object resource) {
		return wouldAuthorize(subject, action, resource, null);
	}

	/**
	 * Retrieves an authorization response from the policy decision point but does not handle any obligations
	 * or advice contained in the response (but checks, whether obligations could be handled, though). If the
	 * authorization decision is {@link Decision#PERMIT} and handlers for all the obligations contained in the
	 * response have been registered with the current obligation handler, {@code true} is returned, {@code false}
	 * otherwise. This method is meant to be used if the specified action should not yet be executed, but the
	 * caller still has to know whether it could be executed (e.g. to decide whether a link or button triggering
	 * the action should be displayed/enabled).
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
		final Object mappedSubject = sm.map(subject, SaplRequestElement.SUBJECT);
		final Object mappedAction = sm.map(action, SaplRequestElement.ACTION);
		final Object mappedResource = sm.map(resource, SaplRequestElement.RESOURCE);
		final Object mappedEnvironment = sm.map(environment, SaplRequestElement.ENVIRONMENT);

		final Response response = pdp.decide(mappedSubject, mappedAction, mappedResource, mappedEnvironment);
		LOGGER.debug("Response decision is: {}", response.getDecision());

		return response.getDecision() == Decision.PERMIT && couldHandleObligations(response);
	}

	/**
	 * Convenience method calling
	 * {@link #reactiveWouldAuthorize(Object, Object, Object, Object) reactiveWouldAuthorize(subject, action,
     * resource, null)}.
	 */
	public Flux<Decision> reactiveWouldAuthorize(Object subject, Object action, Object resource) {
		return reactiveWouldAuthorize(subject, action, resource, null);
	}

    /**
     * Reactive variant of {@link #wouldAuthorize(Object, Object, Object, Object) wouldAuthorize(subject, action,
     * resource, environment)}.
     *
     * @return a {@link Flux} providing the decisions of the responses for the given request as a stream. New
     *         decisions are only added to the stream if they are different from the preceding decision.
     */
    public Flux<Decision> reactiveWouldAuthorize(Object subject, Object action, Object resource, Object environment) {
		final Object mappedSubject = sm.map(subject, SaplRequestElement.SUBJECT);
		final Object mappedAction = sm.map(action, SaplRequestElement.ACTION);
		final Object mappedResource = sm.map(resource, SaplRequestElement.RESOURCE);
		final Object mappedEnvironment = sm.map(environment, SaplRequestElement.ENVIRONMENT);

		final Flux<Response> responseFlux = pdp.reactiveDecide(mappedSubject, mappedAction, mappedResource, mappedEnvironment);
		return responseFlux
				.map(response ->
					response.getDecision() == Decision.PERMIT && couldHandleObligations(response)
						? Decision.PERMIT
						: Decision.DENY
				)
				.distinctUntilChanged();
	}

	/**
	 * Multi-request variant of {@link #wouldAuthorize(Object, Object, Object, Object) wouldAuthorize(subject,
     * action, resource, environment)}.
     *
	 * @param multiRequest the multi request object containing the subjects, actions, resources and environments
     *                     of the authorization requests to be sent to the PDP.
	 * @return an object containing a decision for each request in the multi-request object.
	 */
	public MultiDecision wouldAuthorize(MultiRequest multiRequest) {
		multiRequest.applySaplMapper(sm);
		final MultiResponse multiResponse = pdp.multiDecide(multiRequest);
		final MultiResponse adjustedMultiResponse = new MultiResponse();
		multiResponse.forEach(identifiableResponse -> {
			final String requestId = identifiableResponse.getRequestId();
			final Response response = identifiableResponse.getResponse();
			if (response.getDecision() == Decision.PERMIT && couldHandleObligations(response)) {
				adjustedMultiResponse.setResponseForRequestWithId(requestId, response);
			} else {
				adjustedMultiResponse.setResponseForRequestWithId(requestId, Response.deny());
			}
		});
		return new MultiDecision(adjustedMultiResponse);
	}

	/**
	 * Reactive variant of {@link #wouldAuthorize(MultiRequest)}.
     *
	 * @param multiRequest the multi request object containing the subjects, actions, resources and environments
     *                     of the authorization requests to be sent to the PDP.
	 * @return a {@link Flux} providing the decisions of the responses for the given requests as a stream.
     *         Related decisions and requests have the same id.
	 */
	public Flux<IdentifiableDecision> reactiveWouldAuthorize(MultiRequest multiRequest) {
		multiRequest.applySaplMapper(sm);
		final Flux<IdentifiableResponse> identifiableResponseFlux = pdp.reactiveMultiDecide(multiRequest);
		return identifiableResponseFlux
				.map(identifiableResponse -> {
					final Response response = identifiableResponse.getResponse();
					if (response.getDecision() == Decision.PERMIT && couldHandleObligations(response)) {
						return new IdentifiableDecision(identifiableResponse);
					} else {
						return new IdentifiableDecision(identifiableResponse.getRequestId(), Decision.DENY);
					}
				});
	}

	/**
	 * Convenience method calling
	 * {@link #authorize(Object, Object, Object, Object) authorize(subject, action, resource, null)}.
	 */
	public boolean authorize(Object subject, Object action, Object resource) {
		return authorize(subject, action, resource, null);
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
        final Object mappedSubject = sm.map(subject, SaplRequestElement.SUBJECT);
        final Object mappedAction = sm.map(action, SaplRequestElement.ACTION);
        final Object mappedResource = sm.map(resource, SaplRequestElement.RESOURCE);
        final Object mappedEnvironment = sm.map(environment, SaplRequestElement.ENVIRONMENT);

        LOGGER.debug("Calling PDP.decide() with:\n\tsubject: {}\n\taction: {}\n\tresource: {}\n\tenvironment: {}",
                mappedSubject, mappedAction, mappedResource, mappedEnvironment);

        Response response = pdp.decide(mappedSubject, mappedAction, mappedResource, mappedEnvironment);

        LOGGER.debug("Response from PDP.decide(): {}", response);

        response = handleObligations(response);
        handleAdvice(response);

		LOGGER.debug("Response decision is: {}", response.getDecision());
		return response.getDecision() == Decision.PERMIT;
	}

    /**
     * Convenience method calling
     * {@link #reactiveAuthorize(Object, Object, Object, Object) reactiveAuthorize(subject, action, resource, null)}.
     */
    public Flux<Decision> reactiveAuthorize(Object subject, Object action, Object resource) {
	    return reactiveAuthorize(subject, action, resource, null);
    }

    /**
     * Reactive variant of {@link #authorize(Object, Object, Object, Object) authorize(subject, action, resource,
     * environment)}.
     *
     * @return a {@link Flux} providing the decisions of the responses for the given request as a stream. New
     *         decisions are only added to the stream if they are different from the preceding decision.
     */
    public Flux<Decision> reactiveAuthorize(Object subject, Object action, Object resource, Object environment) {
        final Object mappedSubject = sm.map(subject, SaplRequestElement.SUBJECT);
        final Object mappedAction = sm.map(action, SaplRequestElement.ACTION);
        final Object mappedResource = sm.map(resource, SaplRequestElement.RESOURCE);
        final Object mappedEnvironment = sm.map(environment, SaplRequestElement.ENVIRONMENT);

        final Flux<Response> responseFlux = pdp.reactiveDecide(mappedSubject, mappedAction, mappedResource, mappedEnvironment);
        return responseFlux.map(response -> {
            final Response resp = handleObligations(response);
            handleAdvice(resp);
            return resp.getDecision();
        }).distinctUntilChanged();
    }

    /**
     * Multi-request variant of {@link #authorize(Object, Object, Object, Object) authorize(subject, action,
     * resource, environment)}.
     *
     * @param multiRequest the multi request object containing the subjects, actions, resources and environments
     *                     of the authorization requests to be sent to the PDP.
     * @return an object containing a decision for each request in the multi-request object.
     */
    public MultiDecision authorize(MultiRequest multiRequest) {
        multiRequest.applySaplMapper(sm);
        final MultiResponse multiResponse = pdp.multiDecide(multiRequest);
        for (IdentifiableResponse identifiableResponse : multiResponse) {
            final Response response = handleObligations(identifiableResponse.getResponse());
            handleAdvice(response);
            multiResponse.setResponseForRequestWithId(identifiableResponse.getRequestId(), response);
        }
        return new MultiDecision(multiResponse);
    }

    /**
     * Reactive variant of {@link #authorize(MultiRequest)}.
     *
     * @param multiRequest the multi request object containing the subjects, actions, resources and environments
     *                     of the authorization requests to be sent to the PDP.
     * @return a {@link Flux} providing the decisions of the responses for the given requests as a stream.
     *         Related decisions and requests have the same id.
     */
    public Flux<IdentifiableDecision> reactiveAuthorize(MultiRequest multiRequest) {
        multiRequest.applySaplMapper(sm);
        final Flux<IdentifiableResponse> responseFlux = pdp.reactiveMultiDecide(multiRequest);
        return responseFlux.map(response -> {
            final Response resp = handleObligations(response.getResponse());
            handleAdvice(resp);
            return new IdentifiableDecision(response.getRequestId(), resp.getDecision());
        });
    }

	/**
	 * Convenience method calling
	 * {@link #getResponse(Object, Object, Object, Object) getResponse(subject, action, resource, null)}.
	 */
	public Response getResponse(Object subject, Object action, Object resource) {
		return getResponse(subject, action, resource, null);
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
	 *         original decision (if not all of the required obligations could be fulfilled).
	 */
	public Response getResponse(Object subject, Object action, Object resource, Object environment) {
		LOGGER.trace("Entering getResponse...");
        final Object mappedSubject = sm.map(subject, SaplRequestElement.SUBJECT);
        final Object mappedAction = sm.map(action, SaplRequestElement.ACTION);
        final Object mappedResource = sm.map(resource, SaplRequestElement.RESOURCE);
        final Object mappedEnvironment = sm.map(environment, SaplRequestElement.ENVIRONMENT);

        LOGGER.debug("Calling PDP.decide() with:\n\tsubject: {}\n\taction: {}\n\tresource: {}\n\tenvironment: {}",
                mappedSubject, mappedAction, mappedResource, mappedEnvironment);

        Response response = pdp.decide(mappedSubject, mappedAction, mappedResource, mappedEnvironment);

        LOGGER.debug("Response from PDP.decide(): {}", response);

        response = handleObligations(response);
        handleAdvice(response);

        return response;
	}

    /**
     * Convenience method calling
     * {@link #reactiveGetResponse(Object, Object, Object, Object) reactiveGetResponse(subject, action, resource, null)}.
     */
    public Flux<Response> reactiveGetResponse(Object subject, Object action, Object resource) {
	    return reactiveGetResponse(subject, action, resource, null);
    }

    /**
     * Reactive variant of {@link #getResponse(Object, Object, Object, Object) getResponse(subject, action, resource,
     * environment)}.
     *
     * @return a {@link Flux} providing the responses for the given request as a stream. New responses are only added
     *         to the stream if they are different from the preceding response.
     */
	public Flux<Response> reactiveGetResponse(Object subject, Object action, Object resource, Object environment) {
        final Object mappedSubject = sm.map(subject, SaplRequestElement.SUBJECT);
        final Object mappedAction = sm.map(action, SaplRequestElement.ACTION);
        final Object mappedResource = sm.map(resource, SaplRequestElement.RESOURCE);
        final Object mappedEnvironment = sm.map(environment, SaplRequestElement.ENVIRONMENT);

        final Flux<Response> responseFlux = pdp.reactiveDecide(mappedSubject, mappedAction, mappedResource, mappedEnvironment);
        return responseFlux.map(response -> {
            final Response resp = handleObligations(response);
            handleAdvice(resp);
            return resp;
        }).distinctUntilChanged();
    }

    /**
     * Multi-request variant of {@link #getResponse(Object, Object, Object, Object) getResponse(subject, action,
     * resource, environment)}.
     *
     * @param multiRequest the multi request object containing the subjects, actions, resources and environments
     *                     of the authorization requests to be sent to the PDP.
     * @return an object containing a response for each request in the multi-request object.
     */
    public MultiResponse getResponses(MultiRequest multiRequest) {
	    multiRequest.applySaplMapper(sm);
        final MultiResponse multiResponse = pdp.multiDecide(multiRequest);
        for (IdentifiableResponse identifiableResponse : multiResponse) {
            final Response response = handleObligations(identifiableResponse.getResponse());
            handleAdvice(response);
            multiResponse.setResponseForRequestWithId(identifiableResponse.getRequestId(), response);
        }
        return multiResponse;
    }

    /**
     * Reactive variant of {@link #getResponses(MultiRequest)}.
     *
     * @param multiRequest the multi request object containing the subjects, actions, resources and environments
     *                     of the authorization requests to be sent to the PDP.
     * @return a {@link Flux} providing the responses for the given requests as a stream. Related responses and
     *         requests have the same id.
     */
    public Flux<IdentifiableResponse> reactiveGetResponses(MultiRequest multiRequest) {
        multiRequest.applySaplMapper(sm);
        final Flux<IdentifiableResponse> responseFlux = pdp.reactiveMultiDecide(multiRequest);
        return responseFlux.map(response -> {
            final Response resp = handleObligations(response.getResponse());
            handleAdvice(resp);
            return new IdentifiableResponse(response.getRequestId(), resp);
        });
    }

    private boolean couldHandleObligations(Response response) {
    	if (response.getObligation().isPresent()) {
			final List<Obligation> obligations = Obligation.fromJson(response.getObligation().get());
			for (Obligation obligation : obligations) {
				if (! obs.couldHandle(obligation)) {
					return false;
				}
			}
		}
		return true;
	}

    private Response handleObligations(Response response) {
	    boolean obligationFailed = false;
        if (response.getObligation().isPresent()) {
            final List<Obligation> obligations = Obligation.fromJson(response.getObligation().get());
            try {
                for (Obligation obligation : obligations) {
                    LOGGER.debug("Handling obligation {}", obligation);
                    obs.handle(obligation);
                }
            } catch (ObligationFailed e) {
                obligationFailed = true;
            }
        }
        return obligationFailed ? Response.deny() : response;
    }

    private void handleAdvice(Response response) {
        if (response.getAdvice().isPresent()) {
            final List<Advice> advice = Advice.fromJson(response.getAdvice().get());
            for (Advice advise : advice) {
                LOGGER.debug("Handling advise {}", advise);
                ahs.handle(advise);
            }
        }
    }

}
