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
package io.sapl.node;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import lombok.val;

@DisplayName("SaplNodeApplication native resource hints")
class SaplNodeApplicationNativeResourceHintsTests {

    @Test
    @DisplayName("the PDP HTTP OpenAPI YAML is registered for native images")
    void whenRegisteringNativeResourceHintsThenPdpHttpOpenApiYamlIsIncluded() {
        val hints = new RuntimeHints();

        new SaplNodeApplication.NativeResourceHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.resource().forResource("static/openapi/pdp-http.yaml")).accepts(hints);
    }
}
