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
package io.sapl.grammar.sapl.impl;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterExtended;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import io.sapl.grammar.sapl.impl.util.FilterAlgorithmUtil;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Function;

public class FilterExtendedImplCustom extends FilterExtendedImpl {

    private static final String FILTERS_CANNOT_BE_APPLIED_TO_UNDEFINED_VALUES_ERROR = "Filters cannot be applied to undefined values.";

    @Override
    public Flux<Val> apply(Val unfilteredValue) {
        if (unfilteredValue.isError()) {
            return Flux.just(unfilteredValue.withTrace(FilterExtended.class, true,
                    Map.of(Trace.UNFILTERED_VALUE, unfilteredValue)));
        }
        if (unfilteredValue.isUndefined()) {
            return Flux.just(ErrorFactory.error(this, FILTERS_CANNOT_BE_APPLIED_TO_UNDEFINED_VALUES_ERROR)
                    .withTrace(FilterExtended.class, true, Map.of(Trace.UNFILTERED_VALUE, unfilteredValue)));
        }
        if (statements == null) {
            return Flux.just(unfilteredValue.withTrace(FilterExtended.class, true,
                    Map.of(Trace.UNFILTERED_VALUE, unfilteredValue)));
        }
        return Flux.just(unfilteredValue).switchMap(applyFilterStatements());
    }

    private Function<? super Val, Publisher<? extends Val>> applyFilterStatements() {
        return applyFilterStatements(0);
    }

    private Function<? super Val, Publisher<? extends Val>> applyFilterStatements(int statementId) {
        if (statementId == statements.size()) {
            return Flux::just;
        }

        return value -> applyFilterStatement(value, statements.get(statementId))
                .switchMap(applyFilterStatements(statementId + 1));
    }

    private Flux<Val> applyFilterStatement(Val unfilteredValue, FilterStatement statement) {
        if (statement.getTarget().getSteps().isEmpty()) {
            // the expression has no steps. apply filter to unfiltered node directly
            return FilterAlgorithmUtil.applyFilterFunction(unfilteredValue, statement.getArguments(),
                    statement.getIdentifier(), statement.isEach(), statement);
        } else {
            // descent with steps
            return statement.getTarget().getSteps().get(0).applyFilterStatement(unfilteredValue, 0, statement);
        }
    }

}
