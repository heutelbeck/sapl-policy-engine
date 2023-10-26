/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter;

import static io.sapl.api.pdp.AuthorizationDecision.DENY;
import static io.sapl.api.pdp.AuthorizationDecision.INDETERMINATE;
import static io.sapl.api.pdp.AuthorizationDecision.NOT_APPLICABLE;
import static io.sapl.api.pdp.AuthorizationDecision.PERMIT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.TestPIP;
import reactor.test.StepVerifier;

class DefaultSAPLInterpreterTests {

    private static final String AUTHZ_SUBSCRIPTION_JSON = """
            {
              "subject" : {
                "id" : "1234",
                "organizationId" : "5678",
                "isActive" : true,
                "granted_authorities" : {
                  "roles" : [ "USER", "ACCOUNTANT" ],
                  "groups" : [ "OPERATORS", "DEVELOPERS" ]
                }
              },
              "action" : {
                "verb" : "withdraw_funds",
                "parameters" : [ 200.0 ]
              },
              "resource" : {
                "url" : "http://api.bank.com/accounts/12345",
                "id" : "9012",
                "emptyArray" : [ ],
                "textArray" : [ "one", "two" ],
                "emptyObject" : { },
                "objectArray" : [ {
                  "id" : "1",
                  "name" : "one"
                }, {
                  "id" : "2",
                  "name" : "two"
                } ]
              },
              "environment" : {
                "ipAddress" : "10.10.10.254",
                "year" : 2016
              }
            }
            """;

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private static AuthorizationSubscription authzSubscription;

    private static AnnotationAttributeContext attributeCtx;

    private static AnnotationFunctionContext functionCtx;

    private static Map<String, JsonNode> variables;

    @BeforeAll
    static void beforeAll() throws JsonProcessingException, InitializationException {
        authzSubscription = MAPPER.readValue(AUTHZ_SUBSCRIPTION_JSON, AuthorizationSubscription.class);
        attributeCtx      = new AnnotationAttributeContext();
        attributeCtx.loadPolicyInformationPoint(new TestPIP());
        functionCtx = new AnnotationFunctionContext();
        functionCtx.loadLibrary(new SimpleFunctionLibrary());
        functionCtx.loadLibrary(new FilterFunctionLibrary());
        functionCtx.loadLibrary(new StandardFunctionLibrary());
        variables = new HashMap<String, JsonNode>();

    }

    @Test
    void parseTest() {
        var policyDocument = "policy \"test\" permit";
        assertDoesNotThrow(() -> INTERPRETER.parse(policyDocument));
    }

    @Test
    void parseTestValidationFailsOnLazyBooleanOperatorsInTarget() {
        var policyDocument = "policy \"test\"  permit true && false";
        assertThrows(PolicyEvaluationException.class, () -> INTERPRETER.parse(policyDocument));
    }

    @Test
    void brokenInputStreamTest() {
        var brokenInputStream = mock(InputStream.class);
        assertThrows(PolicyEvaluationException.class, () -> INTERPRETER.parse(brokenInputStream));
    }

    @Test
    void parseTestWithError() {
        var policyDocument = "xyz";
        assertThrows(PolicyEvaluationException.class, () -> INTERPRETER.parse(policyDocument));
    }

    @Test
    void analyzePolicySet() {
        var policyDefinition = "set \"test\" deny-overrides policy \"xx\" permit";
        var expected         = new DocumentAnalysisResult(true, "test", DocumentType.POLICY_SET, "");
        assertThat(INTERPRETER.analyze(policyDefinition), is(expected));
    }

    @Test
    void analyzePolicy() {
        var policyDefinition = "policy \"test\" permit";
        var expected         = new DocumentAnalysisResult(true, "test", DocumentType.POLICY, "");
        assertThat(INTERPRETER.analyze(policyDefinition), is(expected));
    }

    @Test
    void analyzeException() {
        assertThat(INTERPRETER.analyze("xyz").isValid(), is(false));
    }

    @Test
    void syntaxError() {
        var policyDefinition = "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432 ";
        assertThrows(PolicyEvaluationException.class, () -> INTERPRETER.parse(policyDefinition));
    }

    @Test
    void transformation() {
        var policyDefinition = "policy \"test\" permit transform null";
        var expected         = Optional.of(JSON.nullNode());
        StepVerifier
                .create(INTERPRETER.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, variables))
                .assertNext(actual -> assertThat(actual.getResource(), is(expected))).verifyComplete();
    }

    @Test
    void obligation() {
        var policyDefinition   = "policy \"test\" permit obligation null";
        var expectedObligation = JSON.arrayNode();
        expectedObligation.add(JSON.nullNode());
        var expected = Optional.of(expectedObligation);
        StepVerifier
                .create(INTERPRETER.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, variables))
                .assertNext(actual -> assertThat(actual.getObligations(), is(expected))).verifyComplete();
    }

    @Test
    void advice() {
        var policyDefinition = "policy \"test\" permit advice null";
        var expectedAdvice   = JSON.arrayNode();
        expectedAdvice.add(JSON.nullNode());
        var expected = Optional.of(expectedAdvice);
        StepVerifier
                .create(INTERPRETER.evaluate(authzSubscription, policyDefinition, attributeCtx, functionCtx, variables))
                .assertNext(actual -> assertThat(actual.getAdvice(), is(expected))).verifyComplete();
    }

    private static Stream<Arguments> documentTestCases() {
        // @formatter:off
		return Stream.of(
				// permitAll
				Arguments.of("policy \"test\" permit", PERMIT),
				// denyAll
				Arguments.of("policy \"test\" deny", DENY),
				// permitFalse
				Arguments.of("policy \"test\" permit false", NOT_APPLICABLE),
				// permitParseError
				Arguments.of("--- policy \"test\" permit ---", INDETERMINATE),
				// targetNotBoolean
				Arguments.of("policy \"test\" permit 20", INDETERMINATE),
				// processParsedEmptyArray
				Arguments.of("policy \"test\" permit resource.emptyArray == []", PERMIT),
				// processParsedEmptyObject
				Arguments.of("policy \"test\" permit resource.emptyObject == {}", PERMIT),
				// evaluateWorkingBodyTrue
				Arguments.of("policy \"test\" permit subject.isActive == true where true;", PERMIT),
				// evaluateWorkingBodyFalse
				Arguments.of("policy \"test\" permit subject.isActive == true where false;", NOT_APPLICABLE),
				// evaluateWorkingBodyError
				Arguments.of("policy \"test\" permit subject.isActive == true where 4 && true;", INDETERMINATE),
				// echoAttributeFinder
				Arguments.of(
						"policy \"test\" permit where var variable = [1,2,3]; variable.<sapl.pip.test.echo> == variable;",
						PERMIT),
				// attributeFinderInTarget
				Arguments.of("policy \"test\" permit \"test\".<sapl.pip.test.echo> == \"test\"", INDETERMINATE),
				// attributeFinderWithArgumentsTest
				Arguments.of(
						"policy \"test\" permit where var variable = \"hello\"; variable.<sapl.pip.test.echoRepeat(2)> == (variable + variable);",
						PERMIT),
				// bodyStatementNotBoolean
				Arguments.of("policy \"test\" permit where null;", INDETERMINATE),
				// variableRedefinition
				Arguments.of("policy \"test\" permit where var test = null; var test = 2; test == 2;", PERMIT),
				// unboundVariable
				Arguments.of("policy \"test\" permit where variable;", INDETERMINATE),
				// functionCall
				Arguments.of("policy \"test\" permit where simple.append(\"a\",\"b\") == \"ab\";", PERMIT),
				// functionCallImport
				Arguments.of("import simple.append policy \"test\" permit where append(\"a\",\"b\") == \"ab\";",
						PERMIT),
				// functionCallError
				Arguments.of("policy \"test\" permit where append(null) == \"ab\";", INDETERMINATE),
				// keyStepOnUndefined
				Arguments.of("policy \"test\" permit where undefined.key == undefined;", PERMIT),
				// recursiveKeyStepOnUndefined
				Arguments.of("policy \"test\" permit where undefined..key == [];", PERMIT),
				// keyStepOnString
				Arguments.of("policy \"test\" permit where \"foo\".key == undefined;", PERMIT),
				// recursiveKeyStepOnString
				Arguments.of("policy \"test\" permit where \"foo\"..key == [];", PERMIT),
				// keyStepWithUnknownKey
				Arguments.of("policy \"test\" permit where {\"attr\": 1}.key == undefined;", PERMIT),
				// recursiveKeyStepWithUnknownKey
				Arguments.of("policy \"test\" permit where {\"attr\": 1}..key == [];", PERMIT),
				// keyStepWithKnownKey
				Arguments.of("policy \"test\" permit where {\"key\": 1}.key == 1;", PERMIT),
				// recursiveKeyStepWithKnownKey
				Arguments.of("policy \"test\" permit where {\"key\": 1}..key == [1];", PERMIT),
				// keyStepWithKnownKeyInChildObject
				Arguments.of("policy \"test\" permit where {\"attr\": {\"key\": 1}}.key == undefined;", PERMIT),
				// recursiveKeyStepWithKnownKeyInChildObject
				Arguments.of("policy \"test\" permit where {\"attr\": {\"key\": 1}}..key == [1];", PERMIT),
				// keyStepWithKnownKeyInParentAndChildObject
				Arguments.of("policy \"test\" permit where {\"key\": {\"key\": 1}}.key == {\"key\": 1};", PERMIT),
				// recursiveKeyStepWithKnownKeyInParentAndChildObject
				Arguments.of("policy \"test\" permit where {\"key\": {\"key\": 1}}..key == [{\"key\": 1}, 1];", PERMIT),
				// recursiveKeyStepComplex
				Arguments.of(
						"policy \"test\" permit where {\"key\": {\"key\": [{\"key\": 1}, {\"key\": 2}]}}..key == [{\"key\": [{\"key\": 1}, {\"key\": 2}]}, [{\"key\": 1}, {\"key\": 2}], 1, 2];",
						PERMIT),
				// indexStepOnUndefined
				Arguments.of("policy \"test\" permit where var error = undefined[0]; true == true;", INDETERMINATE),
				// recursiveIndexStepOnUndefined
				Arguments.of("policy \"test\" permit where undefined..[0] == [];", PERMIT),
				// indexStepOnString
				Arguments.of("policy \"test\" permit where var error = \"foo\"[0]; true == true;", INDETERMINATE),
				// indexStepOnArrayWithUndefined
				Arguments.of("policy \"test\" permit where var error = [undefined][0]; true == true;", INDETERMINATE),
				// recursiveIndexStepOnArrayWithUndefined
				Arguments.of("policy \"test\" permit where var error = [undefined]..[0]; error == [];", PERMIT),
				// indexStepOnArrayWithIndexOutOfBounds
				Arguments.of("policy \"test\" permit where var error = [0,1][2]; true == true;", INDETERMINATE),
				// recursiveIndexStepOnArrayWithIndexOutOfBounds
				Arguments.of("policy \"test\" permit where [0,1]..[2] == [];", PERMIT),
				// indexStepOnArrayWithValidIndex
				Arguments.of("policy \"test\" permit where [0,1][1] == 1;", PERMIT),
				// recursiveIndexStepOnArrayWithValidIndex
				Arguments.of("policy \"test\" permit where [0,1]..[1] == [1];", PERMIT),
				// recursiveIndexStepOnArrayWithChildArray1
				Arguments.of("policy \"test\" permit where [[0,1], 2]..[1] == [2, 1];", PERMIT),
				// recursiveIndexStepOnArrayWithChildArray2
				Arguments.of("policy \"test\" permit where [0, [0,1]]..[1] == [[0,1], 1];", PERMIT),
				// recursiveIndexStepOnArrayWithChildArray3
				Arguments.of("policy \"test\" permit where [0, [0, 1, 2]]..[2] == [2];", PERMIT),
				// recursiveIndexStepComplex
				Arguments.of(
						"policy \"test\" permit where [0, [{\"text\": 1, \"arr\": [3, 4, 5]}, 1, 2]]..[2] == [2, 5];",
						PERMIT),
				// wildcardStepOnUndefined
				Arguments.of("policy \"test\" permit where var error = undefined.*; true == true;", INDETERMINATE),
				// recursiveWildcardStepOnUndefined
				Arguments.of("policy \"test\" permit where var error = undefined..*; true == true;", INDETERMINATE),
				// wildcardStepOnString
				Arguments.of("policy \"test\" permit where var error = \"foo\".*; true == true;", INDETERMINATE),
				// recursiveWildcardStepOnString
				Arguments.of("policy \"test\" permit where \"foo\"..* == [];", PERMIT),
				// wildcardStepOnEmptyObject
				Arguments.of("policy \"test\" permit where {}.* == [];", PERMIT),
				// recursiveWildcardStepOnEmptyObject
				Arguments.of("policy \"test\" permit where {}..* == [];", PERMIT),
				// wildcardStepOnEmptyArray
				Arguments.of("policy \"test\" permit where [].* == [];", PERMIT),
				// recursiveWildcardStepOnEmptyArray
				Arguments.of("policy \"test\" permit where []..* == [];", PERMIT),
				// wildcardStepOnSimpleObject
				Arguments.of("policy \"test\" permit where {\"key\": 1, \"attr\": 2}.* == [1, 2];", PERMIT),
				// recursiveWildcardStepOnSimpleObject
				Arguments.of("policy \"test\" permit where {\"key\": 1, \"attr\": 2}..* == [1, 2];", PERMIT),
				// wildcardStepOnSimpleArray
				Arguments.of("policy \"test\" permit where [1, 2].* == [1, 2];", PERMIT),
				// recursiveWildcardStepOnSimpleArray
				Arguments.of("policy \"test\" permit where [1, 2]..* == [1, 2];", PERMIT),
				// wildcardStepOnHierarchicalObject
				Arguments.of("policy \"test\" permit where {\"attr\": {\"key\": 1}}.* == [{\"key\": 1}];", PERMIT),
				// recursiveWildcardStepOnHierarchicalObject
				Arguments.of("policy \"test\" permit where {\"attr\": {\"key\": 1}}..* == [{\"key\": 1}, 1];", PERMIT),
				// wildcardStepOnHierarchicalArray
				Arguments.of("policy \"test\" permit where [0, [1, 2]].* == [0, [1, 2]];", PERMIT),
				// recursiveWildcardStepOnHierarchicalArray
				Arguments.of("policy \"test\" permit where [0, [1, 2]]..* == [0, [1, 2], 1, 2];", PERMIT),
				// recursiveWildcardStepComplex
				Arguments.of(
						"policy \"test\" permit where [0, [{\"text\": 1, \"arr\": [3, 4, 5]}, 1, 2], 6]..* == [0, [{\"text\": 1, \"arr\": [3, 4, 5]}, 1, 2], {\"text\": 1, \"arr\": [3, 4, 5]}, 1, [3, 4, 5], 3, 4, 5, 1, 2, 6];",
						PERMIT),
				// conditionStepOnEmptyArray
				Arguments.of("policy \"test\" permit where [][?(@ == undefined)] == [];", PERMIT),
				// conditionStepOnEmptyObject
				Arguments.of("policy \"test\" permit where {}[?(@ == undefined)] == [];", PERMIT),
				// functionCallOnObjectNodeWithRelativeArguments
				Arguments.of(
						"import simple.append import filter.remove policy \"test\" permit where {\"name\": \"Ben\", \"origin\": \"Berlin\"} |- {@.name : append(\" from \", @.origin), @.origin : remove} == {\"name\": \"Ben from Berlin\"};",
						PERMIT),
				// functionCallOnEachArrayItemWithRelativeArguments
				Arguments.of(
						"import simple.* import filter.* policy \"test\" permit where [{\"name\": \"Hans\", \"origin\": \"Hagen\"}, {\"name\": \"Felix\", \"origin\": \"Zürich\"}] |- { @..name : append(\" aus \", @.origin),  @..origin : remove} == [{\"name\": \"Hans aus Hagen\"}, {\"name\": \"Felix aus Zürich\"}];",
						PERMIT),
				// filterExtended
				Arguments.of(
						"policy \"test\" permit transform [\"foo\", \"bars\"] |- {each @.<sapl.pip.test.echo> : simple.length}",
						INDETERMINATE),
				// subTemplateOnEmptyArray
				Arguments.of("policy \"test\" permit where [] :: { \"name\": \"foo\" } == [];", PERMIT),
				// transformationError
				Arguments.of("policy \"test\" permit transform null * true", INDETERMINATE),
				// obligationError
				Arguments.of("policy \"test\" permit obligation \"a\" > 5", INDETERMINATE),
				// adviceError
				Arguments.of("policy \"test\" permit advice \"a\" > 5", INDETERMINATE),
				// importWildcard
				Arguments.of("import simple.* policy \"test\" permit where var a = append(\"a\",\"b\");", PERMIT),
				// importAttributeFinder
				Arguments.of("import sapl.pip.test.echo policy \"test\" permit where \"echo\" == \"echo\".<echo>;",
						PERMIT),
				// importLibrary
				Arguments.of(
						"import simple as simple_lib policy \"test\" permit where var a = simple_lib.append(\"a\",\"b\");",
						PERMIT),
				// importMultiple
				Arguments.of(
						"import simple.length import simple.append policy \"test\" permit where var a = append(\"a\",\"b\");",
						PERMIT),
				// importNonExistingFunction
				Arguments.of("import simple.non_existing policy \"test\" permit where true;", INDETERMINATE),
				// importDuplicateFunction
				Arguments.of("import simple.append import simple.append policy \"test\" permit where true;",
						INDETERMINATE),
				// importDuplicateFunctionMatchingPolicy
				Arguments.of("import simple.append import simple.append policy \"test\" permit where true;",
						INDETERMINATE),
				// importDuplicateWildcard
				Arguments.of("import simple.append import simple.* policy \"test\" permit where true;", INDETERMINATE),
				// importDuplicateAlias
				Arguments.of("import simple as test import simple as test policy \"test\" permit where true;",
						INDETERMINATE),
				// onErrorMap
				Arguments.of("import standard.* policy \"errors\" permit where onErrorMap(100/0, true);", PERMIT));
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("documentTestCases")
	void validateDocumentEvaluationResult(String policyDefinition, AuthorizationDecision expected) {
		assertThatPolicyEvaluationReturnsExpected(policyDefinition, expected);
	}

	private void assertThatPolicyEvaluationReturnsExpected(String document, AuthorizationDecision expected) {
		StepVerifier.create(INTERPRETER.evaluate(authzSubscription, document, attributeCtx, functionCtx, variables))
				.expectNext(expected).verifyComplete();
	}

}
