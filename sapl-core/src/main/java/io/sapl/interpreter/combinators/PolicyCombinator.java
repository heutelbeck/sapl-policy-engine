package io.sapl.interpreter.combinators;

import java.util.List;

import io.sapl.api.pdp.Response;
import io.sapl.grammar.sapl.Policy;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

/**
 * Interface which provides a method for obtaining a combined Response for the evaluation
 * of multiple policies inside a policy set.
 */
public interface PolicyCombinator {

	/**
	 * Method which evaluates multiple SAPL policies against a Request object, combines
	 * the results and creates and returns a corresponding Response object. The method is
	 * supposed to be used to determine a response for multiple policies inside a policy
	 * set.
	 * @param policies the list of policies
	 * @param ctx the evaluation context in which the given policies will be evaluated. It
	 * must contain
	 * <ul>
	 * <li>the attribute context</li>
	 * <li>the function context</li>
	 * <li>the variable context holding the four request variables 'subject', 'action',
	 * 'resource' and 'environment' combined with system variables from the PDP
	 * configuration and other variables e.g. obtained from the containing policy set</li>
	 * <li>the import mapping for functions and attribute finders</li>
	 * </ul>
	 * @return a {@link Flux} of {@link Response} objects containing the combined
	 * decision, the combined obligation and advice and a transformed resource if
	 * applicable. A new response object is only pushed if it is different from the
	 * previous one.
	 */
	Flux<Response> combinePolicies(List<Policy> policies, EvaluationContext ctx);

}
