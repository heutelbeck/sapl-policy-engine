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

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CompilationContextTests {

    /**
     * A foldable PureOperator whose semantic hash is fixed by construction, so
     * two instances with different payloads can be made to collide on the hash
     * while remaining semantically distinct (records compare component by
     * component). Evaluating yields a constant carrying the payload.
     */
    private record FixedHashOperator(long fixedHash, long payload) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return Value.of(payload);
        }

        @Override
        public SourceLocation location() {
            return null;
        }

        @Override
        public boolean isDependingOnSubscription() {
            return false;
        }

        @Override
        public long semanticHash() {
            return fixedHash;
        }
    }

    @Test
    @DisplayName("two foldable operators colliding on semanticHash but semantically distinct fold to their own constants")
    void whenTwoFoldableOperatorsCollideOnHashButAreSemanticallyDistinctThenEachFoldsToOwnConstant() {
        val ctx     = new CompilationContext(mock(FunctionBroker.class));
        val first   = new FixedHashOperator(42L, 100L);
        val second  = new FixedHashOperator(42L, 200L);
        val folded1 = ctx.foldCacheDedupe(first);
        val folded2 = ctx.foldCacheDedupe(second);
        assertThat(folded1).isEqualTo(Value.of(100L));
        assertThat(folded2).isEqualTo(Value.of(200L)).isNotEqualTo(folded1);
    }

    @Test
    @DisplayName("a foldable operator re-presented with identical semantics reuses the cached constant")
    void whenSameFoldableOperatorPresentedTwiceThenCachedConstantReused() {
        val ctx     = new CompilationContext(mock(FunctionBroker.class));
        val first   = new FixedHashOperator(7L, 55L);
        val second  = new FixedHashOperator(7L, 55L);
        val folded1 = ctx.foldCacheDedupe(first);
        val folded2 = ctx.foldCacheDedupe(second);
        assertThat(folded2).isEqualTo(Value.of(55L)).isSameAs(folded1);
    }
}
