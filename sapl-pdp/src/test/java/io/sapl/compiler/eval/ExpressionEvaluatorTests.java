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
package io.sapl.compiler.eval;

import io.sapl.api.model.Value;
import io.sapl.attributes.store.TestAttributeStore;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExpressionEvaluator end-to-end against AttributeStore")
class ExpressionEvaluatorTests {

    @Test
    @DisplayName("Flux: pure expression emits one value and completes")
    void fluxEvaluatorEmitsPureExpressionOnce() {
        try (val store = new TestAttributeStore()) {
            StepVerifier.create(FluxExpressionEvaluator.evaluate("1 + 2", store)).expectNext(Value.of(3))
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("Flux: streaming attribute emits one value per publish; gate stays closed until first publish")
    void fluxEvaluatorEmitsPerStreamPublish() {
        try (val store = new TestAttributeStore()) {
            val flux = FluxExpressionEvaluator.evaluate("<test.attr>", store);
            StepVerifier.create(flux).then(() -> store.publishByName("test.attr", Value.of("first")))
                    .expectNext(Value.of("first")).then(() -> store.publishByName("test.attr", Value.of("second")))
                    .expectNext(Value.of("second")).thenCancel().verify(Duration.ofSeconds(2));
        }
    }

    @Test
    @DisplayName("VT: pure expression delivers one value then completes")
    void vtEvaluatorDeliversPureExpressionOnce() throws InterruptedException {
        try (val store = new TestAttributeStore(); val stream = VTExpressionEvaluator.evaluate("1 + 2", store)) {
            assertThat(stream.awaitNext()).isEqualTo(Value.of(3));
            assertThat(stream.awaitNext()).isNull();
        }
    }

    @Test
    @DisplayName("VT: streaming attribute delivers one value per publish; tryNext is empty before first publish")
    void vtEvaluatorDeliversPerStreamPublish() throws InterruptedException {
        try (val store = new TestAttributeStore(); val stream = VTExpressionEvaluator.evaluate("<test.attr>", store)) {
            assertThat(stream.tryNext()).isEmpty();

            store.publishByName("test.attr", Value.of("first"));
            assertThat(stream.awaitNext()).isEqualTo(Value.of("first"));
            assertThat(stream.tryNext()).isEmpty();

            store.publishByName("test.attr", Value.of("second"));
            assertThat(stream.awaitNext()).isEqualTo(Value.of("second"));
        }
    }
}
