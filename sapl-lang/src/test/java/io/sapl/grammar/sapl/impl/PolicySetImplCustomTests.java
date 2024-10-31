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
package io.sapl.grammar.sapl.impl;

import static io.sapl.api.pdp.AuthorizationDecision.DENY;
import static io.sapl.api.pdp.AuthorizationDecision.INDETERMINATE;
import static io.sapl.api.pdp.AuthorizationDecision.NOT_APPLICABLE;
import static io.sapl.api.pdp.AuthorizationDecision.PERMIT;
import static io.sapl.testutil.TestUtil.hasDecision;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.testutil.MockUtil;
import reactor.test.StepVerifier;

class PolicySetImplCustomTests {

    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private static Stream<Arguments> provideTestCases() throws JsonProcessingException {
        // @formatter:off
		return Stream.of(
			// simplePermitAllOnePolicy
		    Arguments.of("set \"set\" deny-overrides "
			           + "policy \"set.p1\" permit",
			           PERMIT),

			// denyOverrides
			Arguments.of("set \"set\" deny-overrides "
			           + "policy \"permits\" permit "
					   + "policy \"indeterminate\" permit where (10/0); "
			           + "policy \"denies\" deny "
					   + "policy \"not-applicable\" deny where false;",
					   DENY),

			// permitOverrides
			Arguments.of("set \"set\" permit-overrides "
			           + "policy \"permits\" permit "
					   + "policy \"indeterminate\" permit where (10/0); "
			           + "policy \"denies\" deny "
					   + "policy \"not-applicable\" deny where false;",
					   PERMIT),

			// onlyOneApplicable
			Arguments.of("set \"set\" only-one-applicable "
			           + "policy \"permits\" permit "
					   + "policy \"indeterminate\" permit where (10/0); "
			           + "policy \"denies\" deny "
					   + "policy \"not-applicable\" deny where false;",
					   INDETERMINATE),

			// firstApplicable
			Arguments.of("set \"set\" first-applicable "
			           + "policy \"permits\" permit "
					   + "policy \"indeterminate\" permit where (10/0); "
			           + "policy \"denies\" deny "
					   + "policy \"not-applicable\" deny where false;",
					   PERMIT),

			// denyUnlessPermit
			Arguments.of("set \"set\" deny-unless-permit "
			           + "policy \"permits\" permit "
					   + "policy \"indeterminate\" permit where (10/0); "
			           + "policy \"denies\" deny "
					   + "policy \"not-applicable\" deny where false;",
					   PERMIT),

			// duplicatePolicyNamesIndeterminate
			Arguments.of("set \"set\" deny-unless-permit"
			           + " policy \"permits\" permit "
					   + " policy \"permits\" permit",
					   INDETERMINATE),

			// permitUnlessDeny
			Arguments.of("set \"set\" permit-unless-deny "
			           + "policy \"permits\" permit "
					   + "policy \"indeterminate\" permit where (10/0); "
			           + "policy \"denies\" deny "
					   + "policy \"not-applicable\" deny where false;",
					   DENY),

			// valueDefinitions
			Arguments.of("set \"set\" deny-overrides "
			           + "var a = 5; "
					   + "var b = a+2; "
			           + "policy \"set.p1\" permit where a==5 && b == 7;",
			           PERMIT),

			// valueDefinitionsUndefined
			Arguments.of("set \"set\" deny-overrides "
			           + "var a = undefined; "
					   + "policy \"set.p1\" permit where a==undefined;",
					   PERMIT),

			// valueErrorLazy
			Arguments.of("set \"set\" first-applicable "
			           + "var a = (10/0); "
					   + "var b = 12; "
			           + "policy \"set.p1\" permit where a==undefined;",
			           INDETERMINATE),

			// valueDefinitionsFromOnePolicyDoNotLeakIntoOtherPolicy
			Arguments.of("set \"set\" deny-overrides "
			           + "policy \"set.p1\" permit where var a=5; var b=2; "
					   + "policy \"set.p2\" permit where a==undefined && b == undefined;",
					   PERMIT),

			// simpleDenyAll
			Arguments.of("policy \"p\" deny", DENY),

			// simplePermitAllWithBodyTrue
			Arguments.of("policy \"p\" permit where true;", PERMIT),

			// simplePermitAllWithBodyFalse
			Arguments.of("policy \"p\" permit where false;", NOT_APPLICABLE),

			// simplePermitAllWithBodyError
			Arguments.of("policy \"p\" permit where (10/0);", INDETERMINATE),

			// obligationEvaluatesSuccessfully
			Arguments.of("policy \"p\" permit obligation true",
					new AuthorizationDecision(Decision.PERMIT, Optional.empty(),Optional.of(Val.ofJson("[true]").getArrayNode()), Optional.empty())),

			// obligationErrors
			Arguments.of("policy \"p\" permit obligation (10/0)", INDETERMINATE),

			// obligationUndefined
			Arguments.of("policy \"p\" permit obligation undefined", INDETERMINATE),

			// adviceEvaluatesSuccessfully
			Arguments.of("policy \"p\" permit advice true",
					new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(), Optional.of(Val.ofJson("[true]").getArrayNode()))),

			// adviceErrors
			Arguments.of("policy \"p\" permit advice (10/0)", INDETERMINATE),

			// adviceUndefined
			Arguments.of("policy \"p\" permit advice undefined", INDETERMINATE),

			// transformEvaluatesSuccessfully
			Arguments.of("policy \"p\" permit transform true",
					new AuthorizationDecision(Decision.PERMIT, Optional.of(Val.JSON.booleanNode(true)),	Optional.empty(), Optional.empty())),

			// transformErrors
			Arguments.of("policy \"p\" permit transform (10/0)" , INDETERMINATE),

			// transformUndefined
			Arguments.of("policy \"p\" permit transform undefined" , INDETERMINATE),

			// allComponentsPresentSuccessfully
			Arguments.of("policy \"p\" permit where true; obligation \"wash your hands\" advice \"smile\" transform [true,false,null]",
					new AuthorizationDecision(Decision.PERMIT, Optional.of(Val.ofJson("[true,false,null]").get()),
							Optional.of((ArrayNode) Val.ofJson("[\"wash your hands\"]").get()),
							Optional.of((ArrayNode) Val.ofJson("[\"smile\"]").get())))
		);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void documentEvaluatesToExpectedValue(String policySource, AuthorizationDecision expected) {
        final var policy = INTERPRETER.parse(policySource);
        StepVerifier.create(policy.evaluate().contextWrite(MockUtil::setUpAuthorizationContext))
                .expectNextMatches(hasDecision(expected)).verifyComplete();
    }

    @Test
    void testTargetResult() {
        final var policy = INTERPRETER
                .parse("set \"set\" deny-overrides " + "policy \"set.p1\" permit where var a=5; var b=2; "
                        + "policy \"set.p2\" permit where a==undefined && b == undefined;");
        assertThat(policy.getPolicyElement().targetResult(ErrorFactory.error("Error")).getAuthorizationDecision()
                .getDecision()).isEqualTo(Decision.INDETERMINATE);
        assertThat(policy.getPolicyElement().targetResult(Val.of("XXX")).getAuthorizationDecision().getDecision())
                .isEqualTo(Decision.NOT_APPLICABLE);
    }
}
