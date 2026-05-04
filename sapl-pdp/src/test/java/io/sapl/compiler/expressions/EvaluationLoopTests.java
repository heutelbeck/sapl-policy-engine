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
package io.sapl.compiler.expressions;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

import static io.sapl.util.SaplTesting.attributeBroker;
import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.evaluationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sandbox for designing the snapshot-driven evaluation loop. Each test
 * starts from a SAPL expression string, parses and compiles it, then
 * exercises {@code evaluate(EvaluationContext)} against various trigger
 * scenarios.
 */
@DisplayName("Snapshot evaluation loop")
class EvaluationLoopTests {

    @Test
    @DisplayName("compiles a two-attribute equality expression to a StreamOperator")
    void whenCompilingTimeNowEqualsTimeNowThenStreamOperator() {
        val expression = compileExpression("<time.now> == <time.now>");

        assertThat(expression).isInstanceOf(StreamOperator.class);
    }

    @Test
    @DisplayName("legacy stream() prints emissions when both time.now sides emit three values")
    void whenStreamingThreeTimeNowValuesThenPrintEmissions() {
        val values     = new Value[] { Value.of("first"), Value.of("second"), Value.of("third") };
        val attrBroker = attributeBroker(invocation -> {
                           if ("time.now".equals(invocation.attributeName())) {
                               return Flux.fromArray(values).delayElements(Duration.ofMillis(1));
                           }
                           return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
                       });
        val expression = compileExpression("<time.now> == <time.now>", attrBroker);

        assertThat(expression).isInstanceOf(StreamOperator.class);
        val operator = (StreamOperator) expression;
        val evalCtx  = evaluationContext(attrBroker);

        val emissions = operator.stream().contextWrite(c -> c.put(EvaluationContext.class, evalCtx)).toStream()
                .toList();

        System.out.println("--- legacy stream() emissions for <time.now> == <time.now> ---");
        for (int i = 0; i < emissions.size(); i++) {
            System.out.println("  [" + i + "] " + emissions.get(i).value());
        }
    }

    @Test
    @DisplayName("snapshot evaluate() prints one result for a single round")
    void whenEvaluatingOnceThenPrintResultAndDependencies() {
        val expression = compileExpression("<time.now> == <time.now>");

        assertThat(expression).isInstanceOf(StreamOperator.class);
        val operator = (StreamOperator) expression;
        var evalCtx  = evaluationContext();

        val result = operator.evaluate(evalCtx);

        System.out.println("--- snapshot evaluate() (round 1, empty snapshot) ---");
        System.out.println("  result       : " + result.result());
        System.out.println("  dependencies : " + result.dependencies().size());
        for (val entry : result.dependencies().entrySet()) {
            System.out.println("    - invocation: " + entry.getKey().invocation().attributeName());
            for (val occurrence : entry.getValue()) {
                System.out.println("        occurrence: " + occurrence);
            }
        }

        val snapshot = new HashMap<SubscriptionKey, AttributeSnapshot>();
        for (val key : result.dependencies().keySet()) {
            snapshot.put(key, new AttributeSnapshot(Value.of("now"), Instant.now()));
        }
        evalCtx = evalCtx.withSnapshot(snapshot);

        val result2 = operator.evaluate(evalCtx);

        System.out.println("--- snapshot evaluate() (round 2, snapshot seeded) ---");
        System.out.println("  result       : " + result2.result());
        System.out.println("  dependencies : " + result2.dependencies().size());
    }
}
