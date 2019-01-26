package io.sapl.api.pdp;

import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiRequest;
import reactor.core.publisher.Flux;

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
	 * Takes a pre-built Request object and returns a {@link Flux} emitting
	 * matching decision responses.
	 *
	 * @param request
	 *            the SAPL request object
	 * @return a {@link Flux} emitting the responses for the given request.
	 *         New responses are only added to the stream if they are
	 *         different from the preceding response.
	 */
	Flux<Response> decide(Request request);

	/**
	 * Takes POJOs representing subject, action, resource, and environment. These
	 * objects are serialized to JSON and composed into a SAPL request. Returns a
	 * {@link Flux} emitting matching decision responses.
	 *
	 * @param subject
	 *            a POJO representing the subject
	 * @param action
	 *            a POJO representing the action
	 * @param resource
	 *            a POJO representing the resource
	 * @param environment
	 *            a POJO representing the environment
	 * @return a {@link Flux} emitting the responses for the given request.
	 *         New responses are only added to the stream if they are
	 *         different from the preceding response.
	 */
	Flux<Response> decide(Object subject, Object action, Object resource, Object environment);

	/**
	 * Takes POJOs representing subject, action, and resource. These objects are
	 * serialized to JSON and composed into a SAPL request with the environment
	 * being {@code null}. Returns a {@link Flux} emitting matching decision
	 * responses.
	 *
	 * @param subject
	 *            a POJO representing the subject
	 * @param action
	 *            a POJO representing the action
	 * @param resource
	 *            a POJO representing the resource
	 * @return a {@link Flux} emitting the responses for the given request.
	 *         New responses are only added to the stream if they are
	 *         different from the preceding response.
	 */
	Flux<Response> decide(Object subject, Object action, Object resource);

    /**
     * Multi-request variant of {@link #decide(Request)}.
	 *
     * @param multiRequest
	 *            the multi-request object containing the subjects, actions,
	 *            resources and environments of the authorization requests
	 *            to be evaluated by the PDP.
     * @return a {@link Flux} emitting responses for the given requests.
	 *         Related responses and requests have the same id.
     */
	Flux<IdentifiableResponse> decide(MultiRequest multiRequest);

	/**
	 * When clients of a policy decision point no longer need it, they should call
	 * {@code dispose()} to give it the chance to clean up resources like subscriptions,
	 * threads, etc.
	 */
	void dispose();

}
