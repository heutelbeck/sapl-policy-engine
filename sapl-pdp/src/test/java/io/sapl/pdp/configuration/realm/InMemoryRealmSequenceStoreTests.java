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
package io.sapl.pdp.configuration.realm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("InMemoryRealmSequenceStore")
class InMemoryRealmSequenceStoreTests {

    @Test
    @DisplayName("an unseen realm reports no accepted sequence")
    void whenRealmUnseenThenReturnsMinusOne() {
        assertThat(new InMemoryRealmSequenceStore().lastAcceptedSequence("acme")).isEqualTo(-1L);
    }

    @Test
    @DisplayName("a recorded sequence is returned and kept independently per realm")
    void whenSequencesRecordedThenReturnedPerRealm() {
        val store = new InMemoryRealmSequenceStore();
        store.recordAcceptedSequence("acme", 42L);
        store.recordAcceptedSequence("beta", 7L);

        assertThat(store.lastAcceptedSequence("acme")).isEqualTo(42L);
        assertThat(store.lastAcceptedSequence("beta")).isEqualTo(7L);
        assertThat(store.lastAcceptedSequence("gamma")).isEqualTo(-1L);
    }
}
