/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class PolicyRetrievalPointTests {

    static class TestPRP implements PolicyRetrievalPoint {

        @Override
        public Flux<PolicyRetrievalResult> retrievePolicies() {
            return Flux.empty();
        }

    }

    @Test
    void when_destroy_then_nothingThrown() {
        var sut = new TestPRP();
        assertDoesNotThrow(() -> sut.destroy());
    }
}
