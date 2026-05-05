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
package io.sapl.compiler.eval;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.util.SaplTesting;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Compiles an expression string and exposes its evaluation as a
 * {@link Flux} of {@link Value}, driven by an {@link AttributeStore}.
 * Pure expressions emit one value and complete. Streaming expressions
 * emit the latest value per fulfilled trigger until the subscription is
 * cancelled; intermediate values are dropped via
 * {@code onBackpressureLatest()} when downstream is slow.
 * <p>
 * Test fixture for end-to-end exercise of the {@link AttributeStore}
 * contract; the production decision-publication path follows the same
 * shape with {@code Vote} as the payload type.
 */
@UtilityClass
public class FluxExpressionEvaluator {

    public static Flux<Value> evaluate(String expression, AttributeStore store) {
        return evaluate(SaplTesting.compileExpression(expression), store);
    }

    public static Flux<Value> evaluate(CompiledExpression expr, AttributeStore store) {
        val baseCtx = SaplTesting.evaluationContext();

        if (expr instanceof Value v) {
            return Flux.just(v);
        }
        if (expr instanceof PureOperator p) {
            return Flux.just(p.evaluate(baseCtx));
        }
        if (!(expr instanceof StreamOperator streamOp)) {
            return Flux.error(
                    new IllegalStateException("Unexpected CompiledExpression variant: " + expr.getClass().getName()));
        }

        val initialDeps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(2);
        StreamOperator.evalChild(streamOp, baseCtx, initialDeps);
        if (initialDeps.isEmpty()) {
            val r = streamOp.evaluate(baseCtx);
            return r.result() != null ? Flux.just(r.result()) : Flux.empty();
        }

        val sink = Sinks.many().unicast().<Value>onBackpressureBuffer();
        val sub  = store.open("flux-eval-" + UUID.randomUUID(), initialDeps.keySet(), snap -> {
                     val r = streamOp.evaluate(baseCtx.withSnapshot(snap));
                     if (r.result() != null) {
                         sink.tryEmitNext(r.result());
                     }
                     return r.dependencies().keySet();
                 });
        return sink.asFlux().onBackpressureLatest().doFinally(signal -> sub.close());
    }
}
