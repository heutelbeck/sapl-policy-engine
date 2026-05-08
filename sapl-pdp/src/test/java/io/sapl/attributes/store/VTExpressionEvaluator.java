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
package io.sapl.attributes.store;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import io.sapl.util.SaplTesting;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Compiles an expression string and exposes its evaluation as a
 * {@link Stream} of {@link Value} backed by an {@link AttributeStore}.
 * Pure expressions deliver one value then complete. Streaming
 * expressions deliver the latest value per fulfilled trigger; the
 * consumer blocks on {@link Stream#awaitNext()} until the next value
 * or completion.
 * <p>
 * Single-slot mailbox semantic: if the producer fires multiple times
 * before the consumer reads, the consumer observes only the latest
 * value. Intermediate values are dropped.
 */
@UtilityClass
public class VTExpressionEvaluator {

    public static Stream<Value> evaluate(String expression, AttributeStore store) {
        return evaluate(SaplTesting.compileExpression(expression), store);
    }

    public static Stream<Value> evaluate(CompiledExpression expr, AttributeStore store) {
        val baseCtx = SaplTesting.evaluationContext();
        val stream  = new LatestSlotStream<Value>();

        if (expr instanceof Value v) {
            stream.put(v);
            stream.complete();
            return stream;
        }
        if (expr instanceof PureOperator p) {
            stream.put(p.evaluate(baseCtx));
            stream.complete();
            return stream;
        }
        if (!(expr instanceof StreamOperator streamOp)) {
            stream.put(Value.error("Unexpected CompiledExpression variant: " + expr.getClass().getName()));
            stream.complete();
            return stream;
        }

        val initialDeps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(2);
        StreamOperator.evalChild(streamOp, baseCtx, initialDeps);
        if (initialDeps.isEmpty()) {
            val r = streamOp.evaluate(baseCtx);
            if (r.result() != null) {
                stream.put(r.result());
            }
            stream.complete();
            return stream;
        }

        val sub = store.open("vt-eval-" + UUID.randomUUID(), initialDeps.keySet(), snap -> {
            val r = streamOp.evaluate(baseCtx.withSnapshot(snap));
            if (r.result() != null) {
                stream.put(r.result());
            }
            return r.dependencies().keySet();
        });
        stream.onClose(sub::close);
        return stream;
    }
}
