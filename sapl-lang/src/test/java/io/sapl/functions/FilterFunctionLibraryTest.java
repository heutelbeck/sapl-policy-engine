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
package io.sapl.functions;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.sapl.api.interpreter.InitializationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;

class FilterFunctionLibraryTest {

	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	private static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
	private static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
	private static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());
	private static final FilterFunctionLibrary LIBRARY = new FilterFunctionLibrary();
	private static final EvaluationContext PDP_EVALUATION_CONTEXT = new EvaluationContext(ATTRIBUTE_CTX, FUNCTION_CTX,
			SYSTEM_VARIABLES);

	@BeforeEach
	void setUp() throws InitializationException {
		FUNCTION_CTX.loadLibrary(LIBRARY);
	}

	@Test
	void blackenTooManyArguments() {
		assertThrows(IllegalArgumentException.class, () -> {
			FilterFunctionLibrary.blacken(Val.of("abcde"), Val.of(2), Val.of(2), Val.of("x"), Val.of(2));
		});
	}

	@Test
	void blackenNoString() {
		assertThrows(IllegalArgumentException.class, () -> {
			FilterFunctionLibrary.blacken(Val.of(2));
		});
	}

	@Test
	void blackenReplacementNoString() {
		assertThrows(IllegalArgumentException.class, () -> {
			FilterFunctionLibrary.blacken(Val.of("abcde"), Val.of(2), Val.of(2), Val.of(2));
		});
	}

	@Test
	void blackenReplacementNegativeRight() {
		assertThrows(IllegalArgumentException.class, () -> {
			FilterFunctionLibrary.blacken(Val.of("abcde"), Val.of(2), Val.of(-2));
		});
	}

	@Test
	void blackenReplacementNegativeLeft() {
		assertThrows(IllegalArgumentException.class, () -> {
			FilterFunctionLibrary.blacken(Val.of("abcde"), Val.of(-2), Val.of(2));
		});
	}

	@Test
	void blackenReplacementRightNoNumber() {
		assertThrows(IllegalArgumentException.class, () -> {
			FilterFunctionLibrary.blacken(Val.of("abcde"), Val.of(2), Val.ofNull());
		});
	}

	@Test
	void blackenReplacementLeftNoNumber() {
		assertThrows(IllegalArgumentException.class, () -> {
			FilterFunctionLibrary.blacken(Val.of("abcde"), Val.ofNull(), Val.of(2));
		});
	}

	@Test
	void blackenWorking() {
		var given = Val.of("abcde");
		var discloseLeft = Val.of(1);
		var discloseRight = Val.of(1);
		var replacement = Val.of("*");
		var expected = Val.of("a***e");
		var actual = FilterFunctionLibrary.blacken(given, discloseLeft, discloseRight, replacement);
		assertThat("blacken function not working as expected", actual, equalTo(expected));
	}

	@Test
	void blackenWorkingAllVisible() {
		var text = Val.of("abcde");
		var discloseLeft = Val.of(3);
		var discloseRight = Val.of(3);
		var replacement = Val.of("*");

		var result = FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight, replacement);

		assertThat("blacken function not working as expected", result, equalTo(Val.of("abcde")));
	}

	@Test
	void blackenReplacementDefault() {
		var text = Val.of("abcde");
		var discloseLeft = Val.of(1);
		var discloseRight = Val.of(1);
		var result = FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight);
		assertThat("blacken function - default value for replacement not working as expected", result,
				equalTo(Val.of("aXXXe")));
	}

	@Test
	void blackenDiscloseRightDefault() {
		var text = Val.of("abcde");
		var discloseLeft = Val.of(2);

		var result = FilterFunctionLibrary.blacken(text, discloseLeft);
		assertThat("blacken function - default value for disclose left not working as expected", result,
				equalTo(Val.of("abXXX")));
	}

	@Test
	void blackenDiscloseLeftDefault() {
		var text = Val.of("abcde");
		var result = FilterFunctionLibrary.blacken(text);
		assertThat("blacken function - default value for disclose left not working as expected", result,
				equalTo(Val.of("XXXXX")));
	}

	@Test
	void blackenInPolicy() throws JsonProcessingException {
		var authzSubscription = MAPPER.readValue("{ \"resource\": {	\"array\": [ null, true ], \"key1\": \"abcde\" } }",
				AuthorizationSubscription.class);
		var policyDefinition = "policy \"test\"	permit transform resource |- { @.key1 : filter.blacken(1) }";
		var expectedResource = MAPPER.readValue("{	\"array\": [ null, true ], \"key1\": \"aXXXX\" }", JsonNode.class);
		var expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
				Optional.empty(), Optional.empty());
		var authzDecision = INTERPRETER.evaluate(authzSubscription, policyDefinition, PDP_EVALUATION_CONTEXT)
				.blockFirst();
		assertThat("builtin function blacken() not working as expected", authzDecision, equalTo(expectedAuthzDecision));
	}

	@Test
	void replace() {
		var result = FilterFunctionLibrary.replace(Val.ofNull(), Val.of(1));
		assertThat("replace function not working as expected", result, equalTo(Val.of(1)));
	}

	@Test
	void replaceInPolicy() throws JsonProcessingException {
		var authzSubscription = MAPPER.readValue("{ \"resource\": {	\"array\": [ null, true ], \"key1\": \"abcde\" } }",
				AuthorizationSubscription.class);
		var policyDefinition = "policy \"test\" permit transform resource |- { @.array[1] : filter.replace(\"***\"), @.key1 : filter.replace(null) }";
		var expectedResource = MAPPER.readValue("{	\"array\": [ null, \"***\" ], \"key1\": null }", JsonNode.class);
		var expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
				Optional.empty(), Optional.empty());
		var authzDecision = INTERPRETER.evaluate(authzSubscription, policyDefinition, PDP_EVALUATION_CONTEXT)
				.blockFirst();
		assertThat("builtin function replace() not working as expected", authzDecision, equalTo(expectedAuthzDecision));
	}
}
