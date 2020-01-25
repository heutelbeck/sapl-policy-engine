/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.interpreter.combinators;

import java.util.List;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.Policy;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

/**
 * Interface which provides a method for obtaining a combined authorization decision for
 * the evaluation of multiple policies inside a policy set.
 */
public interface PolicyCombinator {

	/**
	 * Method which evaluates multiple SAPL policies against an authorization subscription
	 * object, combines the results and creates and returns a corresponding authorization
	 * decision object. The method is supposed to be used to determine an authorization
	 * decision for multiple policies inside a policy set.
	 * @param policies the list of policies
	 * @param ctx the evaluation context in which the given policies will be evaluated. It
	 * must contain
	 * <ul>
	 * <li>the attribute context</li>
	 * <li>the function context</li>
	 * <li>the variable context holding the four authorization subscription variables
	 * 'subject', 'action', 'resource' and 'environment' combined with system variables
	 * from the PDP configuration and other variables e.g. obtained from the containing
	 * policy set</li>
	 * <li>the import mapping for functions and attribute finders</li>
	 * </ul>
	 * @return a {@link Flux} of {@link AuthorizationDecision} objects containing the
	 * combined decision, the combined obligation and advice and a transformed resource if
	 * applicable. A new authorization decision object is only pushed if it is different
	 * from the previous one.
	 */
	Flux<AuthorizationDecision> combinePolicies(List<Policy> policies, EvaluationContext ctx);

}
