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
package io.sapl.lsp.sapl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SAPLParsedDocumentTests {

    @Test
    void whenValidPolicy_thenParsesWithoutErrors() {
        var content = """
                policy "test"
                permit
                """;

        var document = new SAPLParsedDocument("test.sapl", content);

        assertThat(document.hasErrors()).isFalse();
        assertThat(document.getParseTree()).isNotNull();
        assertThat(document.getTokens()).isNotEmpty();
    }

    @Test
    void whenSyntaxError_thenCapturesParseError() {
        var content = """
                policy
                permit
                """;

        var document = new SAPLParsedDocument("test.sapl", content);

        assertThat(document.hasErrors()).isTrue();
        assertThat(document.getParseErrors()).isNotEmpty();
    }

    @Test
    void whenLazyOperatorInTarget_thenCapturesValidationError() {
        var content = """
                policy "test"
                permit subject == "admin" || action == "read"
                """;

        var document = new SAPLParsedDocument("test.sapl", content);

        assertThat(document.getValidationErrors()).isNotEmpty();
        assertThat(document.getValidationErrors().getFirst().message()).contains("Lazy OR");
    }

    @Test
    void whenPolicySet_thenParsesSuccessfully() {
        var content = """
                set "test policies"
                first-applicable

                policy "policy 1"
                permit subject == "admin"

                policy "policy 2"
                deny
                """;

        var document = new SAPLParsedDocument("test.sapl", content);

        assertThat(document.hasErrors()).isFalse();
        assertThat(document.getParseTree()).isNotNull();
    }

    @Test
    void whenPolicyWithBody_thenParsesSuccessfully() {
        var content = """
                policy "complex policy"
                permit action == "read"
                where
                  var user = subject.name;
                  user == "admin";
                obligation "log access"
                advice "consider caching"
                transform { "allowed": true }
                """;

        var document = new SAPLParsedDocument("test.sapl", content);

        assertThat(document.hasErrors()).isFalse();
    }

    @Test
    void whenAttributeInTarget_thenCapturesValidationError() {
        var content = """
                policy "test"
                permit <time.now>
                """;

        var document = new SAPLParsedDocument("test.sapl", content);

        assertThat(document.getValidationErrors()).isNotEmpty();
        assertThat(document.getValidationErrors().getFirst().message()).contains("Attribute");
    }

}
