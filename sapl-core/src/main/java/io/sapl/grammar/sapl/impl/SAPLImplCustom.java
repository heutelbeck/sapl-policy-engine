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
package io.sapl.grammar.sapl.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class SAPLImplCustom extends SAPLImpl {

	private static final String POLICY_EVALUATION_FAILED = "Policy evaluation failed: {}";

	private static final String IMPORT_EXISTS = "An import for name '%s' already exists.";

	private static final String IMPORT_NOT_FOUND = "Import '%s' was not found.";

	private static final String WILDCARD_IMPORT_EXISTS = "Wildcard import of '%s' not possible as an import for name '%s' already exists.";

	private static final String NO_TARGET_MATCH = "Target not matching.";

	private static final AuthorizationDecision INDETERMINATE = AuthorizationDecision.INDETERMINATE;

	/**
	 * Checks whether the SAPL document matches a AuthorizationSubscription by
	 * evaluating the document's target expression. No custom variables are provided
	 * and imports are extracted from the document.
	 * 
	 * @param ctx the evaluation context in which the document's target expression
	 *            is be evaluated. It must contain
	 *            <ul>
	 *            <li>the function context, as functions can be used in the target
	 *            expression</li>
	 *            <li>the variable context holding the four authorization
	 *            subscription variables 'subject', 'action', 'resource' and
	 *            'environment' combined with system variables from the PDP
	 *            configuration</li>
	 *            </ul>
	 * @return {@code true} if the target expression evaluates to {@code true},
	 *         {@code false} otherwise.
	 * @throws PolicyEvaluationException in case there is an error while evaluating
	 *                                   the target expression
	 */
	@Override
	public boolean matches(EvaluationContext ctx) throws PolicyEvaluationException {
		final Map<String, String> functionImports = fetchFunctionImports(ctx.getFunctionCtx());
		final EvaluationContext evaluationCtx = new EvaluationContext(ctx.getFunctionCtx(), ctx.getVariableCtx(),
				functionImports);
		return getPolicyElement().matches(evaluationCtx);
	}

	/**
	 * Evaluates the body of the SAPL document (containing a policy set or a policy)
	 * within the given evaluation context and returns a {@link Flux} of
	 * {@link AuthorizationDecision} objects.
	 * 
	 * @param ctx the evaluation context in which the document's body is evaluated.
	 *            It must contain
	 *            <ul>
	 *            <li>the attribute context</li>
	 *            <li>the function context</li>
	 *            <li>the variable context holding the four authorization
	 *            subscription variables 'subject', 'action', 'resource' and
	 *            'environment' combined with system variables from the PDP
	 *            configuration</li>
	 *            </ul>
	 * @return a {@link Flux} of {@link AuthorizationDecision} objects.
	 */
	@Override
	public Flux<AuthorizationDecision> evaluate(EvaluationContext ctx) {
		LOGGER.trace("| | |-- SAPL Evaluate: {} ({})", getPolicyElement().getSaplName(),
				getPolicyElement().getClass().getName());
		try {
			if (matches(ctx)) {
				final Map<String, String> imports = fetchFunctionAndPipImports(ctx);
				final EvaluationContext evaluationCtx = new EvaluationContext(ctx.getAttributeCtx(),
						ctx.getFunctionCtx(), ctx.getVariableCtx().copy(), imports);
				return getPolicyElement().evaluate(evaluationCtx).doOnNext(this::logAuthzDecision);
			} else {
				LOGGER.trace("| | |-- NOT_APPLICABLE. Cause: " + NO_TARGET_MATCH);
				LOGGER.trace("| |");
				return Flux.just(AuthorizationDecision.NOT_APPLICABLE);
			}
		} catch (PolicyEvaluationException e) {
			LOGGER.trace("| | |-- INDETERMINATE. Cause: " + POLICY_EVALUATION_FAILED, e.getMessage());
			LOGGER.trace("| |");
			return Flux.just(INDETERMINATE);
		}
	}

	private void logAuthzDecision(AuthorizationDecision r) {
		LOGGER.trace("| | |-- {}. Document: {} Cause: {}", r.getDecision(), getPolicyElement().getSaplName(), r);
		LOGGER.trace("| |");
	}

	@Override
	public Map<String, String> fetchFunctionImports(FunctionContext functionCtx) throws PolicyEvaluationException {
		final Map<String, String> imports = new HashMap<>();

		for (Import anImport : getImports()) {
			final String library = String.join(".", anImport.getLibSteps());

			if (anImport instanceof WildcardImport) {
				imports.putAll(fetchWildcardImports(imports, library, functionCtx.functionsInLibrary(library)));
			} else if (anImport instanceof LibraryImport) {
				final String alias = ((LibraryImport) anImport).getLibAlias();
				imports.putAll(fetchLibraryImports(imports, library, alias, functionCtx.functionsInLibrary(library)));
			} else {
				final String functionName = anImport.getFunctionName();
				final String fullyQualified = String.join(".", library, functionName);

				if (imports.containsKey(anImport.getFunctionName())) {
					throw new PolicyEvaluationException(IMPORT_EXISTS, fullyQualified);
				}
				imports.put(functionName, fullyQualified);
			}
		}

		return imports;
	}

	private Map<String, String> fetchFunctionAndPipImports(EvaluationContext ctx) throws PolicyEvaluationException {
		final FunctionContext functionCtx = ctx.getFunctionCtx();
		final AttributeContext attributeCtx = ctx.getAttributeCtx();

		final Map<String, String> imports = new HashMap<>();

		for (Import anImport : getImports()) {
			final String library = String.join(".", anImport.getLibSteps());
			if (anImport instanceof WildcardImport) {
				imports.putAll(fetchWildcardImports(imports, library, functionCtx.functionsInLibrary(library)));
				imports.putAll(fetchWildcardImports(imports, library, attributeCtx.findersInLibrary(library)));
			} else if (anImport instanceof LibraryImport) {
				String alias = ((LibraryImport) anImport).getLibAlias();
				imports.putAll(fetchLibraryImports(imports, library, alias, functionCtx.functionsInLibrary(library)));
				imports.putAll(fetchLibraryImports(imports, library, alias, attributeCtx.findersInLibrary(library)));
			} else {
				String functionName = anImport.getFunctionName();
				String fullyQualified = String.join(".", library, functionName);

				if (imports.containsKey(functionName)) {
					throw new PolicyEvaluationException(IMPORT_EXISTS, fullyQualified);
				} else if (!functionCtx.provides(fullyQualified) && !attributeCtx.provides(fullyQualified)) {
					throw new PolicyEvaluationException(IMPORT_NOT_FOUND, fullyQualified);
				}
				imports.put(functionName, fullyQualified);
			}
		}

		return imports;
	}

	private Map<String, String> fetchWildcardImports(Map<String, String> imports, String library,
			Collection<String> libraryItems) throws PolicyEvaluationException {
		final Map<String, String> returnImports = new HashMap<>(libraryItems.size(), 1.0F);
		for (String name : libraryItems) {
			if (imports.containsKey(name)) {
				throw new PolicyEvaluationException(WILDCARD_IMPORT_EXISTS, library, name);
			} else {
				returnImports.put(name, String.join(".", library, name));
			}
		}
		return returnImports;
	}

	private Map<String, String> fetchLibraryImports(Map<String, String> imports, String library, String alias,
			Collection<String> libraryItems) throws PolicyEvaluationException {
		final Map<String, String> returnImports = new HashMap<>(libraryItems.size(), 1.0F);
		for (String name : libraryItems) {
			String key = String.join(".", alias, name);
			if (imports.containsKey(key)) {
				throw new PolicyEvaluationException(WILDCARD_IMPORT_EXISTS, library, name);
			} else {
				returnImports.put(key, String.join(".", library, name));
			}
		}
		return returnImports;
	}

}
