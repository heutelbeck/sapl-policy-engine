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

import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import lombok.val;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    private static SAPLParser.SaplContext parse(String source) {
        val lexer  = new SAPLLexer(CharStreams.fromString(source));
        val parser = new SAPLParser(new CommonTokenStream(lexer));
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        return parser.sapl();
    }
}
