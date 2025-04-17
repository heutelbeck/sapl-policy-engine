/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.combinators;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@UtilityClass
public class CombinatorTestUtil {
    private static final DefaultSAPLInterpreter       INTERPRETER      = new DefaultSAPLInterpreter();
    private static final CachingAttributeStreamBroker ATTRIBUTE_BROKER = new CachingAttributeStreamBroker();
    private static final AnnotationFunctionContext    FUNCTION_CTX     = new AnnotationFunctionContext();
    private static final Map<String, Val>             VARIABLES        = new HashMap<>();

    public static void validateDecision(AuthorizationSubscription subscription, String policySet, Decision expected) {
        final var decisions = evaluate(subscription, policySet).map(AuthorizationDecision::getDecision);
        StepVerifier.create(decisions).expectNext(expected).verifyComplete();
    }

    public static void validateResource(AuthorizationSubscription subscription, String policySet,
            Optional<JsonNode> expected) {
        final var decisions = evaluate(subscription, policySet).map(AuthorizationDecision::getResource);
        StepVerifier.create(decisions).expectNext(expected).verifyComplete();
    }

    public static void validateObligations(AuthorizationSubscription subscription, String policySet,
            Optional<ArrayNode> expected) {
        final var decisions = evaluate(subscription, policySet).map(AuthorizationDecision::getObligations);
        StepVerifier.create(decisions).expectNext(expected).verifyComplete();
    }

    public static void validateAdvice(AuthorizationSubscription subscription, String policySet,
            Optional<ArrayNode> expected) {
        final var decisions = evaluate(subscription, policySet).map(AuthorizationDecision::getAdvice);
        StepVerifier.create(decisions).expectNext(expected).verifyComplete();
    }

    private Flux<AuthorizationDecision> evaluate(AuthorizationSubscription subscription, String policySet) {
        return INTERPRETER.evaluate(subscription, policySet, ATTRIBUTE_BROKER, FUNCTION_CTX, VARIABLES);
    }

}
