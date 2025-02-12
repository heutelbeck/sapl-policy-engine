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

import java.util.List;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class AttributeStepIdentifierTests extends CompletionTests {

    @Test
    void testCompletion_NotPolicyBody_attributequery_without_import() {
        final var policy = """
                policy "test" deny subject.<p§""";
        assertProposalsEmpty(policy);
    }

    @Test
    void testCompletion_PolicyBody_attributequery_without_import() {
        final var document = """
                policy "test" deny where subject.<p§""";
        final var expected = List.of("person.age>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attributequery_without_import_longerprefix() {
        final var document = """
                policy "test" deny where subject.<person.a§""";
        final var expected = List.of("age>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attributequery_with_import() {
        final var document = """
                import person.age
                policy "test" deny where subject.<a§""";
        final var expected = List.of("age>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attributequery_with_wildcard() {
        final var document = """
                import person.*
                policy "test" deny where subject.<a§""";
        final var expected = List.of("age>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attributequery_with_alias_import() {
        final var document = """
                import person as humans
                policy "test" deny where subject.<h§""";
        final var expected = List.of("humans.age>");
        assertProposalsContain(document, expected);
    }

}
