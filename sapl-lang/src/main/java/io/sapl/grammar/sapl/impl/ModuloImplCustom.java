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

import static io.sapl.grammar.sapl.impl.util.OperatorUtil.arithmeticOperator;

import java.math.BigDecimal;
import java.util.Map;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Modulo;
import reactor.core.publisher.Flux;

/**
 * Implements the numerical remainder operator, written as '%' in Expressions.
 */
public class ModuloImplCustom extends ModuloImpl {

    private static final String DIVISION_BY_ZERO_ERROR = "Division by zero";

    @Override
    public Flux<Val> evaluate() {
        return arithmeticOperator(this, this::divide);
    }

    private Val divide(Val dividend, Val divisor) {
        if (divisor.decimalValue().compareTo(BigDecimal.ZERO) == 0)
            return Val.error(DIVISION_BY_ZERO_ERROR).withTrace(Modulo.class,
                    Map.of(Trace.DIVIDEND, dividend, Trace.DIVISOR, divisor));
        return Val.of(dividend.decimalValue().remainder(divisor.decimalValue())).withTrace(Modulo.class,
                Map.of(Trace.DIVIDEND, dividend, Trace.DIVISOR, divisor));
    }

}
