/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.compiler.util;

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.expressions.CompilationContext;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DummyEvaluationContextFactory {
    private static final AuthorizationSubscription DUMMY_SUBSCRIPTION = new AuthorizationSubscription(Value.UNDEFINED,
            Value.UNDEFINED, Value.UNDEFINED, Value.UNDEFINED, Value.EMPTY_OBJECT);

    public EvaluationContext dummyContext(CompilationContext ctx) {
        return new EvaluationContext(ctx.getPdpId(), ctx.getConfigurationId(), "compile-time-evaluation",
                DUMMY_SUBSCRIPTION, ctx.getFunctionBroker(), ctx.getAttributeBroker(), Value.UNDEFINED,
                Value.UNDEFINED);
    }
}
