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
package io.sapl.pdp.config;

import java.util.Map;
import java.util.function.UnaryOperator;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.prp.PolicyRetrievalPoint;
import lombok.NonNull;

public record PDPConfiguration(@NonNull String configurationId, @NonNull AttributeContext attributeContext,
        @NonNull FunctionContext functionContext,Map<String, Val> variables,
        PolicyDocumentCombiningAlgorithm documentsCombinator,
        @NonNull UnaryOperator<TracedDecision> decisionInterceptorChain,
        @NonNull UnaryOperator<AuthorizationSubscription> subscriptionInterceptorChain,
        @NonNull PolicyRetrievalPoint policyRetrievalPoint) {

    public boolean isValid() {
        return variables !=null && documentsCombinator != null && policyRetrievalPoint.isConsistent();
    }
}
