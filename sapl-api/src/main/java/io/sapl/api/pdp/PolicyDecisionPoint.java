package io.sapl.api.pdp;

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
	 * Takes an pre-built Request object and returns the matching decision response.
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

}
