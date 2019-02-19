package io.sapl.pep;

import java.util.List;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.advice.Advice;
import io.sapl.api.pdp.advice.AdviceHandlerService;
import io.sapl.api.pdp.multirequest.IdentifiableDecision;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.obligation.Obligation;
import io.sapl.api.pdp.obligation.ObligationFailed;
import io.sapl.api.pdp.obligation.ObligationHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * A reactive Policy Enforcement Point (PEP) implementation.
 */
@Slf4j
@RequiredArgsConstructor
public class SAPLAuthorizer {

	private final PolicyDecisionPoint pdp;
	private final ObligationHandlerService obs;
	private final AdviceHandlerService ahs;

	/**
	 * When clients of this {@code SAPLAuthorizer} no longer need it, they should
	 * call {@code dispose()} to give it the chance to clean up resources like
	 * subscriptions, threads, etc.
	 */
	public void dispose() {
		pdp.dispose();
	}

	/**
	 * Convenience method calling
	 * {@link #wouldAuthorize(Object, Object, Object, Object)
	 * wouldAuthorize(subject, action, resource, null)}.
	 */
	public Flux<Decision> wouldAuthorize(Object subject, Object action, Object resource) {
		return wouldAuthorize(subject, action, resource, null);
	}

	/**
	 * Retrieves authorization responses from the policy decision point but does not
	 * handle any obligations or advice contained in the responses (only checks,
	 * whether obligations could be handled). If the authorization decision is
	 * {@link Decision#PERMIT} and handlers for all the obligations contained in the
	 * response have been registered with the current obligation handler,
	 * {@link Decision#PERMIT} is pushed to the returned {@link Flux},
	 * {@link Decision#DENY} otherwise.<br>
	 * This method is meant to be used if the specified action should not yet be
	 * executed, but the caller still has to know whether it could be executed (e.g.
	 * to decide whether a link or button triggering the action should be
	 * displayed/enabled).<br>
	 * Thus a later call to {@link #authorize(Object, Object, Object, Object)
	 * authorize()} or {@link #getResponse(Object, Object, Object, Object)
	 * getResponse()} has to be executed prior to performing the specified action.
	 *
	 * @param subject     an object representing the subject of the authorization
	 *                    request. May be mapped to an instance of another class
	 *                    using an available {@link SaplMapper SaplMapper} before
	 *                    sending it to the policy decision point.
	 * @param action      an object representing the action of the authorization
	 *                    request. May be mapped to an instance of another class
	 *                    using an available {@link SaplMapper SaplMapper} before
	 *                    sending it to the policy decision point.
	 * @param resource    an object representing the resource of the authorization
	 *                    request. May be mapped to an instance of another class
	 *                    using an available {@link SaplMapper SaplMapper} before
	 *                    sending it to the policy decision point.
	 * @param environment an object representing the environment of the
	 *                    authorization request. May be mapped to an instance of
	 *                    another class using an available {@link SaplMapper
	 *                    SaplMapper} before sending it to the policy decision
	 *                    point.
	 * @return a {@link Flux} emitting the decisions of the responses for the given
	 *         request. Decisions are only emitted if they are different from the
	 *         preceding one.
	 */
	public Flux<Decision> wouldAuthorize(Object subject, Object action, Object resource, Object environment) {
		final Flux<Response> responseFlux = pdp.decide(subject, action, resource, environment);
		return responseFlux
				.map(response -> response.getDecision() == Decision.PERMIT && couldHandleObligations(response)
						? Decision.PERMIT
						: Decision.DENY)
				.distinctUntilChanged();
	}

	/**
	 * Multi-request variant of
	 * {@link #wouldAuthorize(Object, Object, Object, Object)}.
	 *
	 * @param multiRequest the multi request object containing the subjects,
	 *                     actions, resources and environments of the authorization
	 *                     requests to be sent to the PDP.
	 * @return a {@link Flux} emitting the decisions of the responses for the given
	 *         requests. Related decisions and requests have the same id.
	 */
	public Flux<IdentifiableDecision> wouldAuthorize(MultiRequest multiRequest) {
		final Flux<IdentifiableResponse> identifiableResponseFlux = pdp.decide(multiRequest);
		return identifiableResponseFlux.map(identifiableResponse -> {
			final Response response = identifiableResponse.getResponse();
			if (response.getDecision() == Decision.PERMIT && couldHandleObligations(response)) {
				return new IdentifiableDecision(identifiableResponse);
			} else {
				return new IdentifiableDecision(identifiableResponse.getRequestId(), Decision.DENY);
			}
		});
	}

	/**
	 * Convenience method calling {@link #authorize(Object, Object, Object, Object)
	 * authorize(subject, action, resource, null)}.
	 */
	public Flux<Decision> authorize(Object subject, Object action, Object resource) {
		return authorize(subject, action, resource, null);
	}

	/**
	 * Retrieves authorization responses from the policy decision point and handles
	 * the obligations and advice if needed. The final decisions are returned as a
	 * {@link Flux}.
	 *
	 * @param subject     an object representing the subject of the authorization
	 *                    request. May be mapped to an instance of another class
	 *                    using an available {@link SaplMapper SaplMapper} before
	 *                    sending it to the policy decision point.
	 * @param action      an object representing the action of the authorization
	 *                    request. May be mapped to an instance of another class
	 *                    using an available {@link SaplMapper SaplMapper} before
	 *                    sending it to the policy decision point.
	 * @param resource    an object representing the resource of the authorization
	 *                    request. May be mapped to an instance of another class
	 *                    using an available {@link SaplMapper SaplMapper} before
	 *                    sending it to the policy decision point.
	 * @param environment an object representing the environment of the
	 *                    authorization request. May be mapped to an instance of
	 *                    another class using an available {@link SaplMapper
	 *                    SaplMapper} before sending it to the policy decision
	 *                    point.
	 * @return a {@link Flux} emitting the decisions of the responses for the given
	 *         request. Decisions are only emitted if they are different from the
	 *         preceding one.
	 */
	public Flux<Decision> authorize(Object subject, Object action, Object resource, Object environment) {
		final Flux<Response> responseFlux = pdp.decide(subject, action, resource, environment);
		return responseFlux.map(response -> {
			final Response resp = handleObligations(response);
			handleAdvice(resp);
			return resp.getDecision();
		}).distinctUntilChanged();
	}

	/**
	 * Multi-request variant of {@link #authorize(Object, Object, Object, Object)}
	 * authorize(subject, action, resource, environment)}.
	 *
	 * @param multiRequest the multi request object containing the subjects,
	 *                     actions, resources and environments of the authorization
	 *                     requests to be sent to the PDP.
	 * @return a {@link Flux} emitting the decisions of the responses for the given
	 *         requests. Related decisions and requests have the same id.
	 */
	public Flux<IdentifiableDecision> authorize(MultiRequest multiRequest) {
		final Flux<IdentifiableResponse> responseFlux = pdp.decide(multiRequest);
		return responseFlux.map(response -> {
			final Response resp = handleObligations(response.getResponse());
			handleAdvice(resp);
			return new IdentifiableDecision(response.getRequestId(), resp.getDecision());
		});
	}

	/**
	 * Convenience method calling
	 * {@link #getResponse(Object, Object, Object, Object) getResponse(subject,
	 * action, resource, null)}.
	 */
	public Flux<Response> getResponse(Object subject, Object action, Object resource) {
		return getResponse(subject, action, resource, null);
	}

	/**
	 * Retrieves authorization responses from the policy decision point and handles
	 * the obligations and advice if needed. A {@link Flux} of responses containing
	 * the current authorization decisions is returned.
	 *
	 * @param subject     an object representing the subject of the authorization
	 *                    request. May be mapped to an instance of another class
	 *                    using an available {@link SaplMapper SaplMapper} before
	 *                    sending it to the policy decision point.
	 * @param action      an object representing the action of the authorization
	 *                    request. May be mapped to an instance of another class
	 *                    using an available {@link SaplMapper SaplMapper} before
	 *                    sending it to the policy decision point.
	 * @param resource    an object representing the resource of the authorization
	 *                    request. May be mapped to an instance of another class
	 *                    using an available {@link SaplMapper SaplMapper} before
	 *                    sending it to the policy decision point.
	 * @param environment an object representing the environment of the
	 *                    authorization request. May be mapped to an instance of
	 *                    another class using an available {@link SaplMapper
	 *                    SaplMapper} before sending it to the policy decision
	 *                    point.
	 * @return a {@link Flux} emitting the responses for the given request.
	 *         Responses are only emitted if they are different from the preceding
	 *         one.
	 */
	public Flux<Response> getResponse(Object subject, Object action, Object resource, Object environment) {
		final Flux<Response> responseFlux = pdp.decide(action, action, resource, environment);
		return responseFlux.map(response -> {
			final Response resp = handleObligations(response);
			handleAdvice(resp);
			return resp;
		}).distinctUntilChanged();
	}

	/**
	 * Multi-request variant of {@link #getResponse(Object, Object, Object, Object)
	 * getResponse(subject, action, resource, environment)}.
	 *
	 * @param multiRequest the multi request object containing the subjects,
	 *                     actions, resources and environments of the authorization
	 *                     requests to be sent to the PDP.
	 * @return a {@link Flux} emitting the responses for the given requests. Related
	 *         responses and requests have the same id.
	 */
	public Flux<IdentifiableResponse> getResponses(MultiRequest multiRequest) {
		final Flux<IdentifiableResponse> responseFlux = pdp.decide(multiRequest);
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
				if (!obs.couldHandle(obligation)) {
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
