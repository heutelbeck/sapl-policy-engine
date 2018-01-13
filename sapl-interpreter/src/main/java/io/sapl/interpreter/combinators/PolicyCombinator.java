package io.sapl.interpreter.combinators;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.grammar.sapl.Policy;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;

/**
 * Interface which provides a method for obtaining a combined Response for the
 * evaluation of multiple policies inside a policy set.
 */
public interface PolicyCombinator {

	/**
	 * Method which evaluates multiple SAPL policies against a Request object,
	 * combines the results and creates and returns a corresponding Response object.
	 * The method is supposed to be used to determine a response for multiple
	 * policies inside a policy set.
	 *
	 * @param policies
	 *            the list of policies
	 * @param request
	 *            the Request object
	 * @param attributeCtx
	 *            the attribute context
	 * @param functionCtx
	 *            the function context
	 * @param systemVariables
	 *            the system variables
	 * @param variables
	 *            custom variables, e.g., obtained from the containing policy set
	 * @param imports
	 *            the import mapping for functions and attribute finders
	 * @return a Response object containing the combined decision, the combined
	 *         obligation and advice and a transformed resource if applicable
	 */
	Response combinePolicies(List<Policy> policies, Request request, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables, Map<String, JsonNode> variables,
			Map<String, String> imports);
}
