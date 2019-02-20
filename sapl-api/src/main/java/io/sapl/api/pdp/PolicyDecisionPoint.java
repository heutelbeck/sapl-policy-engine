package io.sapl.api.pdp;

import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The policy decision point is the component in the system, which will take a
 * request, retrieve matching policies from the policy retrieval point, evaluate
 * the policies, while potentially consulting external resources (e.g., through
 * attribute finders), and return a {@link Flux} of decision object.
 *
 * This interface offers a number of convenience methods to hand over a request
 * to the policy decision point, which only differ in the construction of the
 * underlying request object.
 */
public interface PolicyDecisionPoint {

	/**
	 * Takes a Request object and returns a {@link Flux} emitting
	 * matching decision responses.
	 *
	 * @param request
	 *            the SAPL request object
	 * @return a {@link Flux} emitting the responses for the given request.
	 *         New responses are only added to the stream if they are
	 *         different from the preceding response.
	 */
	Flux<Response> subscribe(Request request);

    /**
     * Multi-request variant of {@link #subscribe(Request)}.
	 *
     * @param multiRequest
	 *            the multi-request object containing the subjects, actions,
	 *            resources and environments of the authorization requests
	 *            to be evaluated by the PDP.
     * @return a {@link Flux} emitting responses for the given requests.
	 *         Related responses and requests have the same id.
     */
	Flux<IdentifiableResponse> subscribe(MultiRequest multiRequest);
	
	Mono<Response> decide(Request request);
	Mono<IdentifiableResponse> decide(MultiRequest multiRequest);
	
}
