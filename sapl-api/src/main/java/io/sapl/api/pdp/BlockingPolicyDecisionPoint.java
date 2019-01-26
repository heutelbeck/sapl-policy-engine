package io.sapl.api.pdp;

import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;

/**
 * Blocking (non-reactive) variant of the {@link PolicyDecisionPoint} interface.
 * Implementations may delegate to a reactive policy decision point and just return
 * the first emitted result.
 */
public interface BlockingPolicyDecisionPoint {

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
     *            the multi-request object containing the subjects, actions, resources
     *            and environments of the authorization requests to be evaluated by the
     *            PDP.
     * @return an object containing a response for each request in the multi-request
     *         object. Related responses and requests have the same id.
     */
    MultiResponse decide(MultiRequest multiRequest);

    /**
     * Implementations of this {@code BlockingPolicyDecisionPoint} interface may delegate to a
     * reactive policy decision point and just return the first emitted result. When clients of
     * the policy decision point no longer need it, they should call {@code dispose()} to give
     * the reactive policy decision point the chance to clean up resources like subscriptions,
     * threads, etc.
     */
    void dispose();

}
