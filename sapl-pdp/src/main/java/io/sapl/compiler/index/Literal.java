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
package io.sapl.compiler.index;

import io.sapl.api.model.IndexPredicate;

/**
 * A literal in the canonical policy index: a predicate or its negation.
 * <p>
 * Represents {@code p} (positive) or {@code !p} (negated) in a boolean
 * formula.
 *
 * @param predicate the underlying predicate
 * @param negated true if this literal is the negation of the predicate
 */
public record Literal(IndexPredicate predicate, boolean negated) {

    /**
     * Returns a new literal with the opposite negation.
     *
     * @return negated copy of this literal
     */
    public Literal negate() {
        return new Literal(predicate, !negated);
    }

}
