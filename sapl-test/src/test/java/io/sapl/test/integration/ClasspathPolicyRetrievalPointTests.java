/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.nio.file.Paths;
import java.util.HashMap;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.test.SaplTestException;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.lang.TestSaplInterpreter;

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
									ctx = AuthorizationContext.setAttributeContext(ctx,
											new AnnotationAttributeContext());
									ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
									ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
									ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription);
									return ctx;
								})
				.blockFirst().getMatchingDocuments();
		Assertions.assertThat(prpResult).hasSize(2);
	}

	@Test
	void test_dispose() {
		var prp = new ClasspathPolicyRetrievalPoint(Paths.get("policiesIT"), this.interpreter);
		prp.dispose();
		Assertions.assertThatNoException();
	}

	@Test
	void test_invalidPath() {
		assertThrows(SaplTestException.class,
				() -> new ClasspathPolicyRetrievalPoint(Paths.get("notExisting"), this.interpreter));
	}

	@Test
	void return_empty_result_when_no_documents_are_published() {
		var prp = new ClasspathPolicyRetrievalPoint(Paths.get("it"), this.interpreter);

		var result = prp.retrievePolicies().contextWrite(ctx -> {
			ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
			ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
			ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
			ctx = AuthorizationContext.setSubscriptionVariables(ctx, EMPTY_SUBSCRIPTION);
			return ctx;
		}).blockFirst();

		assertThat(result, notNullValue());
		assertThat(result.getMatchingDocuments(), empty());
		assertThat(result.isErrorsInTarget(), is(false));
		assertThat(result.isPrpValidState(), is(true));
	}

	@Test
	void return_fail_fast_for_invalid_document() {
		assertThrows(PolicyEvaluationException.class,
				() -> new ClasspathPolicyRetrievalPoint(Paths.get("it/invalid"), this.interpreter));
	}

	@Test
	void return_error_flag_when_evaluation_throws_exception() {
		var prp    = new ClasspathPolicyRetrievalPoint(Paths.get("it/error"), this.interpreter);
		var result = prp.retrievePolicies().contextWrite(ctx -> {
						ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
						ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
						ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
						ctx = AuthorizationContext.setSubscriptionVariables(ctx, EMPTY_SUBSCRIPTION);
						return ctx;
					}).blockFirst();

		assertThat(result, notNullValue());
		assertThat(result.getMatchingDocuments(), empty());
		assertThat(result.isErrorsInTarget(), is(true));
		assertThat(result.isPrpValidState(), is(true));
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
				.blockFirst();

		assertThat(result1, notNullValue());
		assertThat(result1.getMatchingDocuments().size(), is(1));
		assertThat(result1.isErrorsInTarget(), is(false));

		assertThat(result1.getMatchingDocuments().get(0).getPolicyElement().getSaplName(), is("policy read"));

		var authzSubscription2 = AuthorizationSubscription.of("Willi", "eat", "icecream");

		var result2 = prp.retrievePolicies().contextWrite(ctx -> {
			ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
			ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
			ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
			ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription2);
			return ctx;
		}).blockFirst();

		assertThat(result2, notNullValue());
		assertThat(result2.getMatchingDocuments().size(), is(1));
		assertThat(result2.isErrorsInTarget(), is(false));
		assertThat(result2.isPrpValidState(), is(true));

		assertThat(result2.getMatchingDocuments().get(0).getPolicyElement().getSaplName(), is("policy eat icecream"));
	}

	@Test
	void return_empty_result_for_non_matching_subscription() {
		var prp = new ClasspathPolicyRetrievalPoint(Paths.get("it/policies"), this.interpreter);

		var result = prp.retrievePolicies().contextWrite(ctx -> {
			ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
			ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
			ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
			ctx = AuthorizationContext.setSubscriptionVariables(ctx, EMPTY_SUBSCRIPTION);
			return ctx;
		}).blockFirst();

		assertThat(result, notNullValue());
		assertThat(result.getMatchingDocuments(), empty());
		assertThat(result.isErrorsInTarget(), is(false));
		assertThat(result.isPrpValidState(), is(true));
	}

	@Test
	void test_matchingReturnsError() {
		var prp               = new ClasspathPolicyRetrievalPoint(Paths.get("it/error2"), this.interpreter);
		var authzSubscription = AuthorizationSubscription.of("WILLI", "access", "foo", "");

		var result = prp.retrievePolicies().contextWrite(ctx -> {
			ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
			ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
			ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
			ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription);
			return ctx;
		}).blockFirst();

		Assertions.assertThat(result.isErrorsInTarget()).isTrue();
	}

}
