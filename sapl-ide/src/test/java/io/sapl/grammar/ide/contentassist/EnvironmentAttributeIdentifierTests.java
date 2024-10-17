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
package io.sapl.grammar.ide.contentassist;

import java.util.List;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class EnvironmentAttributeIdentifierTests extends CompletionTests {

    @Test
    void testCompletion_PolicyBody_environmentattribute_without_import() {
        final var policy   = """
                policy "test" deny where <t#""";
        final var expected = List.of("temperature.now>", "temperature.mean(a1, a2)>", "temperature.predicted(a1)>");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_NotPolicyBody_environmentattribute_without_import() {
        final var policy = """
                policy "test" deny <t#""";
        assertProposalsEmpty(policy);
    }

    @Test
    void testCompletion_PolicyBody_environmentattribute_with_wildcardimport() {
        final var policy   = """
                import temperature.*
                policy "test" deny where <m#""";
        final var expected = List.of("mean(a1, a2)>");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_environmentattribute_with_import() {
        final var policy   = """
                import temperature.predicted
                policy "test" deny where <p#""";
        final var expected = List.of("predicted(a1)>");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_environmentattribute_with_alias() {
        final var policy   = """
                import temperature as weather
                policy "test" deny where <w#""";
        final var expected = List.of("weather.now>", "weather.mean(a1, a2)>", "weather.predicted(a1)>");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_atBracketEmpty() {
        final var policy = """
                policy "test" deny where <#""";
        assertProposalsEmpty(policy);
    }

    @Test
    void testCompletion_PolicyBody_environmentheadattribute_without_import() {
        final var policy   = """
                policy "test" deny where |<t#""";
        final var expected = List.of("temperature.now>", "temperature.mean(a1, a2)>", "temperature.predicted(a1)>");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_environmentheadattribute_with_wildcardimport() {
        final var policy   = """
                import temperature.*
                policy "test" deny where |<m#""";
        final var expected = List.of("mean(a1, a2)>");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_environmentheadattribute_with_import() {
        final var policy   = """
                import temperature.predicted
                policy "test" deny where |<p#""";
        final var expected = List.of("predicted(a1)>");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_environmentheadattribute_with_alias() {
        final var policy   = """
                import temperature as weather
                policy "test" deny where |<w#""";
        final var expected = List.of("weather.now>", "weather.mean(a1, a2)>", "weather.predicted(a1)>");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_atHeadBracketEmpty() {
        final var policy = """
                policy "test" deny where |<#""";
        assertProposalsEmpty(policy);
    }

}
