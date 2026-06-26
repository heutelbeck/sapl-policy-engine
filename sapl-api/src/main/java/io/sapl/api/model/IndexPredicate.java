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
package io.sapl.api.model;

/**
 * A predicate in the canonical policy index. Wraps a {@link PureOperator} with
 * its semantic hash for identity.
 * <p>
 * Two predicates are equal when their semantic hashes match and their operators
 * are {@link PureOperator#semanticEquals(PureOperator) semantically equal}. The
 * hash is a fast pre-filter. The structural check guards against a hash
 * collision merging two different predicates, which would change which
 * documents the index reports as applicable. The operator is retained for
 * evaluation when the index needs to determine the predicate's value for a
 * given authorization subscription.
 *
 * @param semanticHash identity hash from {@link PureOperator#semanticHash()}
 * @param operator a representative operator instance for evaluation
 */
public record IndexPredicate(long semanticHash, PureOperator operator) {

    @Override
    public int hashCode() {
        return Long.hashCode(semanticHash);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof IndexPredicate(var otherHash, var otherOperator) && semanticHash == otherHash
                && operator.semanticEquals(otherOperator);
    }

}
