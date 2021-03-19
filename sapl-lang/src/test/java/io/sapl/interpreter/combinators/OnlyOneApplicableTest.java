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
package io.sapl.interpreter.combinators;

import static io.sapl.interpreter.combinators.CombinatorMockUtil.mockPolicyRetrievalResult;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.impl.OnlyOneApplicableCombiningAlgorithmImplCustom;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.test.StepVerifier;

class OnlyOneApplicableTest {

	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final AuthorizationSubscription EMPTY_AUTH_SUBSCRIPTION = new AuthorizationSubscription(null, null,
			null, null);

	private static final AuthorizationSubscription AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE = new AuthorizationSubscription(
			null, null, JSON.booleanNode(true), null);

	private final OnlyOneApplicableCombiningAlgorithmImplCustom combinator = new OnlyOneApplicableCombiningAlgorithmImplCustom();

	private EvaluationContext evaluationCtx;

	@BeforeEach
	void setUp() {
		var attributeCtx = new AnnotationAttributeContext();
		var functionCtx = new AnnotationFunctionContext();
		evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, new HashMap<>());
	}

	@Test
	void combineDocumentsOneDeny() {
		var given = mockPolicyRetrievalResult(false, AuthorizationDecision.DENY);
		var expected = AuthorizationDecision.DENY;
		verifyDocumentsCombinator(given, expected);
	}

	@Test
	void combineDocumentsOnePermit() {
		var given = mockPolicyRetrievalResult(false, AuthorizationDecision.PERMIT);
		var expected = AuthorizationDecision.PERMIT;
		verifyDocumentsCombinator(given, expected);
	}

	@Test
	void combineDocumentsOneIndeterminate() {
		var given = mockPolicyRetrievalResult(false, AuthorizationDecision.INDETERMINATE);
		var expected = AuthorizationDecision.INDETERMINATE;
		verifyDocumentsCombinator(given, expected);
	}

	@Test
	void combineDocumentsOneNotApplicable() {
		var given = mockPolicyRetrievalResult(false, AuthorizationDecision.NOT_APPLICABLE);
		var expected = AuthorizationDecision.NOT_APPLICABLE;
		verifyDocumentsCombinator(given, expected);
	}

	@Test
	void combineDocumentsNoDocs() {
		var given = mockPolicyRetrievalResult(false);
		var expected = AuthorizationDecision.NOT_APPLICABLE;
		verifyDocumentsCombinator(given, expected);
	}

	@Test
	void combineDocumentsNoDocsButError() {
		var given = mockPolicyRetrievalResult(true);
		var expected = AuthorizationDecision.INDETERMINATE;
		verifyDocumentsCombinator(given, expected);
	}

	@Test
	void combineDocumentsMoreDocsWithError() {
		var given = mockPolicyRetrievalResult(true, AuthorizationDecision.DENY, AuthorizationDecision.NOT_APPLICABLE,
				AuthorizationDecision.PERMIT);
		var expected = AuthorizationDecision.INDETERMINATE;
		verifyDocumentsCombinator(given, expected);
	}

	@Test
	void combineDocumentsMoreDocs() {
		var given = mockPolicyRetrievalResult(false, AuthorizationDecision.DENY, AuthorizationDecision.NOT_APPLICABLE,
				AuthorizationDecision.PERMIT);
		var expected = AuthorizationDecision.INDETERMINATE;
		verifyDocumentsCombinator(given, expected);
	}

	private void verifyDocumentsCombinator(PolicyRetrievalResult given, AuthorizationDecision expected) {
		StepVerifier.create(combinator.combineMatchingDocuments(given, evaluationCtx)).expectNext(expected)
				.verifyComplete();
	}

	@Test
	void collectWithError() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp1\" deny "
				+ " policy \"testp2\" permit where (10/0);";
		assertEquals(AuthorizationDecision.INDETERMINATE,
				INTERPRETER.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst());
	}

	@Test
	void collectWithError2() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp1\" deny where (10/0);"
				+ " policy \"testp2\" permit";
		assertEquals(AuthorizationDecision.INDETERMINATE,
				INTERPRETER.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst());
	}

	@Test
	void collectWithError3() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp1\" deny where (10/0);"
				+ " policy \"testp2\" permit where (10/0)";
		assertEquals(AuthorizationDecision.INDETERMINATE,
				INTERPRETER.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst());
	}

	@Test
	void permit() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" permit";

		assertEquals(Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void deny() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" deny";

		assertEquals(Decision.DENY,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void notApplicableTarget() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" deny true == false";

		assertEquals(Decision.NOT_APPLICABLE,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void notApplicableCondition() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" deny where true == false;";

		assertEquals(Decision.NOT_APPLICABLE,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void indeterminateTarget() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" permit \"a\" < 5";

		assertEquals(Decision.INDETERMINATE,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void indeterminateCondition() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" permit where \"a\" < 5;";

		assertEquals(Decision.INDETERMINATE,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void onePolicyMatching() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp1\" deny false"
				+ " policy \"testp2\" permit true" + " policy \"testp3\" permit false";

		assertEquals(Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void twoPoliciesMatching1() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp1\" permit"
				+ " policy \"testp2\" deny";

		assertEquals(Decision.INDETERMINATE,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void twoPoliciesMatching2() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp1\" permit"
				+ " policy \"testp2\" permit";

		assertEquals(Decision.INDETERMINATE,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void twoPoliciesMatchingButOneNotApplicable() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp1\" permit"
				+ " policy \"testp2\" deny where false;";

		assertEquals(Decision.INDETERMINATE,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void singlePermitTransformation() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" permit transform true";

		assertEquals(Decision.PERMIT,
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getDecision());
	}

	@Test
	void singlePermitTransformationResource() {
		String policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" permit transform true";

		assertEquals(Optional.of(JSON.booleanNode(true)),
				INTERPRETER.evaluate(EMPTY_AUTH_SUBSCRIPTION, policySet, evaluationCtx).blockFirst().getResource());
	}

	@Test
	void collectObligationDeny() {
		String policySet = "set \"tests\" only-one-applicable"
				+ " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" deny false obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" permit false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

		ArrayNode obligation = JSON.arrayNode();
		obligation.add(JSON.textNode("obligation1"));

		assertEquals(Optional.of(obligation),
				INTERPRETER.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst()
						.getObligations());
	}

	@Test
	void collectAdviceDeny() {
		String policySet = "set \"tests\" only-one-applicable"
				+ " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" deny false obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" permit false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));

		assertEquals(Optional.of(advice), INTERPRETER
				.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst().getAdvices());
	}

	@Test
	void collectObligationPermit() {
		String policySet = "set \"tests\" only-one-applicable"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit false obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" deny false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false where false; obligation \"obligation4\" advice \"advice4\"";

		ArrayNode obligation = JSON.arrayNode();
		obligation.add(JSON.textNode("obligation1"));

		assertEquals(Optional.of(obligation),
				INTERPRETER.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst()
						.getObligations());
	}

	@Test
	void collectAdvicePermit() {
		String policySet = "set \"tests\" only-one-applicable"
				+ " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
				+ " policy \"testp2\" permit false obligation \"obligation2\" advice \"advice2\""
				+ " policy \"testp3\" deny false obligation \"obligation3\" advice \"advice3\""
				+ " policy \"testp4\" deny false where false; obligation \"obligation4\" advice \"advice4\"";

		ArrayNode advice = JSON.arrayNode();
		advice.add(JSON.textNode("advice1"));

		assertEquals(Optional.of(advice), INTERPRETER
				.evaluate(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, evaluationCtx).blockFirst().getAdvices());
	}

}
