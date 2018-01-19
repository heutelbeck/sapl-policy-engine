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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
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
import io.sapl.grammar.sapl.Expression;
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

/**
 * The default implementation of the SAPLInterpreter interface.
 */
@Slf4j
public class DefaultSAPLInterpreter implements SAPLInterpreter {

	private static final String PERMIT = "permit";
	private static final String DUMMY_RESOURCE_URI = "policy:/apolicy.sapl";

	static final String FILTER_REMOVE = "remove";

	static final String PARSING_ERRORS = "Parsing errors: %s";
	static final String CONDITION_NOT_BOOLEAN = "Evaluation error: Target condition must evaluate to a boolean value, but was: '%s'.";
	static final String NO_TARGET_MATCH = "Target not matching.";
	static final String STATEMENT_NOT_BOOLEAN = "Evaluation error: Statement must evaluate to a boolean value, but was: '%s'.";
	static final String UNKNOWN_STATEMENT = "Evaluation error: Encountered unknown statement type: '%s'.";
	static final String VARIABLE_ALREADY_DEFINED = "Evaluation error: The variable %s has already been defined. Redefinition is not allowed.";
	static final String TRANSFORMATION_OBLIGATION_ADVICE_ERROR = "Error occurred while evaluating transformation/obligation/advice.";
	static final String IMPORT_NOT_FOUND = "Import '%s' was not found.";
	static final String IMPORT_EXISTS = "An import for name '%s' already exists.";
	static final String WILDCARD_IMPORT_EXISTS = "Wildcard import of '%s' not possible as an import for name '%s' already exists.";

	private static final Injector INJECTOR = new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Override
	public SAPL parse(String saplDefinition) throws PolicyEvaluationException {
		return (SAPL) loadAsResource(saplDefinition).getContents().get(0);
	}

	@Override
	public SAPL parse(InputStream saplInputStream) throws PolicyEvaluationException {
		return (SAPL) loadAsResource(saplInputStream).getContents().get(0);
	}

	private static Resource loadAsResource(InputStream policyInputStream) throws PolicyEvaluationException {
		XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		Resource resource = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

		try {
			resource.load(policyInputStream, resourceSet.getLoadOptions());
		} catch (IOException | WrappedException e) {
			throw new PolicyEvaluationException(String.format(PARSING_ERRORS, resource.getErrors()), e);
		}

		if (!resource.getErrors().isEmpty()) {
			throw new PolicyEvaluationException(String.format(PARSING_ERRORS, resource.getErrors()));
		}
		return resource;
	}

	private static Resource loadAsResource(String saplDefinition) throws PolicyEvaluationException {
		XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		Resource resource = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));
		log.trace("policy : {}", saplDefinition);

		try (InputStream in = new ByteArrayInputStream(saplDefinition.getBytes(StandardCharsets.UTF_8))) {
			resource.load(in, resourceSet.getLoadOptions());
		} catch (IOException e) {
			throw new PolicyEvaluationException(String.format(PARSING_ERRORS, resource.getErrors()), e);
		}

		if (!resource.getErrors().isEmpty()) {
			throw new PolicyEvaluationException(String.format(PARSING_ERRORS, resource.getErrors()));
		}
		return resource;
	}

	@Override
	public boolean matches(Request request, SAPL saplDocument, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables) throws PolicyEvaluationException {
		Map<String, String> imports = fetchFunctionImports(saplDocument, functionCtx);
		return matches(request, saplDocument.getPolicyElement(), functionCtx, systemVariables, null, imports);
	}

	@Override
	public boolean matches(Request request, PolicyElement policyElement, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables, Map<String, JsonNode> variables, Map<String, String> imports)
			throws PolicyEvaluationException {
		VariableContext variableCtx = new VariableContext(request, systemVariables);
		if (variables != null) {
			for (Map.Entry<String, JsonNode> entry : variables.entrySet()) {
				variableCtx.put(entry.getKey(), entry.getValue());
			}
		}
		EvaluationContext evaluationCtx = new EvaluationContext(functionCtx, variableCtx, imports);

		if (policyElement.getTargetExpression() == null) {
			return true;
		} else {
			JsonNode expressionResult = policyElement.getTargetExpression().evaluate(evaluationCtx, false, null);

			if (expressionResult.isBoolean()) {
				return ((BooleanNode) expressionResult).asBoolean();
			} else {
				throw new PolicyEvaluationException(
						String.format(CONDITION_NOT_BOOLEAN, expressionResult.getNodeType()));
			}
		}
	}

	@Override
	public Response evaluate(Request request, String saplDefinition, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables) {
		try {
			SAPL saplDocument = parse(saplDefinition);
			return evaluate(request, saplDocument, attributeCtx, functionCtx, systemVariables);
		} catch (PolicyEvaluationException e) {
			log.trace(e.getMessage());
			return Response.indeterminate();
		}
	}

	@Override
	public Response evaluate(Request request, SAPL saplDocument, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables) {
		try {
			if (matches(request, saplDocument, functionCtx, systemVariables)) {
				return evaluateRules(request, saplDocument, attributeCtx, functionCtx, systemVariables);
			} else {
				log.trace(NO_TARGET_MATCH);
				return Response.notApplicable();
			}
		} catch (PolicyEvaluationException e) {
			log.trace("Policy evaluation failed: {}", e.getMessage());
			return Response.indeterminate();
		}
	}

	@Override
	public Response evaluateRules(Request request, SAPL saplDocument, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables) {

		Map<String, String> imports;
		try {
			imports = fetchFunctionAndPipImports(saplDocument, functionCtx, attributeCtx);
		} catch (PolicyEvaluationException e) {
			return Response.indeterminate();
		}
		return evaluateRules(request, saplDocument.getPolicyElement(), attributeCtx, functionCtx, systemVariables, null,
				imports);
	}

	@Override
	public Response evaluateRules(Request request, PolicyElement policyElement, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables, Map<String, JsonNode> variables,
			Map<String, String> imports) {

		if (policyElement instanceof PolicySet) {
			return evaluatePolicySetRules(request, (PolicySet) policyElement, attributeCtx, functionCtx,
					systemVariables, imports);
		} else {
			return evaluatePolicyRules(request, (Policy) policyElement, attributeCtx, functionCtx, systemVariables,
					variables, imports);
		}
	}

	private Response evaluatePolicyRules(Request request, Policy policy, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables, Map<String, JsonNode> variables,
			Map<String, String> imports) {
		EvaluationContext evaluationCtx;
		try {
			VariableContext variableCtx = new VariableContext(request, systemVariables);
			if (variables != null) {
				for (Map.Entry<String, JsonNode> entry : variables.entrySet()) {
					variableCtx.put(entry.getKey(), entry.getValue());
				}
			}
			evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, variableCtx, imports);
		} catch (PolicyEvaluationException e) {
			return Response.indeterminate();
		}

		Decision entitlement = PERMIT.equals(policy.getEntitlement()) ? Decision.PERMIT : Decision.DENY;

		Decision decision = entitlement;
		if (policy.getBody() != null) {
			decision = evaluateBody(entitlement, policy.getBody(), evaluationCtx);
		}

		Optional<ArrayNode> obligation = Optional.empty();
		Optional<ArrayNode> advice = Optional.empty();
		if (decision == Decision.PERMIT || decision == Decision.DENY) {
			try {
				obligation = evaluateObligation(policy, evaluationCtx);
				advice = evaluateAdvice(policy, evaluationCtx);
			} catch (PolicyEvaluationException e) {
				log.trace(TRANSFORMATION_OBLIGATION_ADVICE_ERROR, e);
				return Response.indeterminate();
			}
		}

		if (decision == Decision.PERMIT) {
			Optional<JsonNode> returnedResource;
			try {
				returnedResource = evaluateTransformation(request.getResource(), policy, evaluationCtx);
			} catch (PolicyEvaluationException e) {
				log.trace(TRANSFORMATION_OBLIGATION_ADVICE_ERROR, e);
				return Response.indeterminate();
			}
			if (returnedResource.isPresent()) {
				return new Response(decision, Optional.of(returnedResource.get()), obligation, advice);
			}
		}

		return new Response(decision, Optional.empty(), obligation, advice);
	}

	private Optional<ArrayNode> evaluateObligation(Policy policy, EvaluationContext evaluationCtx) throws PolicyEvaluationException {
		if (policy.getObligation() != null) {
			ArrayNode obligation = JSON.arrayNode();
			obligation.add(policy.getObligation().evaluate(evaluationCtx, true, null));
			return Optional.of(obligation);
		}
		return Optional.empty();
	}
	
	private Optional<ArrayNode> evaluateAdvice(Policy policy, EvaluationContext evaluationCtx) throws PolicyEvaluationException {
		if (policy.getAdvice() != null) {
			ArrayNode advice = JSON.arrayNode();
			advice.add(policy.getAdvice().evaluate(evaluationCtx, true, null));
			return Optional.of(advice);
		}
		return Optional.empty();
	}

	@Override
	public Map<String, String> fetchFunctionImports(SAPL saplDocument, FunctionContext functionCtx)
			throws PolicyEvaluationException {

		Map<String, String> imports = new HashMap<>();

		for (Import anImport : saplDocument.getImports()) {
			String library = String.join(".", anImport.getLibSteps());

			if (anImport instanceof WildcardImport) {
				imports.putAll(fetchWildcardImports(imports, library, functionCtx.functionsInLibrary(library)));
			} else if (anImport instanceof LibraryImport) {
				String alias = ((LibraryImport) anImport).getLibAlias();
				imports.putAll(fetchLibraryImports(imports, library, alias, functionCtx.functionsInLibrary(library)));
			} else {
				String functionName = anImport.getFunctionName();
				String fullyQualified = String.join(".", library, functionName);

				if (imports.containsKey(anImport.getFunctionName())) {
					throw new PolicyEvaluationException(String.format(IMPORT_EXISTS, fullyQualified));
				}
				imports.put(functionName, fullyQualified);
			}
		}

		return imports;
	}

	private static Map<String, String> fetchFunctionAndPipImports(SAPL saplDocument, FunctionContext functionCtx,
			AttributeContext attributeCtx) throws PolicyEvaluationException {

		Map<String, String> imports = new HashMap<>();

		for (Import anImport : saplDocument.getImports()) {
			String library = String.join(".", anImport.getLibSteps());

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

				if (imports.containsKey(anImport.getFunctionName())) {
					throw new PolicyEvaluationException(String.format(IMPORT_EXISTS, fullyQualified));
				} else if (!functionCtx.provides(fullyQualified) && !attributeCtx.provides(fullyQualified)) {
					throw new PolicyEvaluationException(String.format(IMPORT_NOT_FOUND, fullyQualified));
				}
				imports.put(functionName, fullyQualified);
			}
		}

		return imports;
	}

	private static Map<String, String> fetchWildcardImports(Map<String, String> imports, String library,
			Collection<String> libraryItems) throws PolicyEvaluationException {
		Map<String, String> returnImports = new HashMap<>();

		for (String name : libraryItems) {
			if (imports.containsKey(name)) {
				throw new PolicyEvaluationException(String.format(WILDCARD_IMPORT_EXISTS, library, name));
			} else {
				returnImports.put(name, String.join(".", library, name));
			}
		}

		return returnImports;
	}

	private static Map<String, String> fetchLibraryImports(Map<String, String> imports, String library, String alias,
			Collection<String> libraryItems) throws PolicyEvaluationException {
		Map<String, String> returnImports = new HashMap<>();

		for (String name : libraryItems) {
			String key = String.join(".", alias, name);
			if (imports.containsKey(key)) {
				throw new PolicyEvaluationException(String.format(WILDCARD_IMPORT_EXISTS, library, name));
			} else {
				returnImports.put(key, String.join(".", library, name));
			}
		}

		return returnImports;
	}

	private static Optional<JsonNode> evaluateTransformation(JsonNode resource, Policy policy,
			EvaluationContext evaluationCtx) throws PolicyEvaluationException {
		Expression transformationExpression = policy.getTransformation();
		if (transformationExpression != null) {
			return Optional.of(transformationExpression.evaluate(evaluationCtx, true, null));
		} else {
			return Optional.empty();
		}
	}

	private static Decision evaluateBody(Decision response, PolicyBody body, EvaluationContext evaluationCtx) {
		Decision result = response;
		try {
			for (Statement statement : body.getStatements()) {
				if (!evaluateStatement(evaluationCtx, statement)) {
					result = Decision.NOT_APPLICABLE;
					break;
				}
			}
		} catch (PolicyEvaluationException e) {
			result = Decision.INDETERMINATE;
		}
		return result;
	}

	private static boolean evaluateStatement(EvaluationContext evaluationCtx, Statement statement)
			throws PolicyEvaluationException {
		if (statement instanceof ValueDefinition) {
			evaluateValueDefinition(evaluationCtx, (ValueDefinition) statement);
			return true;
		} else {
			JsonNode statementResult = ((Condition) statement).getExpression().evaluate(evaluationCtx, true, null);
			if (statementResult.isBoolean()) {
				return ((BooleanNode) statementResult).asBoolean();
			} else {
				throw new PolicyEvaluationException(
						String.format(STATEMENT_NOT_BOOLEAN, statementResult.getNodeType()));
			}
		}
	}

	private static void evaluateValueDefinition(EvaluationContext evaluationCtx, ValueDefinition statement)
			throws PolicyEvaluationException {
		evaluationCtx.getVariableCtx().put(statement.getName(),
				statement.getEval().evaluate(evaluationCtx, true, null));
	}

	private Response evaluatePolicySetRules(Request request, PolicySet policySet, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables, Map<String, String> imports) {
		Map<String, JsonNode> variables = new HashMap<>();
		try {
			VariableContext variableCtx = new VariableContext(request, systemVariables);
			EvaluationContext evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, variableCtx, imports);
			for (ValueDefinition valueDefinition : policySet.getValueDefinitions()) {
				JsonNode result = valueDefinition.getEval().evaluate(evaluationCtx, true, null);
				evaluationCtx.getVariableCtx().put(valueDefinition.getName(), result);
				variables.put(valueDefinition.getName(), result);
			}
		} catch (PolicyEvaluationException e) {
			return Response.indeterminate();
		}

		PolicyCombinator combinator;
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

		return combinator.combinePolicies(policySet.getPolicies(), request, attributeCtx, functionCtx, systemVariables,
				variables, imports);
	}

	@Override
	public DocumentAnalysisResult analyze(String policyDefinition) {
		DocumentAnalysisResult result = new DocumentAnalysisResult(false, "", null, "not a valid policy document");
		try {
			Resource resource = loadAsResource(policyDefinition);
			SAPL sapl = (SAPL) resource.getContents().get(0);

			if (sapl.getPolicyElement() instanceof PolicySet) {
				PolicySet set = (PolicySet) sapl.getPolicyElement();
				result = new DocumentAnalysisResult(true, set.getSaplName(), DocumentType.POLICY_SET, "");
			} else {
				Policy policy = (Policy) sapl.getPolicyElement();
				result = new DocumentAnalysisResult(true, policy.getSaplName(), DocumentType.POLICY, "");
			}
		} catch (PolicyEvaluationException e) {
			result = new DocumentAnalysisResult(false, "", null, e.getMessage());
		}
		return result;
	}

}
