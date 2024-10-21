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
class FunctionIdentifierTests extends CompletionTests {

    @Test
    void testCompletion_documentBody_functionId_without_import() {
        final var document = """
                policy "test" deny where schem§""";
        final var expected = List.of("schemaTest.person(name, nationality, age)", "schemaTest.dog(dogRegistryRecord)",
                "schemaTest.food(species)", "schemaTest.foodPrice(food)", "schemaTest.location()");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_TargetExpression_functionId_without_import() {
        final var document = """
                policy "test" deny schem§ where""";
        final var expected = List.of("schemaTest.person(name, nationality, age)", "schemaTest.dog(dogRegistryRecord)",
                "schemaTest.food(species)", "schemaTest.foodPrice(food)", "schemaTest.location()");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_schemaExpression_functionId_without_import() {
        final var document = """
                subject schema enforced { "key": schem§ }
                policy "test" deny where""";
        final var expected = List.of("schemaTest.person(name, nationality, age)", "schemaTest.dog(dogRegistryRecord)",
                "schemaTest.food(species)", "schemaTest.foodPrice(food)", "schemaTest.location()");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_documentBody_functionId_without_import_longerPrefix() {
        final var document = """
                policy "test" deny where schemaTest§""";
        final var expected = List.of(".person(name, nationality, age)", ".dog(dogRegistryRecord)", ".food(species)",
                ".foodPrice(food)", ".location()");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_documentBody_functionId_without_import_longerPrefix_after_dot() {
        final var document = """
                policy "test" deny where schemaTest.§""";
        final var expected = List.of(".person(name, nationality, age)", ".dog(dogRegistryRecord)", ".food(species)",
                ".foodPrice(food)", ".location()");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_documentBody_functionId_without_import_longerPrefix_after_dot_and_fragment() {
        final var document = """
                policy "test" deny where schemaTest.f§""";
        final var expected = List.of("food(species)", "foodPrice(food)");
        assertProposalsContain(document, expected);
    }

}
