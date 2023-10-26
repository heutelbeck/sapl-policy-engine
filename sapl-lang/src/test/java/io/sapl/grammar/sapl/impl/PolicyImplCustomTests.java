/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.grammar.sapl.impl.util.TestUtil.hasDecision;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import reactor.test.StepVerifier;

class PolicyImplCustomTests {

    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private static Stream<Arguments> provideTestCases() throws JsonProcessingException {
        // @formatter:off
		return Stream.of(
	 			// simplePermitAll
	 			Arguments.of("policy \"p\" permit", AuthorizationDecision.PERMIT),
			
	 			// simpleDenyAll
	 			Arguments.of("policy \"p\" deny", AuthorizationDecision.DENY),
			
	 			// simplePermitAllWithBodyTrue
	 			Arguments.of("policy \"p\" permit where true;", AuthorizationDecision.PERMIT),
			
	 			// simplePermitAllWithBodyFalse
	 			Arguments.of("policy \"p\" permit where false;", AuthorizationDecision.NOT_APPLICABLE),
			
	 			// simplePermitAllWithBodyError
	 			Arguments.of("policy \"p\" permit where (10/0);", AuthorizationDecision.INDETERMINATE),
			
	 			// obligationEvaluatesSuccessfully
	 			Arguments.of("policy \"p\" permit obligation true", new AuthorizationDecision(Decision.PERMIT, Optional.empty(),
	 					Optional.of(Val.ofJson("[true]").getArrayNode()), Optional.empty())),
			
	 			// obligationErrors
	 			Arguments.of("policy \"p\" permit obligation (10/0)", AuthorizationDecision.INDETERMINATE),
			
	 			// obligationUndefined
	 			Arguments.of("policy \"p\" permit obligation undefined", AuthorizationDecision.INDETERMINATE),
			
	 			// adviceEvaluatesSuccessfully
	 			Arguments.of("policy \"p\" permit advice true", new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
	 					Optional.of(Val.ofJson("[true]").getArrayNode()))),
			
	 			// adviceErrors
	 			Arguments.of("policy \"p\" permit advice (10/0)", AuthorizationDecision.INDETERMINATE),
			
	 			// adviceUndefined
	 			Arguments.of("policy \"p\" permit advice undefined", AuthorizationDecision.INDETERMINATE),
			
	 			// transformEvaluatesSuccessfully
	 			Arguments.of("policy \"p\" permit transform true", new AuthorizationDecision(Decision.PERMIT, Optional.of(Val.JSON.booleanNode(true)),
	 					Optional.empty(), Optional.empty())),
			
	 			// transformErrors
	 			Arguments.of("policy \"p\" permit transform (10/0)", AuthorizationDecision.INDETERMINATE),
			
	 			// transformUndefined
	 			Arguments.of("policy \"p\" permit transform undefined",AuthorizationDecision.INDETERMINATE),
			
	 			// allComponentsPresentSuccessfully
	 			Arguments.of("policy \"p\" permit where true; obligation \"wash your hands\" advice \"smile\" transform [true,false,null]",
	 					new AuthorizationDecision(Decision.PERMIT, Optional.of(Val.ofJson("[true,false,null]").get()),
	 							Optional.of((ArrayNode) Val.ofJson("[\"wash your hands\"]").get()),
	 							Optional.of((ArrayNode) Val.ofJson("[\"smile\"]").get())))
			);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("provideTestCases")
	void documentEvaluatesToExpectedValue(String policySource, AuthorizationDecision expected) {
		var policy   = INTERPRETER.parse(policySource);
		StepVerifier.create(policy.evaluate().contextWrite(MockUtil::setUpAuthorizationContext)).expectNextMatches(hasDecision(expected)).verifyComplete();
	}
}