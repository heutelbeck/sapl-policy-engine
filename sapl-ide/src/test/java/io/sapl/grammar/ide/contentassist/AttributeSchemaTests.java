/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.ide.contentassist;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
class AttributeSchemaTests extends CompletionTests {

    @Test
    void testCompletion_PolicyBody_environmentAttribute() {
        final var document = """
                policy "test" deny where <temperature.now>.§""";
        final var expected = List.of("value", "unit");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_environmentAttribute_alias_import() {
        final var document = """
                import temperature.now as temp
                policy "test" deny where <temp>.§""";
        final var expected = List.of("value", "unit");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attributeStep() {
        final var document = """
                policy "test" deny where subject.<person.age>.§""";
        // TextEdit inserts after the dot, so proposals don't include the leading dot
        final var expected = List.of("years", "days");
        assertProposalsContain(document, expected);
    }
}
