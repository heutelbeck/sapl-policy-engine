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
package io.sapl.functions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Injector;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionException;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.BasicRelative;
import io.sapl.grammar.sapl.impl.StepResolver;
import io.sapl.grammar.services.SAPLGrammarAccess;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.ResultNode;
import reactor.core.Exceptions;

@FunctionLibrary(name = SelectionFunctionLibrary.NAME, description = SelectionFunctionLibrary.DESCRIPTION)
public class SelectionFunctionLibrary {

	private static final String BLOCKING_METHOD_CALL_RETURNED_NULL = "Blocking call of a reactive method returned null.";

	private static final String EQUAL = "equal";

	public static final String NAME = "selection";

	public static final String DESCRIPTION = "This library contains functions for selections on JSON objects.";

	private static final String APPLY_DOC = "Expects a STRUCTURE (JsonObject) and an EXPRESSION (a String representing a relative expression, starting with @). "
			+ "Evaluates the EXPRESSION relative to STRUCTURE and returns the result.";

	private static final String COUNT_DOC = "Expects a STRUCTURE (JsonObject) and an EXPRESSION (a String representing a relative expression, starting with @). "
			+ "Evaluates the EXPRESSION relative to STRUCTURE and returns the number of nodes selected.";

	private static final String MATCH_DOC = "Expects a STRUCTURE (JsonObject), a NEEDLE and a HAYSTACK (both Strings representing a relative expression, starting with @). "
			+ "Evaluates both the selections in HAYSTACK and NEEDLE relative to STRUCTURE. Returns true if everything selected by NEEDLE is equal to or part of any selection by HAYSTACK.";

	private static final String EQUAL_DOC = "Expects a STRUCTURE (JsonObject), a NEEDLE and a HAYSTACK (both Strings representing a relative expression, starting with @). "
			+ "Evaluates both the selections in HAYSTACK and NEEDLE relative to STRUCTURE. Returns true if every node selected by NEEDLE is equal to any node selected by HAYSTACK.";

	private static final String DEFAULT_POLICY = "policy:/default.sapl";

	private static final Injector INJECTOR = new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();

	@Function(docs = APPLY_DOC)
	public static Val apply(@JsonObject Val structure, @Text Val expression) throws FunctionException {
		BasicRelative relativeExpression = parseRelative(expression.get().asText());
		Val oStruct = structure;
		try {
			final ResultNode result = StepResolver
					.resolveSteps(oStruct, relativeExpression.getSteps(), null, false, oStruct).blockFirst();
			if (result == null) {
				throw new FunctionException(BLOCKING_METHOD_CALL_RETURNED_NULL);
			}
			return Val
					.of(result.asJsonWithoutAnnotations().orElseThrow(() -> new FunctionException("undefined result")));
		} catch (RuntimeException e) {
			throw new FunctionException(Exceptions.unwrap(e));
		}
	}

	@Function(docs = COUNT_DOC)
	public static Val count(@JsonObject Val structure, @Text Val expression) throws FunctionException {
		BasicRelative relativeExpression = parseRelative(expression.get().asText());
		Val oStruct = structure;
		try {
			final ResultNode result = StepResolver
					.resolveSteps(oStruct, relativeExpression.getSteps(), null, false, oStruct).blockFirst();
			if (result == null) {
				throw new FunctionException(BLOCKING_METHOD_CALL_RETURNED_NULL);
			} else if (result.isResultArray()) {
				return Val.of(((ArrayResultNode) result).getNodes().size());
			} else {
				return Val.of(1L);
			}
		} catch (RuntimeException e) {
			throw new FunctionException(Exceptions.unwrap(e));
		}
	}

	@Function(docs = MATCH_DOC)
	public static Val match(@JsonObject Val structure, @Text Val needle, @Text Val haystack) throws FunctionException {
		BasicRelative haystackExpression = parseRelative(haystack.get().asText());
		BasicRelative needleExpression = parseRelative(needle.get().asText());
		Val oStruct = structure;

		try {
			ResultNode haystackResult = StepResolver
					.resolveSteps(oStruct, haystackExpression.getSteps(), null, false, oStruct).blockFirst();
			ResultNode needleResult = StepResolver
					.resolveSteps(oStruct, needleExpression.getSteps(), null, false, oStruct).blockFirst();

			if (haystackResult == null || needleResult == null) {
				throw new PolicyEvaluationException(BLOCKING_METHOD_CALL_RETURNED_NULL);
			} else if (haystackResult.isNodeWithoutParent()) {
				return Val.ofTrue();
			} else if (needleResult.isNodeWithoutParent()) {
				return Val.ofFalse();
			} else if (!needleResult.isResultArray()) {
				return Val.of(inStructure((AbstractAnnotatedJsonNode) needleResult, haystackResult));
			}
			for (AbstractAnnotatedJsonNode node : (ArrayResultNode) needleResult) {
				if (!inStructure(node, haystackResult)) {
					return Val.ofFalse();
				}
			}
			return Val.ofTrue();
		} catch (PolicyEvaluationException e) {
			throw new FunctionException(e);
		} catch (RuntimeException e) {
			throw new FunctionException(Exceptions.unwrap(e));
		}
	}

	@Function(name = EQUAL, docs = EQUAL_DOC)
	public static Val areEqual(@JsonObject Val structure, @Text Val first, @Text Val second) throws FunctionException {
		BasicRelative firstExpression = parseRelative(second.get().asText());
		BasicRelative secondExpression = parseRelative(first.get().asText());
		Val oStruct = structure;

		try {
			ResultNode firstResult = StepResolver
					.resolveSteps(oStruct, firstExpression.getSteps(), null, false, oStruct).blockFirst();
			ResultNode secondResult = StepResolver
					.resolveSteps(oStruct, secondExpression.getSteps(), null, false, oStruct).blockFirst();

			if (firstResult == null || secondResult == null) {
				throw new PolicyEvaluationException(BLOCKING_METHOD_CALL_RETURNED_NULL);
			} else if (firstResult.isNodeWithoutParent() && secondResult.isNodeWithoutParent()) {
				return Val.ofTrue();
			} else if (!firstResult.isResultArray() && !secondResult.isResultArray()) {
				return Val.of(((AbstractAnnotatedJsonNode) firstResult)
						.sameReference((AbstractAnnotatedJsonNode) secondResult));
			} else if (firstResult.isResultArray() && secondResult.isResultArray()) {
				return Val.of(allNodesInList((ArrayResultNode) firstResult, (ArrayResultNode) secondResult)
						&& allNodesInList((ArrayResultNode) secondResult, (ArrayResultNode) firstResult));
			} else {
				return Val.ofFalse();
			}
		} catch (PolicyEvaluationException e) {
			throw new FunctionException(e);
		} catch (RuntimeException e) {
			throw new FunctionException(Exceptions.unwrap(e));
		}
	}

	private static boolean allNodesInList(Iterable<AbstractAnnotatedJsonNode> nodes,
			Iterable<AbstractAnnotatedJsonNode> list) throws PolicyEvaluationException {
		for (AbstractAnnotatedJsonNode node : nodes) {
			for (AbstractAnnotatedJsonNode listNode : list) {
				if (node.sameReference(listNode)) {
					return true;
				}
			}
			return false;
		}
		return true;
	}

	private static boolean inStructure(AbstractAnnotatedJsonNode needleResult, ResultNode haystackResult)
			throws PolicyEvaluationException {
		if (haystackResult instanceof AbstractAnnotatedJsonNode) {
			return inNode(needleResult, (AbstractAnnotatedJsonNode) haystackResult);
		} else {
			for (AbstractAnnotatedJsonNode haystackNode : (ArrayResultNode) haystackResult) {
				if (inNode(needleResult, haystackNode)) {
					return true;
				}
			}
			return false;
		}
	}

	private static boolean inNode(AbstractAnnotatedJsonNode needleResult, AbstractAnnotatedJsonNode haystackResult)
			throws PolicyEvaluationException {
		if (needleResult.sameReference(haystackResult)) {
			return true;
		} else if (needleResult.getParent().isDefined()) {
			return recursiveCheck(needleResult.getParent().get(), haystackResult.getNode().get());
		}
		return false;
	}

	private static boolean recursiveCheck(JsonNode needleParent, JsonNode haystack) {
		if (needleParent == haystack) {
			return true;
		}
		if (haystack.isArray()) {
			for (JsonNode node : (ArrayNode) haystack) {
				if (recursiveCheck(needleParent, node)) {
					return true;
				}
			}
		} else if (haystack.isObject()) {
			for (JsonNode node : (ObjectNode) haystack) {
				if (recursiveCheck(needleParent, node)) {
					return true;
				}
			}
		}
		return false;
	}

	private static BasicRelative parseRelative(String expression) throws FunctionException {
		XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		XtextResource resource = (XtextResource) resourceSet.createResource(URI.createFileURI(DEFAULT_POLICY));
		resource.setEntryPoint(INJECTOR.getInstance(SAPLGrammarAccess.class).getBasicRelativeRule());

		InputStream in = new ByteArrayInputStream(expression.getBytes(StandardCharsets.UTF_8));

		try {
			resource.load(in, resourceSet.getLoadOptions());
		} catch (IOException e) {
			throw new FunctionException(e);
		}
		return (BasicRelative) resource.getContents().get(0);
	}

}
