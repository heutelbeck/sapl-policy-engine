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
package io.sapl.prp.index.canonical;

import java.util.Objects;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Literal {

    @Getter
    @NonNull
    private final Bool bool;

    @Getter
    private final boolean negated;

    private int hash;

    private boolean hasHashCode;

    public Literal(@NonNull final Bool bool) {
        this(bool, false);
    }

    public boolean isImmutable() {
        return bool.isImmutable();
    }

    public boolean evaluate() {
        return negated ^ bool.evaluate();
    }

    public Literal negate() {
        return new Literal(bool, !negated);
    }

    public boolean sharesBool(final Literal other) {
        return bool.equals(other.bool);
    }

    public boolean sharesNegation(final Literal other) {
        return negated == other.negated;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Literal other = (Literal) obj;
        if (negated != other.negated) {
            return false;
        }
        if (hashCode() != obj.hashCode()) {
            return false;
        }
        return bool.equals(other.bool);
    }

    @Override
    public int hashCode() {
        if (!hasHashCode) {
            hash        = Objects.hash(bool, negated);
            hasHashCode = true;
        }
        return hash;
    }

}
