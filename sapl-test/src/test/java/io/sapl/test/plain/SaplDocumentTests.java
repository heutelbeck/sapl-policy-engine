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
package io.sapl.test.plain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SaplDocument tests")
class SaplDocumentTests {

    @Test
    @DisplayName("record constructor stores all fields")
    void whenCreatingWithConstructor_thenAllFieldsAreStored() {
        var doc = new SaplDocument("id1", "name1", "policy \"test\" permit", "/path/to/file.sapl");

        assertThat(doc).satisfies(d -> {
            assertThat(d.id()).isEqualTo("id1");
            assertThat(d.name()).isEqualTo("name1");
            assertThat(d.sourceCode()).isEqualTo("policy \"test\" permit");
            assertThat(d.filePath()).isEqualTo("/path/to/file.sapl");
        });
    }

    @Test
    @DisplayName("of factory with name and source creates document without path")
    void whenUsingOfWithTwoArgs_thenIdEqualsNameAndPathIsNull() {
        var doc = SaplDocument.of("myPolicy", "policy \"myPolicy\" deny");

        assertThat(doc).satisfies(d -> {
            assertThat(d.id()).isEqualTo("myPolicy");
            assertThat(d.name()).isEqualTo("myPolicy");
            assertThat(d.sourceCode()).isEqualTo("policy \"myPolicy\" deny");
            assertThat(d.filePath()).isNull();
        });
    }

    @Test
    @DisplayName("of factory with name, source and path creates complete document")
    void whenUsingOfWithThreeArgs_thenIdEqualsNameAndPathIsSet() {
        var doc = SaplDocument.of("myPolicy", "policy \"myPolicy\" deny", "/path/to/myPolicy.sapl");

        assertThat(doc).satisfies(d -> {
            assertThat(d.id()).isEqualTo("myPolicy");
            assertThat(d.name()).isEqualTo("myPolicy");
            assertThat(d.sourceCode()).isEqualTo("policy \"myPolicy\" deny");
            assertThat(d.filePath()).isEqualTo("/path/to/myPolicy.sapl");
        });
    }

    @Test
    @DisplayName("null file path is allowed")
    void whenCreatingWithNullPath_thenPathIsNull() {
        var doc = new SaplDocument("id", "name", "code", null);

        assertThat(doc.filePath()).isNull();
    }
}
