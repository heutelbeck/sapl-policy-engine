/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.interpreter;

import java.io.InputStream;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import reactor.core.publisher.Flux;

public interface SAPLInterpreter {

	/**
	 * Method which applies the SAPL parser to a String containing a SAPL document
	 * and generates the matching parse-tree.
	 * 
	 * @param saplDefinition a String containing a SAPL document
	 * @return A parse tree of the document @ in case an error occurs during
	 *         parsing. This may be either a syntax error or an IO error.
	 * @throws PolicyEvaluationException
	 */
	SAPL parse(String saplDefinition);

	/**
	 * Method which applies the SAPL parser to an InputStream containing a SAPL
	 * document and generates the matching parse-tree.
	 * 
	 * @param saplInputStream an InputStream containing a SAPL document
	 * @return A parse tree of the document @ in case an error occurs during
	 *         parsing. This may be either a syntax error or an IO error.
	 * @throws PolicyEvaluationException
	 */
	SAPL parse(InputStream saplInputStream);

	/**
	 * Convenience method for unit tests which evaluates a String representing a
	 * SAPL document (containing a policy set or policy) against an authorization
	 * subscription object within a given attribute context and function context and
	 * returns a {@link Flux} of {@link AuthorizationDecision} objects.
	 * 
	 * @param authzSubscription the authorization subscription object
	 * @param saplDefinition    the String representing the SAPL document
	 * @param evaluationCtx     the PDP scoped evaluation context
	 * @return A {@link Flux} of {@link AuthorizationDecision} objects.
	 */
	Flux<AuthorizationDecision> evaluate(AuthorizationSubscription authzSubscription, String saplDefinition,
			EvaluationContext evaluationCtx);

	/**
	 * Method which analyzes a String containing a SAPL document.
	 * 
	 * @param saplDefinition the String containing the SAPL document
	 * @return the document analysis result
	 */
	DocumentAnalysisResult analyze(String saplDefinition);

}
