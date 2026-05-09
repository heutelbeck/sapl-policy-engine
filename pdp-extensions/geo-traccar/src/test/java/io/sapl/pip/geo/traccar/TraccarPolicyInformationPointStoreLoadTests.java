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
package io.sapl.pip.geo.traccar;

import io.sapl.api.stream.BlockingWebClient;
import io.sapl.attributes.store.InMemoryAttributeStore;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight registration test: confirms that the Traccar PIP can
 * be loaded into an {@link InMemoryAttributeStore} without errors.
 * The functional behaviour against a real Traccar server is covered
 * by {@link TraccarPolicyInformationPointIT}.
 */
@DisplayName("TraccarPolicyInformationPoint store registration")
class TraccarPolicyInformationPointStoreLoadTests {

    @Test
    @DisplayName("loads under the traccar namespace without errors")
    void whenLoadedIntoStoreThenRegistersUnderTraccarNamespace() {
        try (val store = new InMemoryAttributeStore()) {
            val webClient = BlockingWebClient.withDefaults(JsonMapper.builder().build());
            val handle    = store.load(new TraccarPolicyInformationPoint(webClient));

            assertThat(handle.pipName()).isEqualTo(TraccarPolicyInformationPoint.NAME);
            assertThat(handle.isLoaded()).isTrue();
            assertThat(store.catalog()).containsExactly(handle);
        }
    }
}
