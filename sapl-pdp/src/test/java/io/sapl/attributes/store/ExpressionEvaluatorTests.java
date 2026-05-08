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

import io.sapl.api.model.Poll;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExpressionEvaluator end-to-end against AttributeStore")
class ExpressionEvaluatorTests {

    @Test
    @DisplayName("VT: pure expression delivers one value then completes")
    void vtEvaluatorDeliversPureExpressionOnce() throws InterruptedException {
        try (val store = new TestAttributeStore(); val stream = VTExpressionEvaluator.evaluate("1 + 2", store)) {
            assertThat(stream.awaitNext()).isEqualTo(Value.of(3));
            assertThat(stream.awaitNext()).isNull();
        }
    }

    @Test
    @DisplayName("VT: registered PIP delivers one value per publish; tryNext empty before first publish")
    void vtEvaluatorDeliversPerStreamPublishWhenPipRegistered() throws InterruptedException {
        try (val store = new TestAttributeStore()) {
            store.register("test.attr");
            try (val stream = VTExpressionEvaluator.evaluate("<test.attr>", store)) {
                assertThat(stream.tryNext()).isEqualTo(Poll.empty());

                store.publishByName("test.attr", Value.of("first"));
                assertThat(stream.awaitNext()).isEqualTo(Value.of("first"));
                assertThat(stream.tryNext()).isEqualTo(Poll.empty());

                store.publishByName("test.attr", Value.of("second"));
                assertThat(stream.awaitNext()).isEqualTo(Value.of("second"));
            }
        }
    }

    @Test
    @DisplayName("VT: registered PIP with primed initial value fires gate immediately on open")
    void vtEvaluatorDeliversPrimedValueOnOpen() throws InterruptedException {
        try (val store = new TestAttributeStore()) {
            store.register("test.attr", Value.of("primed"));
            try (val stream = VTExpressionEvaluator.evaluate("<test.attr>", store)) {
                assertThat(stream.awaitNext()).isEqualTo(Value.of("primed"));
                assertThat(stream.tryNext()).isEqualTo(Poll.empty());

                store.publishByName("test.attr", Value.of("updated"));
                assertThat(stream.awaitNext()).isEqualTo(Value.of("updated"));
            }
        }
    }

    @Test
    @DisplayName("VT: unregistered attribute delivers UNDEFINED on open")
    void vtEvaluatorDeliversUndefinedWhenPipNotRegistered() throws InterruptedException {
        try (val store = new TestAttributeStore(); val stream = VTExpressionEvaluator.evaluate("<test.attr>", store)) {
            assertThat(stream.awaitNext()).isEqualTo(Value.UNDEFINED);
        }
    }
}
