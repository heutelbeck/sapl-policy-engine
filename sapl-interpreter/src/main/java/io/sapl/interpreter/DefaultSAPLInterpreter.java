/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.interpreter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.inject.Injector;

import io.sapl.api.interpreter.DocumentAnalysisResult;
import io.sapl.api.interpreter.DocumentType;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.Statement;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.interpreter.combinators.DenyOverridesCombinator;
import io.sapl.interpreter.combinators.DenyUnlessPermitCombinator;
import io.sapl.interpreter.combinators.FirstApplicableCombinator;
import io.sapl.interpreter.combinators.OnlyOneApplicableCombinator;
import io.sapl.interpreter.combinators.PermitOverridesCombinator;
import io.sapl.interpreter.combinators.PermitUnlessDenyCombinator;
import io.sapl.interpreter.combinators.PolicyCombinator;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.variables.VariableContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * The default implementation of the SAPLInterpreter interface.
 */
@Slf4j
public class DefaultSAPLInterpreter implements SAPLInterpreter {

	private static final String CANNOT_ASSIGN_UNDEFINED_TO_A_VAL = "Cannot assign undefined to a val.";

	private static final Response INDETERMINATE = Response.indeterminate();

	@FunctionalInterface
	interface FluxProvider<T> {

		Flux<T> getFlux();

	}

	private static class Void {

		private static final Void INSTANCE = new Void();

	}

	private static final String PERMIT = "permit";

	private static final String DUMMY_RESOURCE_URI = "policy:/apolicy.sapl";

	static final String POLICY_EVALUATION_FAILED = "Policy evaluation failed: {}";
	static final String PARSING_ERRORS = "Parsing errors: %s";
	static final String NO_TARGET_MATCH = "Target not matching.";
	static final String STATEMENT_NOT_BOOLEAN = "Evaluation error: Statement must evaluate to a boolean value, but was: '%s'.";
	static final String OBLIGATIONS_ERROR = "Error occurred while evaluating obligations.";
	static final String ADVICE_ERROR = "Error occurred while evaluating advice.";
	static final String TRANSFORMATION_ERROR = "Error occurred while evaluating transformation.";
	static final String IMPORT_NOT_FOUND = "Import '%s' was not found.";
	static final String IMPORT_EXISTS = "An import for name '%s' already exists.";
	static final String WILDCARD_IMPORT_EXISTS = "Wildcard import of '%s' not possible as an import for name '%s' already exists.";

	private static final Injector INJECTOR = new SAPLStandaloneSetup()
			.createInjectorAndDoEMFRegistration();

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Override
	public SAPL parse(String saplDefinition) throws PolicyEvaluationException {
		return (SAPL) loadAsResource(saplDefinition).getContents().get(0);
	}

	@Override
	public SAPL parse(InputStream saplInputStream) throws PolicyEvaluationException {
		return (SAPL) loadAsResource(saplInputStream).getContents().get(0);
	}

	private static Resource loadAsResource(InputStream policyInputStream)
			throws PolicyEvaluationException {
		final XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		final Resource resource = resourceSet
				.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

		try {
			resource.load(policyInputStream, resourceSet.getLoadOptions());
		}
		catch (IOException | WrappedException e) {
			throw new PolicyEvaluationException(
					String.format(PARSING_ERRORS, resource.getErrors()), e);
		}

		if (!resource.getErrors().isEmpty()) {
			throw new PolicyEvaluationException(
					String.format(PARSING_ERRORS, resource.getErrors()));
		}
		return resource;
	}

	private static Resource loadAsResource(String saplDefinition)
			throws PolicyEvaluationException {
		final XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		final Resource resource = resourceSet
				.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

		try (InputStream in = new ByteArrayInputStream(
				saplDefinition.getBytes(StandardCharsets.UTF_8))) {
			resource.load(in, resourceSet.getLoadOptions());
		}
		catch (IOException e) {
			throw new PolicyEvaluationException(
					String.format(PARSING_ERRORS, resource.getErrors()), e);
		}

		if (!resource.getErrors().isEmpty()) {
			throw new PolicyEvaluationException(
					String.format(PARSING_ERRORS, resource.getErrors()));
		}
		return resource;
	}

	@Override
	public boolean matches(Request request, SAPL saplDocument,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables)
			throws PolicyEvaluationException {

		final VariableContext variableCtx = new VariableContext(request, systemVariables);
		final EvaluationContext evaluationCtx = new EvaluationContext(functionCtx, variableCtx);
		return saplDocument.matches(request, evaluationCtx);
	}

	@Override
	public boolean matches(Request request, PolicyElement policyElement,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables,
			Map<String, JsonNode> variables, Map<String, String> imports)
			throws PolicyEvaluationException {

		final VariableContext variableCtx = new VariableContext(request, systemVariables);
		if (variables != null) {
			for (Map.Entry<String, JsonNode> entry : variables.entrySet()) {
				variableCtx.put(entry.getKey(), entry.getValue());
			}
		}
		final EvaluationContext evaluationCtx = new EvaluationContext(functionCtx, variableCtx, imports);
		return policyElement.matches(request, evaluationCtx);
	}

	@Override
	public Flux<Response> evaluate(Request request, String saplDefinition,
			AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables) {
		final SAPL saplDocument;
		try {
			saplDocument = parse(saplDefinition);
		}
		catch (PolicyEvaluationException e) {
			LOGGER.error("Error in policy parsing: {}", e.getMessage());
			return Flux.just(INDETERMINATE);
		}

		return evaluate(request, saplDocument, attributeCtx, functionCtx, systemVariables)
				.onErrorReturn(INDETERMINATE);
	}

	@Override
	public Flux<Response> evaluate(Request request, SAPL saplDocument,
			AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables) {
		LOGGER.trace("| | |-- Interpreter Evaluate: {} ({})",
				saplDocument.getPolicyElement().getSaplName(),
				saplDocument.getPolicyElement().getClass().getName());
		try {
			if (matches(request, saplDocument, functionCtx, systemVariables)) {
				return evaluateRules(request, saplDocument, attributeCtx, functionCtx,
						systemVariables).map(r -> {
							logResponse(r, saplDocument);
							return r;
						});
			}
			else {
				LOGGER.trace("| | |-- NOT_APPLICABLE. Cause: " + NO_TARGET_MATCH);
				LOGGER.trace("| |");
				return Flux.just(Response.notApplicable());
			}
		}
		catch (PolicyEvaluationException e) {
			LOGGER.trace("| | |-- INDETERMINATE. Cause: " + POLICY_EVALUATION_FAILED,
					e.getMessage());
			LOGGER.trace("| |");
			return Flux.just(INDETERMINATE);
		}
	}

	@Override
	public Flux<Response> evaluateRules(Request request, SAPL saplDocument,
			AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables) {
		try {
			final Map<String, String> imports = fetchFunctionAndPipImports(saplDocument,
					functionCtx, attributeCtx);
			final PolicyElement policyElement = saplDocument.getPolicyElement();
			return evaluateRules(request, policyElement, attributeCtx, functionCtx,
					systemVariables, null, imports);
		}
		catch (PolicyEvaluationException e) {
			return Flux.just(INDETERMINATE);
		}
	}

	@Override
	public Flux<Response> evaluateRules(Request request, PolicyElement policyElement,
			AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables, Map<String, JsonNode> variables,
			Map<String, String> imports) {
		if (policyElement instanceof PolicySet) {
			final PolicySet policySet = (PolicySet) policyElement;
			return evaluatePolicySetRules(request, policySet, attributeCtx, functionCtx,
					systemVariables, imports);
		}
		else {
			final Policy policy = (Policy) policyElement;
			return evaluatePolicyRules(request, policy, attributeCtx, functionCtx,
					systemVariables, variables, imports);
		}
	}

	private Flux<Response> evaluatePolicySetRules(Request request, PolicySet policySet,
			AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables, Map<String, String> imports) {
		final VariableContext variableCtx;
		try {
			variableCtx = new VariableContext(request, systemVariables);
		}
		catch (PolicyEvaluationException e) {
			return Flux.just(INDETERMINATE);
		}

		final EvaluationContext evaluationCtx = new EvaluationContext(attributeCtx,
				functionCtx, variableCtx, imports);
		final Map<String, JsonNode> variables = new HashMap<>();
		final List<FluxProvider<Void>> fluxProviders = new ArrayList<>(
				policySet.getValueDefinitions().size());
		for (ValueDefinition valueDefinition : policySet.getValueDefinitions()) {
			fluxProviders.add(() -> evaluateValueDefinition(valueDefinition,
					evaluationCtx, variables));
		}
		final Flux<Void> variablesFlux = cascadingSwitchMap(fluxProviders, 0);

		final PolicyCombinator combinator;
		switch (policySet.getAlgorithm()) {
		case "deny-unless-permit":
			combinator = new DenyUnlessPermitCombinator(this);
			break;
		case "permit-unless-deny":
			combinator = new PermitUnlessDenyCombinator(this);
			break;
		case "deny-overrides":
			combinator = new DenyOverridesCombinator(this);
			break;
		case "permit-overrides":
			combinator = new PermitOverridesCombinator(this);
			break;
		case "only-one-applicable":
			combinator = new OnlyOneApplicableCombinator(this);
			break;
		default: // "first-applicable":
			combinator = new FirstApplicableCombinator(this);
			break;
		}

		return variablesFlux.flatMap(
				voiD -> combinator.combinePolicies(policySet.getPolicies(), request,
						attributeCtx, functionCtx, systemVariables, variables, imports))
				.onErrorReturn(INDETERMINATE);
	}

	private static Flux<Void> evaluateValueDefinition(ValueDefinition valueDefinition,
			EvaluationContext evaluationCtx, Map<String, JsonNode> variables) {
		return valueDefinition.getEval().evaluate(evaluationCtx, true, Optional.empty())
				.map(evaluatedValue -> {
					try {
						if (!evaluatedValue.isPresent()) {
							throw new PolicyEvaluationException(
									CANNOT_ASSIGN_UNDEFINED_TO_A_VAL);
						}
						evaluationCtx.getVariableCtx().put(valueDefinition.getName(),
								evaluatedValue.get());
						variables.put(valueDefinition.getName(), evaluatedValue.get());
						return Void.INSTANCE;
					}
					catch (PolicyEvaluationException e) {
						LOGGER.error("Value definition evaluation failed: {}",e.getMessage(), e);
						throw Exceptions.propagate(e);
					}
				}).onErrorResume(error -> Flux.error(Exceptions.unwrap(error)));
	}

	private static Flux<Void> cascadingSwitchMap(List<FluxProvider<Void>> fluxProviders,
			int idx) {
		if (idx < fluxProviders.size()) {
			return fluxProviders.get(idx).getFlux()
					.switchMap(result -> cascadingSwitchMap(fluxProviders, idx + 1));
		}
		return Flux.just(Void.INSTANCE);
	}

	private Flux<Response> evaluatePolicyRules(Request request, Policy policy,
			AttributeContext attributeCtx, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables, Map<String, JsonNode> variables,
			Map<String, String> imports) {
		final EvaluationContext evaluationCtx;
		try {
			final VariableContext variableCtx = new VariableContext(request,
					systemVariables);
			if (variables != null) {
				for (Map.Entry<String, JsonNode> entry : variables.entrySet()) {
					variableCtx.put(entry.getKey(), entry.getValue());
				}
			}
			evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, variableCtx,
					imports);
		}
		catch (PolicyEvaluationException e) {
			return Flux.just(INDETERMINATE);
		}

		final Decision entitlement = PERMIT.equals(policy.getEntitlement())
				? Decision.PERMIT : Decision.DENY;
		final Flux<Decision> decisionFlux;
		if (policy.getBody() != null) {
			decisionFlux = evaluateBody(entitlement, policy.getBody(), evaluationCtx);
		}
		else {
			decisionFlux = Flux.just(entitlement);
		}

		return decisionFlux.flatMap(decision -> {
			if (decision == Decision.PERMIT || decision == Decision.DENY) {
				return evaluateObligationsAndAdvice(policy, evaluationCtx)
						.map(obligationsAndAdvice -> {
							final Optional<ArrayNode> obligations = obligationsAndAdvice
									.getT1();
							final Optional<ArrayNode> advice = obligationsAndAdvice
									.getT2();
							return new Response(decision, Optional.empty(), obligations,
									advice);
						});
			}
			else {
				return Flux.just(new Response(decision, Optional.empty(),
						Optional.empty(), Optional.empty()));
			}
		}).flatMap(response -> {
			final Decision decision = response.getDecision();
			if (decision == Decision.PERMIT) {
				return evaluateTransformation(policy, evaluationCtx)
						.map(resource -> new Response(decision, resource,
								response.getObligations(), response.getAdvices()));
			}
			else {
				return Flux.just(response);
			}
		}).onErrorReturn(INDETERMINATE);
	}

	private static Flux<Decision> evaluateBody(Decision entitlement, PolicyBody body,
			EvaluationContext evaluationCtx) {
		final EList<Statement> statements = body.getStatements();
		if (statements != null && !statements.isEmpty()) {
			final boolean initialResult = true;
			final List<FluxProvider<Boolean>> fluxProviders = new ArrayList<>(
					statements.size());
			for (Statement statement : statements) {
				fluxProviders.add(() -> evaluateStatement(evaluationCtx, statement));
			}
			return cascadingSwitchMap(initialResult, fluxProviders, 0)
					.map(result -> result ? entitlement : Decision.NOT_APPLICABLE)
					.onErrorResume(error -> {
						final Throwable unwrapped = Exceptions.unwrap(error);
						LOGGER.error("Error in policy body evaluation: {}",
								unwrapped.getMessage());
						return Flux.just(Decision.INDETERMINATE);
					});
		}
		else {
			return Flux.just(entitlement);
		}
	}

	private static Flux<Boolean> cascadingSwitchMap(boolean currentResult,
			List<FluxProvider<Boolean>> fluxProviders, int idx) {
		if (idx < fluxProviders.size() && currentResult) {
			return fluxProviders.get(idx).getFlux().switchMap(
					result -> cascadingSwitchMap(result, fluxProviders, idx + 1));
		}
		return Flux.just(currentResult);
	}

	private static Flux<Boolean> evaluateStatement(EvaluationContext evaluationCtx,
			Statement statement) {
		if (statement instanceof ValueDefinition) {
			return evaluateValueDefinition((ValueDefinition) statement, evaluationCtx);
		}
		else {
			return evaluateCondition((Condition) statement, evaluationCtx);
		}
	}

	private static Flux<Boolean> evaluateValueDefinition(ValueDefinition valueDefinition,
			EvaluationContext evaluationCtx) {
		return valueDefinition.getEval().evaluate(evaluationCtx, true, Optional.empty())
				.map(evaluatedValue -> {
					try {
						if (!evaluatedValue.isPresent()) {
							throw new PolicyEvaluationException(
									CANNOT_ASSIGN_UNDEFINED_TO_A_VAL);
						}
						evaluationCtx.getVariableCtx().put(valueDefinition.getName(),
								evaluatedValue.get());
						return Boolean.TRUE;
					}
					catch (PolicyEvaluationException e) {
						LOGGER.error("Error in value definition evaluation: {}",
								e.getMessage());
						throw Exceptions.propagate(e);
					}
				}).onErrorResume(error -> Flux.error(Exceptions.unwrap(error)));
	}

	private static Flux<Boolean> evaluateCondition(Condition condition,
			EvaluationContext evaluationCtx) {
		return condition.getExpression().evaluate(evaluationCtx, true, Optional.empty())
				.map(statementResult -> {
					if (statementResult.isPresent()
							&& statementResult.get().isBoolean()) {
						return statementResult.get().asBoolean();
					}
					else {
						throw Exceptions.propagate(new PolicyEvaluationException(
								String.format(STATEMENT_NOT_BOOLEAN, statementResult)));
					}
				}).onErrorResume(error -> Flux.error(Exceptions.unwrap(error)));
	}

	private static Flux<Tuple2<Optional<ArrayNode>, Optional<ArrayNode>>> evaluateObligationsAndAdvice(
			Policy policy, EvaluationContext evaluationCtx) {
		Flux<Optional<ArrayNode>> obligationsFlux;
		if (policy.getObligation() != null) {
			final ArrayNode obligationArr = JSON.arrayNode();
			obligationsFlux = policy.getObligation().evaluate(evaluationCtx, true, Optional.empty())
					.doOnError(error -> LOGGER.error(OBLIGATIONS_ERROR, error))
					.map(obligation -> {
						obligation.ifPresent(obligationArr::add);
						return obligationArr.size() > 0 ? Optional.of(obligationArr)
								: Optional.empty();
					});
		}
		else {
			obligationsFlux = Flux.just(Optional.empty());
		}

		Flux<Optional<ArrayNode>> adviceFlux;
		if (policy.getAdvice() != null) {
			final ArrayNode adviceArr = JSON.arrayNode();
			adviceFlux = policy.getAdvice().evaluate(evaluationCtx, true, Optional.empty())
					.doOnError(error -> LOGGER.error(ADVICE_ERROR, error)).map(advice -> {
						advice.ifPresent(adviceArr::add);
						return adviceArr.size() > 0 ? Optional.of(adviceArr)
								: Optional.empty();
					});
		}
		else {
			adviceFlux = Flux.just(Optional.empty());
		}

		return Flux.combineLatest(obligationsFlux, adviceFlux, Tuples::of);
	}

	private static Flux<Optional<JsonNode>> evaluateTransformation(Policy policy,
			EvaluationContext evaluationCtx) {
		if (policy.getTransformation() != null) {
			return policy.getTransformation().evaluate(evaluationCtx, true, Optional.empty())
					.doOnError(error -> LOGGER.error(TRANSFORMATION_ERROR, error));
		}
		else {
			return Flux.just(Optional.empty());
		}
	}

	private static Map<String, String> fetchFunctionAndPipImports(SAPL saplDocument,
			FunctionContext functionCtx, AttributeContext attributeCtx)
			throws PolicyEvaluationException {
		final Map<String, String> imports = new HashMap<>();

		for (Import anImport : saplDocument.getImports()) {
			final String library = String.join(".", anImport.getLibSteps());
			if (anImport instanceof WildcardImport) {
				imports.putAll(fetchWildcardImports(imports, library,
						functionCtx.functionsInLibrary(library)));
				imports.putAll(fetchWildcardImports(imports, library,
						attributeCtx.findersInLibrary(library)));
			}
			else if (anImport instanceof LibraryImport) {
				String alias = ((LibraryImport) anImport).getLibAlias();
				imports.putAll(fetchLibraryImports(imports, library, alias,
						functionCtx.functionsInLibrary(library)));
				imports.putAll(fetchLibraryImports(imports, library, alias,
						attributeCtx.findersInLibrary(library)));
			}
			else {
				String functionName = anImport.getFunctionName();
				String fullyQualified = String.join(".", library, functionName);

				if (imports.containsKey(functionName)) {
					throw new PolicyEvaluationException(
							String.format(IMPORT_EXISTS, fullyQualified));
				}
				else if (!functionCtx.provides(fullyQualified)
						&& !attributeCtx.provides(fullyQualified)) {
					throw new PolicyEvaluationException(
							String.format(IMPORT_NOT_FOUND, fullyQualified));
				}
				imports.put(functionName, fullyQualified);
			}
		}

		return imports;
	}

	private static Map<String, String> fetchWildcardImports(Map<String, String> imports,
			String library, Collection<String> libraryItems)
			throws PolicyEvaluationException {

		final Map<String, String> returnImports = new HashMap<>(libraryItems.size(),
				1.0F);
		for (String name : libraryItems) {
			if (imports.containsKey(name)) {
				throw new PolicyEvaluationException(
						String.format(WILDCARD_IMPORT_EXISTS, library, name));
			}
			else {
				returnImports.put(name, String.join(".", library, name));
			}
		}
		return returnImports;
	}

	private static Map<String, String> fetchLibraryImports(Map<String, String> imports,
			String library, String alias, Collection<String> libraryItems)
			throws PolicyEvaluationException {

		final Map<String, String> returnImports = new HashMap<>(libraryItems.size(),
				1.0F);
		for (String name : libraryItems) {
			String key = String.join(".", alias, name);
			if (imports.containsKey(key)) {
				throw new PolicyEvaluationException(
						String.format(WILDCARD_IMPORT_EXISTS, library, name));
			}
			else {
				returnImports.put(key, String.join(".", library, name));
			}
		}
		return returnImports;
	}

	@Override
	public DocumentAnalysisResult analyze(String policyDefinition) {
		DocumentAnalysisResult result;
		try {
			Resource resource = loadAsResource(policyDefinition);
			SAPL sapl = (SAPL) resource.getContents().get(0);

			if (sapl.getPolicyElement() instanceof PolicySet) {
				PolicySet set = (PolicySet) sapl.getPolicyElement();
				result = new DocumentAnalysisResult(true, set.getSaplName(),
						DocumentType.POLICY_SET, "");
			}
			else {
				Policy policy = (Policy) sapl.getPolicyElement();
				result = new DocumentAnalysisResult(true, policy.getSaplName(),
						DocumentType.POLICY, "");
			}
		}
		catch (PolicyEvaluationException e) {
			result = new DocumentAnalysisResult(false, "", null, e.getMessage());
		}
		return result;
	}

	private void logResponse(Response r, SAPL saplDocument) {
		LOGGER.trace("| | |-- {}. Document: {} Cause: {}", r.getDecision(),
				saplDocument.getPolicyElement().getSaplName(), r);
		LOGGER.trace("| |");
	}

}
