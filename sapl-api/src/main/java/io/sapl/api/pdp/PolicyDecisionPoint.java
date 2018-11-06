package io.sapl.api.pdp;

import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import reactor.core.publisher.Flux;

/**
 * The policy decision point is the component in the system, which will take a
 * request, retrieve matching policies from the policy retrieval point, evaluate
 * the policies, while potentially consulting external resources (e.g., through
 * attribute finders), and returns a decision object.
 *
 * This interface offers a number of convenience methods to hand over a request
 * to the policy decision point, which only differ in the construction of the
 * underlying request object.
 *
 */
public interface PolicyDecisionPoint {
	/**
	 * Takes a pre-built Request object and returns the matching decision response.
	 *
	 * @param request
	 *            the SAPL request object
	 * @return the response for the given request.
	 */
	Response decide(Request request);

	/**
	 * Takes POJOs representing subject, action, resource, and environment. These
	 * objects are serialized to JSON and composed into a SAPL request.
	 *
	 * @param subject
	 *            a POJO representing the subject
	 * @param action
	 *            a POJO representing the action
	 * @param resource
	 *            a POJO representing the resource
	 * @param environment
	 *            a POJO representing the environment
	 * @return the response for the given request.
	 */
	Response decide(Object subject, Object action, Object resource, Object environment);

	/**
	 * Takes POJOs representing subject, action, and resource. These objects are
	 * serialized to JSON and composed into a SAPL request with the environment
	 * being null.
	 *
	 * @param subject
	 *            a POJO representing the subject
	 * @param action
	 *            a POJO representing the action
	 * @param resource
	 *            a POJO representing the resource
	 * @return the response for the given request.
	 */
	Response decide(Object subject, Object action, Object resource);

    /**
     * Multi-request variant of {@link #decide(Request)}.
	 *
     * @param multiRequest
	 *            the multi request object containing the subjects, actions, resources
	 *            and environments of the authorization requests to be evaluated by the
	 *            PDP.
     * @return an object containing a response for each request in the multi-request
	 *         object.
     */
	MultiResponse multiDecide(MultiRequest multiRequest);

	/**
	 * Takes a pre-built Request object and returns a {@link Flux} providing a
	 * stream of matching decision responses.
	 *
	 * @param request
	 *            the SAPL request object
	 * @return a {@link Flux} providing the responses for the given request as a
	 *         stream. New responses are only added to the stream if they are
	 *         different from the preceding response.
	 */
	Flux<Response> reactiveDecide(Request request);

	/**
	 * Takes POJOs representing subject, action, resource, and environment. These
	 * objects are serialized to JSON and composed into a SAPL request. Returns a
	 * {@link Flux} providing a stream of matching decision responses.
	 *
	 * @param subject
	 *            a POJO representing the subject
	 * @param action
	 *            a POJO representing the action
	 * @param resource
	 *            a POJO representing the resource
	 * @param environment
	 *            a POJO representing the environment
	 * @return a {@link Flux} providing the responses for the given request as a
	 *         stream. New responses are only added to the stream if they are
	 *         different from the preceding response.
	 */
	Flux<Response> reactiveDecide(Object subject, Object action, Object resource, Object environment);

	/**
	 * Takes POJOs representing subject, action, and resource. These objects are
	 * serialized to JSON and composed into a SAPL request with the environment
	 * being null. Returns a {@link Flux} providing a stream of matching decision
	 * responses.
	 *
	 * @param subject
	 *            a POJO representing the subject
	 * @param action
	 *            a POJO representing the action
	 * @param resource
	 *            a POJO representing the resource
	 * @return a {@link Flux} providing the responses for the given request as a
	 *         stream. New responses are only added to the stream if they are
	 *         different from the preceding response.
	 */
	Flux<Response> reactiveDecide(Object subject, Object action, Object resource);

    /**
     * Reactive variant of {@link #multiDecide(MultiRequest)}.
	 *
     * @param multiRequest
	 *            the multi request object containing the subjects, actions, resources
	 *            and environments of the authorization requests to be evaluated by the
	 *            PDP.
     * @return a {@link Flux} providing the responses for the given requests as a stream.
	 *         Related responses and requests have the same id.
     */
	Flux<IdentifiableResponse> reactiveMultiDecide(MultiRequest multiRequest);

}
