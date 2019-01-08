package io.sapl.api.pdp;

import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;

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
     *            the multi request object containing the subjects, actions, resources
     *            and environments of the authorization requests to be evaluated by the
     *            PDP.
     * @return an object containing a response for each request in the multi-request
     *         object.
     */
    MultiResponse decide(MultiRequest multiRequest);

}
