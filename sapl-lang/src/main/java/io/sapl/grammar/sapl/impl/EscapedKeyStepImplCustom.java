/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Map;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.EscapedKeyStep;
import io.sapl.grammar.sapl.FilterStatement;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the application of a key step to a previous value, e.g
 * 'value."name"'.
 * <p>
 * Grammar: {EscapedKeyStep} id=STRING
 */
public class EscapedKeyStepImplCustom extends EscapedKeyStepImpl {

    @Override
    public Flux<Val> apply(@NonNull Val parentValue) {
        return Flux.just(KeyStepImplCustom.applyToValue(parentValue, id).withTrace(EscapedKeyStep.class,
                Map.of(Trace.PARENT_VALUE, parentValue, Trace.IDENTIFIER, Val.of(id))));
    }

    @Override
    public Flux<Val> applyFilterStatement(@NonNull Val parentValue, int stepId, @NonNull FilterStatement statement) {
        return KeyStepImplCustom.applyKeyStepFilterStatement(id, parentValue, stepId, statement);
    }
}
