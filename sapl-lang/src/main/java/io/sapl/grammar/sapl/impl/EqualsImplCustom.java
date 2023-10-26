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
package io.sapl.grammar.sapl.impl;

import static io.sapl.grammar.sapl.impl.util.OperatorUtil.operator;

import java.util.Map;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Equals;
import reactor.core.publisher.Flux;

/**
 * Checks for equality of two values.
 * <p>
 * Grammar: Comparison returns Expression: Prefixed (({Equals.left=current}
 * '==') right=Prefixed)? ;
 */
public class EqualsImplCustom extends EqualsImpl {

    @Override
    public Flux<Val> evaluate() {
        return operator(this, this::tracedEquals);
    }

    private Val tracedEquals(Val left, Val right) {
        return equals(left, right).withTrace(Equals.class, Map.of(Trace.LEFT, left, Trace.RIGHT, right));
    }

    private Val equals(Val left, Val right) {
        if (left.isUndefined() && right.isUndefined())
            return Val.TRUE;

        if (left.isUndefined() || right.isUndefined())
            return Val.FALSE;

        if (bothValuesAreNumbers(left, right))
            return Val.of(bothNumbersAreEqual(left, right));

        return Val.of(left.get().equals(right.get()));
    }

    private boolean bothNumbersAreEqual(Val left, Val right) {
        return left.decimalValue().compareTo(right.decimalValue()) == 0;
    }

    private boolean bothValuesAreNumbers(Val left, Val right) {
        return left.isNumber() && right.isNumber();
    }

}
