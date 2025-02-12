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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Mono;

class PredicateTests {

    @Test
    void testConstruction() {
        final var predicate = new Predicate(new Bool(true));
        assertThat(predicate, is(notNullValue()));
        assertThat(predicate.getBool().evaluate(), is(true));
    }

    @Test
    void testEvaluate() {
        final var boolMock = mock(Bool.class);
        when(boolMock.evaluateExpression()).thenReturn(Mono.just(Val.TRUE));

        final var predicate = new Predicate(boolMock);
        final var result    = predicate.evaluate().block();
        assertThat(result.getBoolean(), is(true));
    }

}
