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
package io.sapl.lsp.sapltest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.lsp.sapl.ProtocolText;
import lombok.val;

@DisplayName("SAPLTest diagnostics provider")
class SAPLTestDiagnosticsProviderTests {

    private final SAPLTestDiagnosticsProvider provider = new SAPLTestDiagnosticsProvider();

    @Test
    @DisplayName("an error without offending text still marks one character, so the editor shows it")
    void whenAnErrorHasNoOffendingTextThenTheDiagnosticMarksOneCharacter() {
        // The lexer reports a text that is not closed without a token, so the error
        // names no offending text.
        val content  = "requirement \"unclosed";
        val document = new SAPLTestParsedDocument("test.sapltest", content);

        val diagnostics = provider.provideDiagnostics(document);

        assertThat(diagnostics).first().extracting(diagnostic -> ProtocolText.textOf(content, diagnostic.getRange()))
                .isEqualTo("\"");
    }

}
