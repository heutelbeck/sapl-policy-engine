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

import static io.sapl.grammar.sapl.impl.util.OperatorUtil.booleanOperator;

import java.util.Map;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.EagerOr;
import reactor.core.publisher.Flux;

/**
 * Implements the eager logical OR operation, noted as '|' in the grammar.
 * <p>
 * Grammar: Addition returns Expression: Multiplication (('|'
 * {EagerOr.left=current}) right=Multiplication)* ;
 */
public class EagerOrImplCustom extends EagerOrImpl {

    @Override
    public Flux<Val> evaluate() {
        return booleanOperator(this, this::or);
    }

    private Val or(Val left, Val right) {
        return Val.of(left.getBoolean() || right.getBoolean()).withTrace(EagerOr.class,
                Map.of(Trace.LEFT, left, Trace.RIGHT, right));
    }

}
