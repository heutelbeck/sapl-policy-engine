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

@DisplayName("SaplTestDocument tests")
class SaplTestDocumentTests {

    @Test
    @DisplayName("record constructor stores all fields")
    void whenCreatingWithConstructor_thenAllFieldsAreStored() {
        var doc = new SaplTestDocument("id1", "name1", "requirement \"test\" {}");

        assertThat(doc.id()).isEqualTo("id1");
        assertThat(doc.name()).isEqualTo("name1");
        assertThat(doc.sourceCode()).isEqualTo("requirement \"test\" {}");
    }

    @Test
    @DisplayName("of factory with name and source creates document")
    void whenUsingOfFactory_thenIdEqualsName() {
        var doc = SaplTestDocument.of("myTests", "requirement \"myTests\" {}");

        assertThat(doc.id()).isEqualTo("myTests");
        assertThat(doc.name()).isEqualTo("myTests");
        assertThat(doc.sourceCode()).isEqualTo("requirement \"myTests\" {}");
    }
}
