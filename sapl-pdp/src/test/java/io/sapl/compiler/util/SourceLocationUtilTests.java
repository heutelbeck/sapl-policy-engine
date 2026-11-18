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
package io.sapl.compiler.util;

import io.sapl.api.model.SourceLocation;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.util.PlacedDocuments.Placed;
import lombok.val;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.util.PlacedDocuments.GRINNING_FACE;
import static io.sapl.util.PlacedDocuments.documentOf;
import static io.sapl.util.PlacedDocuments.placedWithEveryLineEnd;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SourceLocationUtil")
class SourceLocationUtilTests {

    private static final String DOCUMENT = "policy \"test\" permit";

    @Test
    @DisplayName("all source locations from one parse share a single document-source string instance")
    void whenMultipleLocationsFromSameDocumentThenDocumentSourceIsShared() {
        val root  = parse(DOCUMENT);
        val child = root.policyElement();

        val rootLocation  = SourceLocationUtil.fromContext(root);
        val childLocation = SourceLocationUtil.fromContext(child);

        assertThat(rootLocation.documentSource()).isSameAs(childLocation.documentSource()).isEqualTo(DOCUMENT);
    }

    @Nested
    @DisplayName("the place of a construct in a document")
    class PlaceOfAConstructTests {

        /**
         * Documents whose third line holds two variable definitions, and where the
         * second one stands.
         */
        static Stream<Placed> secondVariableDefinitions() {
            val plain        = "var mood = 2";
            val afterTheFace = "var mood = \"sad\"";
            val holdsTheFace = "var mood = \"" + GRINNING_FACE + "\"";
            val plainLine    = "    var mood = 1; " + plain + ";";
            val faceBefore   = "    var mood = \"" + GRINNING_FACE + "\"; " + afterTheFace + ";";
            val faceInside   = "    var mood = 1; " + holdsTheFace + ";";
            return Stream.concat(placedWithEveryLineEnd(plainLine, plain, 3, 19, 3, 31),
                    Stream.of(
                            new Placed("a character outside the basic plane before the construct, on its line",
                                    documentOf("\n", faceBefore), afterTheFace, 3, 21, 3, 37),
                            new Placed("a character outside the basic plane inside the construct",
                                    documentOf("\n", faceInside), holdsTheFace, 3, 19, 3, 33)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("secondVariableDefinitions")
        @DisplayName("its offsets address its text in the document")
        void whenConstructIsLocatedThenItsOffsetsAddressItsTextInTheDocument(Placed placed) {
            val location = locationOfSecondStatement(placed.document());

            assertThat(placed.document().substring(location.start(), location.end())).isEqualTo(placed.text());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("secondVariableDefinitions")
        @DisplayName("its lines and columns count from 1, and a column counts whole characters")
        void whenConstructIsLocatedThenItsLinesAndColumnsCountWholeCharactersFromOne(Placed placed) {
            val location = locationOfSecondStatement(placed.document());

            assertThat(location)
                    .extracting(SourceLocation::line, SourceLocation::column, SourceLocation::endLine,
                            SourceLocation::endColumn)
                    .containsExactly(placed.line(), placed.column(), placed.endLine(), placed.endColumn());
        }

        private static SourceLocation locationOfSecondStatement(String document) {
            val policy = ((PolicyOnlyElementContext) parse(document).policyElement()).policy();
            return SourceLocationUtil.fromContext(policy.policyBody().statements.get(1));
        }
    }

    private static SAPLParser.SaplContext parse(String source) {
        val lexer  = new SAPLLexer(CharStreams.fromString(source));
        val parser = new SAPLParser(new CommonTokenStream(lexer));
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        return parser.sapl();
    }
}
