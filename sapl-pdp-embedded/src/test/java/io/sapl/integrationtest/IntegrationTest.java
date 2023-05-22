/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.integrationtest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.prp.GenericInMemoryIndexedPolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.prp.filesystem.FileSystemPrpUpdateEventSource;
import io.sapl.prp.index.ImmutableParsedDocumentIndex;
import io.sapl.prp.index.canonical.CanonicalImmutableParsedDocumentIndex;
import reactor.util.context.Context;

@Timeout(3)
class IntegrationTest {

	private SAPLInterpreter interpreter;

	private ImmutableParsedDocumentIndex seedIndex;

	private static final AuthorizationSubscription EMPTY_SUBSCRIPTION = AuthorizationSubscription.of(null, null, null);

	@BeforeEach
	void setUp() {
		interpreter = new DefaultSAPLInterpreter();
		seedIndex   = new CanonicalImmutableParsedDocumentIndex(new AnnotationAttributeContext(),
				new AnnotationFunctionContext());
	}

	@Test
	void return_empty_result_when_no_documents_are_published() {
		var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/empty", interpreter);
		var prp    = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);

		PolicyRetrievalResult result = prp.retrievePolicies()
				.contextWrite(ctx -> setUpAuthorizationContext(ctx, EMPTY_SUBSCRIPTION)).blockFirst();

		assertThat(result, notNullValue());
		assertThat(result.getMatchingDocuments(), empty());
		assertThat(result.isErrorsInTarget(), is(false));
		assertThat(result.isPrpValidState(), is(true));
	}

	@Test
	void return_invalid_prp_state_for_invalid_document() {
		var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/invalid", interpreter);
		var prp    = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);

		PolicyRetrievalResult result = prp.retrievePolicies()
				.contextWrite(ctx -> setUpAuthorizationContext(ctx, EMPTY_SUBSCRIPTION)).blockFirst();

		assertThat(result, notNullValue());
		assertThat(result.getMatchingDocuments(), empty());
		assertThat(result.isErrorsInTarget(), is(true));
		assertThat(result.isPrpValidState(), is(false));

	}

	@Test
	void return_error_flag_when_evaluation_throws_exception() {
		var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/error", interpreter);
		var prp    = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);

		PolicyRetrievalResult result = prp.retrievePolicies()
				.contextWrite(ctx -> setUpAuthorizationContext(ctx, EMPTY_SUBSCRIPTION)).blockFirst();

		assertThat(result, notNullValue());
		assertThat(result.getMatchingDocuments(), empty());
		assertThat(result.isErrorsInTarget(), is(true));
		assertThat(result.isPrpValidState(), is(true));
	}

	@Test
	void return_matching_document_for_valid_subscription() {
		var source            = new FileSystemPrpUpdateEventSource("src/test/resources/it/policies", interpreter);
		var prp               = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);
		var authzSubscription = AuthorizationSubscription.of(null, "read", null);

		PolicyRetrievalResult result = prp.retrievePolicies()
				.contextWrite(ctx -> setUpAuthorizationContext(ctx, authzSubscription)).blockFirst();

		assertThat(result, notNullValue());
		assertThat(result.getMatchingDocuments().size(), is(1));
		assertThat(result.isErrorsInTarget(), is(false));

		assertThat(result.getMatchingDocuments().get(0).getPolicyElement().getSaplName(), is("policy read"));

		var authzSubscription2 = AuthorizationSubscription.of("Willi", "eat", "icecream");

		result = prp.retrievePolicies().contextWrite(ctx -> setUpAuthorizationContext(ctx, authzSubscription2))
				.blockFirst();

		assertThat(result, notNullValue());
		assertThat(result.getMatchingDocuments().size(), is(1));
		assertThat(result.isErrorsInTarget(), is(false));
		assertThat(result.isPrpValidState(), is(true));

		assertThat(result.getMatchingDocuments().get(0).getPolicyElement().getSaplName(), is("policy eat icecream"));
	}

	@Test
	void return_empty_result_for_non_matching_subscription() {
		var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/policies", interpreter);
		var prp    = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);

		PolicyRetrievalResult result = prp.retrievePolicies()
				.contextWrite(ctx -> setUpAuthorizationContext(ctx, EMPTY_SUBSCRIPTION)).blockFirst();

		assertThat(result, notNullValue());
		assertThat(result.getMatchingDocuments(), empty());
		assertThat(result.isErrorsInTarget(), is(false));
		assertThat(result.isPrpValidState(), is(true));
	}

	public static Context setUpAuthorizationContext(Context ctx, AuthorizationSubscription authzSubscription) {
		var attributeCtx = new AnnotationAttributeContext();
		var functionCtx  = new AnnotationFunctionContext();
		ctx = AuthorizationContext.setAttributeContext(ctx, attributeCtx);
		ctx = AuthorizationContext.setFunctionContext(ctx, functionCtx);
		ctx = AuthorizationContext.setVariable(ctx, "nullVariable", Val.NULL);
		ctx = AuthorizationContext.setImports(ctx, new HashMap<>());
		ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription);
		return ctx;
	}
}
