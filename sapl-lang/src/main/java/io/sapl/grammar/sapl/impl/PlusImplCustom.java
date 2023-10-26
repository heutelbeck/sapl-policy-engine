/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.node.TextNode;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Plus;
import reactor.core.publisher.Flux;

public class PlusImplCustom extends PlusImpl {

    private static final TextNode UNDEFINED = Val.JSON.textNode("undefined");

    @Override
    public Flux<Val> evaluate() {
        return operator(this, this::plus);
    }

    private Val plus(Val left, Val right) {
        if (left.isNumber() && right.isNumber())
            return Val.of(left.get().decimalValue().add(right.get().decimalValue())).withTrace(Plus.class,
                    Map.of(Trace.LEFT, left, Trace.RIGHT, right));

        var lStr = left.orElse(UNDEFINED).asText();
        var rStr = right.orElse(UNDEFINED).asText();
        return Val.of(lStr.concat(rStr)).withTrace(Plus.class, Map.of(Trace.LEFT, left, Trace.RIGHT, right));
    }

}
