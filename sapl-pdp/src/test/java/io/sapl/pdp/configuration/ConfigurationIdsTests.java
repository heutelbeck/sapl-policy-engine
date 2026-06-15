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
package io.sapl.pdp.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("ConfigurationIds")
class ConfigurationIdsTests {

    @Test
    @DisplayName("generates a prefixed id whose tail is a parseable ISO instant and UUID")
    void whenGenerateThenPrefixedIsoTimestampAndUuid() {
        val id       = ConfigurationIds.generate("config");
        val uuidPart = id.substring(id.length() - 36);
        val isoPart  = id.substring("config-".length(), id.length() - 37);
        assertThat(id).startsWith("config-");
        assertThatCode(() -> UUID.fromString(uuidPart)).doesNotThrowAnyException();
        assertThatCode(() -> Instant.parse(isoPart)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("never collides even when many ids are generated in the same millisecond")
    void whenGeneratedRapidlyThenAllIdsAreUnique() {
        val ids = new HashSet<String>();
        IntStream.range(0, 10000).forEach(ignored -> ids.add(ConfigurationIds.generate("bundle")));
        assertThat(ids).hasSize(10000);
    }
}
