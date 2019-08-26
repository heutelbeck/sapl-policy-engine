package io.sapl.spring;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;
import io.sapl.spring.constraints.ConstraintHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * This service can be used to establish a policy enforcement point at any location in
 * users code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEnforcementPoint {

	private static final Response DENY = Response.DENY;

	private final PolicyDecisionPoint pdp;

	private final ConstraintHandlerService constraintHandlers;

	private final ObjectMapper mapper;

	/**
	 * Creates a SAPL request based on its parameters and asks the PDP for a decision. In
	 * case of {@link Decision#PERMIT permit}, obligation and advice handlers are invoked.
	 * Emits {@link Decision#PERMIT permit} only if all obligations could be fulfilled and
	 * no resource value was provided by the PDP's response. Emits {@link Decision#DENY
	 * deny} otherwise. Decisions are only emitted if they are different from the
	 * preceding one.
	 * @param subject the subject, will be serialized into JSON.
	 * @param action the action, will be serialized into JSON.
	 * @param resource the resource, will be serialized into JSON.
	 * @param environment the environment, will be serialized into JSON.
	 * @return a Flux emitting {@link Decision#PERMIT permit}, if the PDP returned
	 * {@link Decision#PERMIT permit} and all obligations could be fulfilled, and the
	 * PDP's response did not contain a resource value, {@link Decision#DENY deny}
	 * otherwise.
	 */
	public Flux<Decision> enforce(Object subject, Object action, Object resource,
			Object environment) {
		return execute(subject, action, resource, environment, false)
				.map(Response::getDecision);

	}

	/**
	 * Creates a SAPL request based on its parameters and asks the PDP for a decision. In
	 * case of {@link Decision#PERMIT permit}, obligation and advice handlers are invoked.
	 * Emits {@link Decision#PERMIT permit} only if all obligations could be fulfilled and
	 * no resource value was provided by the PDP's response. Emits {@link Decision#DENY
	 * deny} otherwise. Decisions are only emitted if they are different from the
	 * preceding one.
	 * @param subject the subject, will be serialized into JSON.
	 * @param action the action, will be serialized into JSON.
	 * @param resource the resource, will be serialized into JSON.
	 * @return a Flux emitting {@link Decision#PERMIT permit}, if the PDP returned
	 * {@link Decision#PERMIT permit} and all obligations could be fulfilled, and the
	 * PDP's response did not contain a resource value, {@link Decision#DENY deny}
	 * otherwise.
	 */
	public Flux<Decision> enforce(Object subject, Object action, Object resource) {
		return enforce(subject, action, resource, null);
	}

	/**
	 * Creates a SAPL request based on its parameters and asks the PDP for a decision. In
	 * case of {@link Decision#PERMIT permit}, obligation and advice handlers are invoked.
	 * If all obligations can be fulfilled, the original response emitted by the PDP is
	 * passed through. Emits a {@link Response response} containing {@link Decision#DENY
	 * deny} and no resource otherwise. Responses are only emitted if they are different
	 * from the preceding one.
	 * @param subject the subject, will be serialized into JSON.
	 * @param action the action, will be serialized into JSON.
	 * @param resource the resource, will be serialized into JSON.
	 * @param environment the environment, will be serialized into JSON.
	 * @return a Flux emitting the original response of the PDP, if the PDP returned a
	 * response containing {@link Decision#PERMIT permit} and all obligations could be
	 * fulfilled, a {@link Response response} containing {@link Decision#DENY deny} and no
	 * resource otherwise.
	 */
	public Flux<Response> filterEnforce(Object subject, Object action, Object resource,
			Object environment) {
		return execute(subject, action, resource, environment, true);
	}

	private Flux<Response> execute(Object subject, Object action, Object resource,
		    Object environment, boolean supportResourceTransformation) {
		Request request = buildRequest(subject, action, resource, environment);
		final Flux<Response> responseFlux = pdp.decide(request);
		return responseFlux.map(response -> {
			LOGGER.debug("REQUEST  : ACTION={} RESOURCE={} SUBJ={} ENV={}",
					request.getAction(), request.getResource(), request.getSubject(),
					request.getEnvironment());
			LOGGER.debug("RESPONSE : {} - {}",
					response == null ? "null" : response.getDecision(), response);

			if (response == null || response.getDecision() != Decision.PERMIT) {
				return DENY;
			}
			if (!supportResourceTransformation && response.getResource().isPresent()) {
				LOGGER.debug("PDP returned a new resource value. "
						+ "This PEP cannot handle resource replacement. Thus, deny access.");
				return DENY;
			}
			try {
				constraintHandlers.handleObligations(response);
				constraintHandlers.handleAdvices(response);
				return response;
			}
			catch (AccessDeniedException e) {
				LOGGER.debug("PEP failed to fulfill PDP obligations ({}). Access denied by policy enforcement point.",
						e.getLocalizedMessage());
				return DENY;
			}
		}).distinctUntilChanged();
	}

	/**
	 * Creates a SAPL request based on its parameters and asks the PDP for a decision. In
	 * case of {@link Decision#PERMIT permit}, obligation and advice handlers are invoked.
	 * If all obligations can be fulfilled, the original response emitted by the PDP is
	 * passed through. Emits a {@link Response response} containing {@link Decision#DENY
	 * deny} and no resource otherwise. Responses are only emitted if they are different
	 * from the preceding one.
	 * @param subject the subject, will be serialized into JSON.
	 * @param action the action, will be serialized into JSON.
	 * @param resource the resource, will be serialized into JSON.
	 * @return a Flux emitting the original response of the PDP, if the PDP returned a
	 * response containing {@link Decision#PERMIT permit} and all obligations could be
	 * fulfilled, a {@link Response response} containing {@link Decision#DENY deny} and no
	 * resource otherwise.
	 */
	public Flux<Response> filterEnforce(Object subject, Object action, Object resource) {
		return filterEnforce(subject, action, resource, null);
	}

	/**
	 * Sends the given {@code multiRequest} to the PDP which emits
	 * {@link IdentifiableResponse identifiable responses} as soon as they are available.
	 * Each response is handled as follows: If its decision is {@link Decision#PERMIT permit},
	 * obligation and advice handlers are invoked. If all obligations can be fulfilled, the original
	 * response is left as is. If its decision is not {@link Decision#PERMIT permit} or if
	 * not all obligations can be fulfilled, the response is replaced by a response
	 * containing {@link Decision#DENY deny} and no resource.
	 * @param multiRequest the multi-request to be sent to the PDP.
	 * @return a Flux emitting {@link IdentifiableResponse identifiable responses} which may differ
	 *        from the original ones emitted by the PDP after having handled the obligations.
	 */
	public Flux<IdentifiableResponse> filterEnforce(MultiRequest multiRequest) {
		final Flux<IdentifiableResponse> multiResponseFlux = pdp.decide(multiRequest);
		return multiResponseFlux.map(identifiableResponse -> {
			LOGGER.debug("REQUEST           : {}", multiRequest.getRequestWithId(identifiableResponse.getRequestId()));
			LOGGER.debug("ORIGINAL RESPONSE : {}", identifiableResponse.getResponse());
			return handle(identifiableResponse);
		});
	}

	/**
	 * Sends the given {@code multiRequest} to the PDP which emits related
	 * {@link MultiResponse multi-responses}. Each response in the multi-response is
	 * handled as follows: If its decision is {@link Decision#PERMIT permit}, obligation
	 * and advice handlers are invoked. If all obligations can be fulfilled, the original
	 * response is left as is. If its decision is not {@link Decision#PERMIT permit} or if
	 * not all obligations can be fulfilled, the response is replaced by a response
	 * containing {@link Decision#DENY deny} and no resource. {@link MultiResponse}s are
	 * only emitted if they are different from the preceding one.
	 * @param multiRequest the multi-request to be sent to the PDP.
	 * @return a Flux emitting {@link MultiResponse multi-responses} which may differ from
	 * the original ones emitted by the PDP after having handled the obligations.
	 */
	public Flux<MultiResponse> filterEnforceAll(MultiRequest multiRequest) {
		final Flux<MultiResponse> multiResponseFlux = pdp.decideAll(multiRequest);
		return multiResponseFlux.map(multiResponse -> {
			LOGGER.debug("REQUEST           : {}", multiRequest);
			LOGGER.debug("ORIGINAL RESPONSE : {}", multiResponse);

			final MultiResponse resultResponse = new MultiResponse();
			for (IdentifiableResponse identifiableResponse : multiResponse) {
				final IdentifiableResponse handledResponse = handle(identifiableResponse);
				resultResponse.setResponseForRequestWithId(handledResponse.getRequestId(), handledResponse.getResponse());
			}

			LOGGER.debug("RETURNED RESPONSE : {}", resultResponse);
			return resultResponse;
		}).distinctUntilChanged();
	}

	private Request buildRequest(Object subject, Object action, Object resource,
								 Object environment) {
		final JsonNode subjectNode = mapper.valueToTree(subject);
		final JsonNode actionNode = mapper.valueToTree(action);
		final JsonNode resourceNode = mapper.valueToTree(resource);
		final JsonNode environmentNode = mapper.valueToTree(environment);
		return new Request(subjectNode, actionNode, resourceNode, environmentNode);
	}

	private IdentifiableResponse handle(IdentifiableResponse identifiableResponse) {
		final String requestId = identifiableResponse.getRequestId();
		final Response response = identifiableResponse.getResponse();
		if (response == null || response.getDecision() != Decision.PERMIT) {
			return new IdentifiableResponse(requestId, DENY);
		}
		try {
			constraintHandlers.handleObligations(response);
			constraintHandlers.handleAdvices(response);
			return identifiableResponse;
		}
		catch (AccessDeniedException e) {
			LOGGER.debug("PEP failed to fulfill PDP obligations ({}). Access denied by policy enforcement point.",
					e.getLocalizedMessage());
			return new IdentifiableResponse(requestId, DENY);
		}
	}

}
