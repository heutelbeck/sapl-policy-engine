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
package io.sapl.grammar.sapl.impl.util;

import io.sapl.grammar.sapl.FilterStatement;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;

class FilterAlgorithmUtilTests {

    @Test
    void errorsAreNotFiltered() {
        final var unfiltered     = ErrorFactory.error("unfiltered");
        final var expected       = ErrorFactory.error("unfiltered");
        final var actualFiltered = FilterAlgorithmUtil.applyFilter(unfiltered, 0, null, mock(FilterStatement.class),
                getClass());
        StepVerifier.create(actualFiltered)
                .expectNextMatches(actual -> actual.equals(expected)
                        && "ConditionStep".equals(actual.getTrace().get("trace").get("operator").asText()))
                .verifyComplete();
    }
}
