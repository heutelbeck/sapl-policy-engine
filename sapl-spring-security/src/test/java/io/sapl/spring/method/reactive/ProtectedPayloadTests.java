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
package io.sapl.spring.method.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

class ProtectedPayloadTests {

    @Test
    void withPayload() {
        assertThatThrownBy(() -> ProtectedPayload.withPayload(null)).isInstanceOf(NullPointerException.class);
        var pp = ProtectedPayload.withPayload("Payload");
        assertThat(pp.isError()).isFalse();
        assertThat(pp.hasPayload()).isTrue();
        StepVerifier.create(pp.getPayload()).expectNext("Payload").verifyComplete();
        assertThatThrownBy(() -> pp.getError()).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void withException() {
        assertThatThrownBy(() -> ProtectedPayload.withError(null)).isInstanceOf(NullPointerException.class);
        var pp = ProtectedPayload.withError(new RuntimeException("ERROR"));
        assertThat(pp.isError()).isTrue();
        assertThat(pp.hasPayload()).isFalse();
        assertThatThrownBy(() -> pp.getPayload()).isInstanceOf(RuntimeException.class);
        assertThat(pp.getError()).hasMessage("ERROR");
    }
}
