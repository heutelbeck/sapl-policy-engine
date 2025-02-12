/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.index.canonical;

import java.util.Map;
import java.util.Objects;

import com.google.common.base.Preconditions;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import io.sapl.interpreter.context.AuthorizationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Bool {

    static final String BOOL_NOT_IMMUTABLE = "Unable to evaluate volatile Bool in static context.";

    private boolean constant;

    private Expression expression;

    private int hash;

    private boolean hasHashCode;

    private Map<String, String> imports;

    private boolean isConstantExpression;

    public Bool(boolean value) {
        isConstantExpression = true;
        constant             = value;
    }

    public Bool(final Expression expression, final Map<String, String> imports) {
        this.expression = Preconditions.checkNotNull(expression);
        this.imports    = imports;
    }

    public boolean evaluate() {
        if (isConstantExpression) {
            return constant;
        }
        throw new IllegalStateException(BOOL_NOT_IMMUTABLE);
    }

    public Mono<Val> evaluateExpression() {
        Flux<Val> resultFlux = isConstantExpression ? Flux.just(Val.of(constant))
                : expression.evaluate().contextWrite(ctx -> AuthorizationContext.setImports(ctx, imports));
        return resultFlux.map(result -> result.isError() || result.isBoolean() ? result
                : ErrorFactory.error("Canonical Index Lookup: expression not boolean")).next();
    }

    public boolean isImmutable() {
        return isConstantExpression;
    }

    @Override
    public int hashCode() {
        if (!hasHashCode) {
            int h = 7;
            h = 59 * h + Objects.hashCode(isConstantExpression);
            if (isConstantExpression) {
                h = 59 * h + Objects.hashCode(constant);
            } else {
                h = 59 * h + EquivalenceAndHashUtil.semanticHash(expression, imports);
            }
            hash        = h;
            hasHashCode = true;
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Bool other = (Bool) obj;
        if (!Objects.equals(isConstantExpression, other.isConstantExpression)) {
            return false;
        }
        if (hashCode() != other.hashCode()) {
            return false;
        }
        if (isConstantExpression) {
            return Objects.equals(constant, other.constant);
        } else {
            return EquivalenceAndHashUtil.areEquivalent(expression, imports, other.expression, other.imports);
        }
    }

}
