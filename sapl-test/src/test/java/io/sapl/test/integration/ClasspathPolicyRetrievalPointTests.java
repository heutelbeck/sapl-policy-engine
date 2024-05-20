/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.test.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.test.SaplTestException;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.lang.TestSaplInterpreter;
import reactor.test.StepVerifier;

class ClasspathPolicyRetrievalPointTests {

    private static final AuthorizationSubscription EMPTY_SUBSCRIPTION = AuthorizationSubscription.of(null, null, null);

    private SAPLInterpreter interpreter;

    @BeforeEach
    void setup() {
        var recorder = mock(CoverageHitRecorder.class);
        interpreter = new TestSaplInterpreter(recorder);
    }

    @Test
    void test() {
        var prp               = new ClasspathPolicyRetrievalPoint(Paths.get("policiesIT"), this.interpreter);
        var authzSubscription = AuthorizationSubscription.of("WILLI", "access", "foo", "");
        var prpResult         = prp.retrievePolicies().contextWrite(ctx -> {
                                  ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
                                  ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
                                  ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
                                  ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription);
                                  return ctx;
                              });
        StepVerifier.create(prpResult).expectNextMatches(result -> result.getMatchingDocuments().size() == 2)
                .verifyComplete();
    }

    @Test
    void test_dispose() {
        new ClasspathPolicyRetrievalPoint(Paths.get("policiesIT"), this.interpreter);
        assertThatNoException();
    }

    @Test
    void test_invalidPath() {
        var path = Paths.get("notExisting");
        assertThrows(SaplTestException.class, () -> new ClasspathPolicyRetrievalPoint(path, this.interpreter));
    }

    @Test
    void return_fail_fast_for_invalid_document() {
        var path = Paths.get("it/invalid");
        assertThrows(PolicyEvaluationException.class, () -> new ClasspathPolicyRetrievalPoint(path, this.interpreter));
    }

    private static Stream<Arguments> provideTestCases() {
        // @formatter:off
		return Stream.of(
				// return_empty_result_when_no_documents_are_published
			    Arguments.of("it", Boolean.FALSE),
				// return_error_flag_when_evaluation_throws_exception
			    Arguments.of("it/error", Boolean.TRUE),
				// return_empty_result_for_non_matching_subscription
			    Arguments.of("it/policies", Boolean.FALSE)
			);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void validateSimmilarScenarionsWhereNoMatchValidState(String path, boolean hasErrors) {
        var prp = new ClasspathPolicyRetrievalPoint(Paths.get(path), this.interpreter);

        var result = prp.retrievePolicies().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
            return AuthorizationContext.setSubscriptionVariables(ctx, EMPTY_SUBSCRIPTION);
        }).block();

        assertThat(result, notNullValue());
        assertThat(result.getMatchingDocuments(), empty());
        assertThat(result.isRetrievalWithErrors(), is(hasErrors));
        assertThat(result.isPrpInconsistent(), is(false));
    }

    @Test
    void return_matching_document_for_valid_subscription() {
        var prp = new ClasspathPolicyRetrievalPoint(Paths.get("it/policies"), this.interpreter);

        var authzSubscription1 = AuthorizationSubscription.of(null, "read", null);
        var result1            = prp.retrievePolicies().contextWrite(ctx -> {
                                   ctx = AuthorizationContext.setAttributeContext(ctx,
                                           new AnnotationAttributeContext());
                                   ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
                                   ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
                                   ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription1);
                                   return ctx;
                               })
                .block();

        assertThat(result1, notNullValue());
        assertThat(result1.getMatchingDocuments().size(), is(1));
        assertThat(result1.isRetrievalWithErrors(), is(false));

        assertThat(result1.getMatchingDocuments().get(0).document().name(), is("policy read"));

        var authzSubscription2 = AuthorizationSubscription.of("Willi", "eat", "ice cream");

        var result2 = prp.retrievePolicies().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription2);
            return ctx;
        }).block();

        assertThat(result2, notNullValue());
        assertThat(result2.getMatchingDocuments().size(), is(1));
        assertThat(result2.isRetrievalWithErrors(), is(false));
        assertThat(result2.isPrpInconsistent(), is(false));

        assertThat(result2.getMatchingDocuments().get(0).document().name(), is("policy eat ice cream"));
    }

    @Test
    void test_matchingReturnsError() {
        var prp               = new ClasspathPolicyRetrievalPoint(Paths.get("it/error2"), this.interpreter);
        var authzSubscription = AuthorizationSubscription.of("WILLI", "access", "foo", "");

        var result = prp.retrievePolicies().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
            return AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription);
        }).block();

        Assertions.assertThat(result.isRetrievalWithErrors()).isTrue();
    }

    @Nested
    @DisplayName("Read policies from document names tests")
    class ReadPoliciesFromDocumentNamesTests {
        private PolicyRetrievalResult getResultFromPRP(final PolicyRetrievalPoint policyRetrievalPoint,
                final AuthorizationSubscription authorizationSubscription) {
            return policyRetrievalPoint.retrievePolicies().contextWrite(ctx -> {
                ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
                ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
                ctx = AuthorizationContext.setVariables(ctx, Collections.emptyMap());
                return AuthorizationContext.setSubscriptionVariables(ctx, authorizationSubscription);
            }).block();
        }

        @Test
        void givenNullDocumentNames_resultContainsNoErrorAndNoDocuments() {
            final var prp    = new ClasspathPolicyRetrievalPoint((List<String>) null, interpreter);
            final var result = getResultFromPRP(prp, EMPTY_SUBSCRIPTION);

            assertThat(result, notNullValue());
            assertThat(result.getMatchingDocuments(), empty());
            assertThat(result.isRetrievalWithErrors(), is(false));
            assertThat(result.isPrpInconsistent(), is(false));
        }

        @Test
        void givenEmptyDocumentNames_resultContainsNoErrorAndNoDocuments() {
            final var prp    = new ClasspathPolicyRetrievalPoint(Collections.emptyList(), interpreter);
            final var result = getResultFromPRP(prp, EMPTY_SUBSCRIPTION);

            assertThat(result, notNullValue());
            assertThat(result.getMatchingDocuments(), empty());
            assertThat(result.isRetrievalWithErrors(), is(false));
            assertThat(result.isPrpInconsistent(), is(false));
        }

        @Test
        void givenNullValueInDocumentNames_throwsSaplTestException() {
            final var documentNames = Collections.<String>singletonList(null);
            final var exception     = assertThrows(SaplTestException.class,
                    () -> new ClasspathPolicyRetrievalPoint(documentNames, interpreter));

            assertEquals("Encountered invalid policy name", exception.getMessage());
        }

        @Test
        void givenEmptyValueInDocumentNames_throwsSaplTestException() {
            final var saplDocumentNames = List.of("");

            final var exception = assertThrows(SaplTestException.class,
                    () -> new ClasspathPolicyRetrievalPoint(saplDocumentNames, interpreter));

            assertEquals("Encountered invalid policy name", exception.getMessage());
        }

        @Test
        void givenSinglePolicyWithError_resultContainsErrorAndEmptyDocuments() {
            final var prp = new ClasspathPolicyRetrievalPoint(List.of("it/error/policy_with_error"), interpreter);

            final var result = getResultFromPRP(prp, EMPTY_SUBSCRIPTION);

            assertThat(result, notNullValue());
            assertThat(result.getMatchingDocuments(), empty());
            assertThat(result.isRetrievalWithErrors(), is(true));
            assertThat(result.isPrpInconsistent(), is(false));
        }

        @Test
        void givenMultiplePoliciesWithError_resultContainsErrorAndEmptyDocuments() {
            final var prp = new ClasspathPolicyRetrievalPoint(
                    List.of("it/error/policy_with_error", "it/error2/policy.sapl"), interpreter);

            final var result = getResultFromPRP(prp, EMPTY_SUBSCRIPTION);

            assertThat(result, notNullValue());
            assertThat(result.getMatchingDocuments(), empty());
            assertThat(result.isRetrievalWithErrors(), is(true));
            assertThat(result.isPrpInconsistent(), is(false));
        }

        @Test
        void givenOnePolicyWithErrorAndOnePolicyNotMatching_resultContainsErrorAndEmptyDocuments() {
            final var prp = new ClasspathPolicyRetrievalPoint(
                    List.of("it/error/policy_with_error", "it/policies/policy_1.sapl"), interpreter);

            final var result = getResultFromPRP(prp, EMPTY_SUBSCRIPTION);

            assertThat(result, notNullValue());
            assertThat(result.getMatchingDocuments(), empty());
            assertThat(result.isRetrievalWithErrors(), is(true));
            assertThat(result.isPrpInconsistent(), is(false));
        }

        @Test
        void givenOnePolicyWithErrorAndOneValidMatchingPolicyAndOneInvalidPolicy_throwsPolicyEvaluationException() {
            final var saplDocumentNames = List.of("it/error/policy_with_error", "it/policies/policy_1",
                    "it/invalid/invalid_policy");

            final var exception = assertThrows(PolicyEvaluationException.class,
                    () -> new ClasspathPolicyRetrievalPoint(saplDocumentNames, interpreter));

            assertThat(exception.getMessage()).contains(
                    "Parsing errors: [XtextSyntaxDiagnostic: null:2 Incomplete policy, expected an entitlement, e.g. deny or permit]");
        }

        @Test
        void givenOnePolicyWithErrorAndOneValidNonMatchingPolicy_returnsErrorAndMatchingPolicy() {
            final var prp = new ClasspathPolicyRetrievalPoint(
                    List.of("it/error/policy_with_error", "it/policies/policy_2", "it/error2/policy.sapl"),
                    interpreter);

            final var result = getResultFromPRP(prp, EMPTY_SUBSCRIPTION);

            assertThat(result, notNullValue());
            assertThat(result.getMatchingDocuments(), empty());
            assertThat(result.isRetrievalWithErrors(), is(true));
            assertThat(result.isPrpInconsistent(), is(false));
        }

        @Test
        void givenOnePolicyWithErrorAndOneValidMatchingPolicy_returnsErrorAndMatchingPolicy() {
            final var prp = new ClasspathPolicyRetrievalPoint(
                    List.of("it/error/policy_with_error", "it/policies/policy_2", "it/error2/policy.sapl"),
                    interpreter);

            final var authorizationSubscription = AuthorizationSubscription.of("Willi", "eat", "ice cream");
            final var result                    = getResultFromPRP(prp, authorizationSubscription);

            assertThat(result, notNullValue());
            assertThat(result.getMatchingDocuments().size(), is(1));
            assertThat(result.getMatchingDocuments().get(0).document().name(), is("policy eat ice cream"));
            assertThat(result.isRetrievalWithErrors(), is(true));
            assertThat(result.isPrpInconsistent(), is(false));
        }
    }

}
