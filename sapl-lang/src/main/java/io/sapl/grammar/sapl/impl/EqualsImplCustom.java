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
package io.sapl.grammar.sapl.impl;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Equals;
import reactor.core.publisher.Flux;

import java.util.Map;

import static io.sapl.grammar.sapl.impl.util.OperatorUtil.operator;

/**
 * Checks for equality of two values.
 * <p>
 * Grammar: Comparison returns Expression: Prefixed (({Equals.left=current}
 * '==') right=Prefixed)?
 */
public class EqualsImplCustom extends EqualsImpl {

    @Override
    public Flux<Val> evaluate() {
        return operator(this, this, EqualsImplCustom::equals);
    }

    public static Val equals(Val left, Val right) {
        return primitiveEquals(left, right).withTrace(Equals.class, false,
                Map.of(Trace.LEFT, left, Trace.RIGHT, right));
    }

    private static Val primitiveEquals(Val left, Val right) {
        if (left.isUndefined() && right.isUndefined())
            return Val.TRUE;

        if (left.isUndefined() || right.isUndefined())
            return Val.FALSE;

        if (bothValuesAreNumbers(left, right))
            return Val.of(bothNumbersAreEqual(left, right));

        return Val.of(left.get().equals(right.get()));
    }

    private static boolean bothNumbersAreEqual(Val left, Val right) {
        return left.decimalValue().compareTo(right.decimalValue()) == 0;
    }

    private static boolean bothValuesAreNumbers(Val left, Val right) {
        return left.isNumber() && right.isNumber();
    }

}
