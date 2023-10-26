/*
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
package io.sapl.pdp.config;

import java.util.Map;
import java.util.function.UnaryOperator;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;

public record PDPConfiguration(AttributeContext attributeContext, FunctionContext functionContext,
        Map<String, JsonNode> variables, CombiningAlgorithm documentsCombinator,
        UnaryOperator<TracedDecision> decisionInterceptorChain,
        UnaryOperator<AuthorizationSubscription> subscriptionInterceptorChain) {

    public boolean isValid() {
        return attributeContext != null && functionContext != null && variables != null && documentsCombinator != null
                && decisionInterceptorChain != null && subscriptionInterceptorChain != null;
    }

}
