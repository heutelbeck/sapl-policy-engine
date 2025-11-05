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
package io.sapl.compiler;

import io.sapl.api.pdp.Decision;
import io.sapl.api.v2.Value;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

public class CompiledDocument {

    private List<Value>        constants = new ArrayList<>();
    private CompiledExpression targetExpression;

    public boolean matches(AuthorizationSubscription authorizationSubscription) {
        return true;
    }

    public Flux<AuthorizationDecision> evaluate(AuthorizationSubscription authorizationSubscription) {
        return Flux.just(new AuthorizationDecision(Decision.NOT_APPLICABLE, Value.UNDEFINED, List.of(), List.of()));
    }
}
