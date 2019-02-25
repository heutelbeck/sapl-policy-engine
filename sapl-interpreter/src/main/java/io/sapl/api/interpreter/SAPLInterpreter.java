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
package io.sapl.api.interpreter;

import java.io.InputStream;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import reactor.core.publisher.Flux;

public interface SAPLInterpreter {

	/**
	 * Method which analyzes a String containing a SAPL document.
	 *
	 * @param saplDefinition the String containing the SAPL document
	 * @return the document analysis result
	 */
	DocumentAnalysisResult analyze(String saplDefinition);

	/**
	 * Method which applies the SAPL parser to a String containing a SAPL document
	 * and generates the matching parse-tree.
	 *
	 * @param saplDefinition a String containing a SAPL document
	 * @return A parse tree of the document
	 * @throws PolicyEvaluationException in case an error occurs during parsing.
	 *                                   This may be either a syntax error or an IO
	 *                                   error.
	 */
	SAPL parse(String saplDefinition) throws PolicyEvaluationException;

	/**
	 * Method which applies the SAPL parser to an InputStream containing a SAPL
	 * document and generates the matching parse-tree.
	 *
	 * @param saplInputStream an InputStream containing a SAPL document
	 * @return A parse tree of the document
	 * @throws PolicyEvaluationException in case an error occurs during parsing.
	 *                                   This may be either a syntax error or an IO
	 *                                   error.
	 */
	SAPL parse(InputStream saplInputStream) throws PolicyEvaluationException;

	/**
	 * Method which evaluates a SAPL document (containing a policy set or policy)
	 * against a Request object within a given attribute context and function
	 * context and returns a {@link Flux} of {@link Response} objects.
	 *
	 * @param request         the Request object
	 * @param saplDocument    the SAPL document
	 * @param attributeCtx    the attribute context
	 * @param functionCtx     the function context
	 * @param systemVariables the system variables, a Map between the variable name
	 *                        and its value
	 * @return A {@link Flux} of {@link Response} objects.
	 */
	Flux<Response> evaluate(Request request, SAPL saplDocument, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables);

	/**
	 * Method which evaluates a String containing a SAPL document (containing a
	 * policy set or policy) against a Request object within a given attribute
	 * context and function context and returns a {@link Flux} of {@link Response}
	 * objects.
	 *
	 * @param request         the Request object
	 * @param saplDefinition  the String containing the SAPL document
	 * @param attributeCtx    the attribute context
	 * @param functionCtx     the function context
	 * @param systemVariables the system variables, a Map between the variable name
	 *                        and its value
	 * @return A {@link Flux} of {@link Response} objects.
	 */
	Flux<Response> evaluate(Request request, String saplDefinition, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables);

	/**
	 * Method which evaluates the body of a SAPL document (containing a policy set
	 * or a policy) against a Request object within a given attribute context and
	 * function context and returns a {@link Flux} of {@link Response} objects.
	 *
	 * @param request         the Request object
	 * @param saplDocument    the SAPL document
	 * @param attributeCtx    the attribute context
	 * @param functionCtx     the function context
	 * @param systemVariables the system variables, a Map between the variable name
	 *                        and its value
	 * @return A {@link Flux} of {@link Response} objects.
	 */
	Flux<Response> evaluateRules(Request request, SAPL saplDocument, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables);

	/**
	 * Method which evaluates a SAPL policy element (policy set or policy) against a
	 * Request object within a given AttributeContext and FunctionContext and
	 * returns a {@link Flux} of {@link Response} objects.. An import Map and custom
	 * variables can be provided.
	 *
	 * @param request         the Request object
	 * @param policyElement   the policy element
	 * @param attributeCtx    the attribute context
	 * @param functionCtx     the function context
	 * @param systemVariables the system variables, a Map between the variable name
	 *                        and its value
	 * @param variables       the custom variables, a Map between the variable name
	 *                        and its value
	 * @param imports         the imports Map, a Map between short names (String)
	 *                        and fully qualified names (String)
	 * @return A {@link Flux} of {@link Response} objects.
	 */
	Flux<Response> evaluateRules(Request request, PolicyElement policyElement, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables, Map<String, JsonNode> variables,
			Map<String, String> imports);

	/**
	 * Method which checks whether a SAPL document matches a Request by evaluating
	 * the document's target expression.
	 *
	 * No custom variables are provided and imports are extracted from the document.
	 *
	 * @param request         the Request to check against
	 * @param saplDocument    the SAPL document
	 * @param functionCtx     the function context, as functions can be used in the
	 *                        target expression
	 * @param systemVariables the system variables
	 * @return true if the target expression evaluates to true, otherwise false
	 * @throws PolicyEvaluationException in case there is an error while evaluating
	 *                                   the target expression
	 */
	boolean matches(Request request, SAPL saplDocument, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables) throws PolicyEvaluationException;

	/**
	 * Method which checks whether a policy element (policy set or policy) matches a
	 * Request by evaluating the element's target expression.
	 *
	 * An import mapping and custom variables can be provided.
	 *
	 * @param request         the Request to check against
	 * @param policyElement   the policy element (policy set or policy) to be
	 *                        checked
	 * @param functionCtx     the function context, as functions can be used in the
	 *                        target expression
	 * @param systemVariables the system variables
	 * @param variables       other variables
	 * @param imports         the imports Map, a Map between short names (String)
	 *                        and fully qualified names (String)
	 * @return true if the target expression evaluates to true, otherwise false
	 * @throws PolicyEvaluationException in case there is an error while evaluating
	 *                                   the target expression
	 */
	boolean matches(Request request, PolicyElement policyElement, FunctionContext functionCtx,
			Map<String, JsonNode> systemVariables, Map<String, JsonNode> variables, Map<String, String> imports)
			throws PolicyEvaluationException;

	/**
	 * Method which uses the import statements from a SAPL document to create a Map
	 * between simple function names (String) and fully qualified names (String)
	 * depending on the functions available in the function context.
	 *
	 * @param saplDocument the SAPL document
	 * @param functionCtx  the function context
	 * @return the Map from simple function names (String) to fully qualified names
	 *         (String)
	 * @throws PolicyEvaluationException in case there is an error while evaluating
	 *                                   the import statements and creating the Map
	 */
	Map<String, String> fetchFunctionImports(SAPL saplDocument, FunctionContext functionCtx)
			throws PolicyEvaluationException;
}
