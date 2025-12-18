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
package io.sapl.compiler;

import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import lombok.val;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceLocationUtilTests {

    @Test
    void whenNullContext_thenReturnsNull() {
        assertThat(SourceLocationUtil.fromContext(null)).isNull();
    }

    @Test
    void whenValidContext_thenExtractsLocation() {
        val source  = "policy \"test\" permit";
        val context = parsePolicy(source);

        val location = SourceLocationUtil.fromContext(context);

        assertThat(location).isNotNull();
        assertThat(location.line()).isEqualTo(1);
        assertThat(location.start()).isZero();
        assertThat(location.documentSource()).isEqualTo(source);
    }

    @Test
    void whenPolicyHasName_thenExtractsDocumentName() {
        val source  = "policy \"my-policy\" permit";
        val context = parsePolicy(source);

        val location = SourceLocationUtil.fromContext(context);

        assertThat(location).isNotNull();
        assertThat(location.documentName()).isEqualTo("my-policy");
    }

    @Test
    void whenPolicySet_thenExtractsSetName() {
        val source  = """
                set "my-set" deny-overrides
                policy "inner" permit
                """;
        val context = parsePolicy(source);

        val location = SourceLocationUtil.fromContext(context);

        assertThat(location).isNotNull();
        assertThat(location.documentName()).isEqualTo("my-set");
    }

    @Test
    void whenExpressionContext_thenExtractsLocation() {
        val source           = "policy \"test\" permit subject == \"admin\"";
        val context          = parsePolicy(source);
        val policyElement    = (PolicyOnlyElementContext) context.policyElement();
        val targetExpression = policyElement.policy().targetExpression;

        val location = SourceLocationUtil.fromContext(targetExpression);

        assertThat(location).isNotNull();
        assertThat(location.line()).isEqualTo(1);
        assertThat(location.documentName()).isEqualTo("test");
    }

    @Test
    void whenNullToken_thenReturnsNull() {
        assertThat(SourceLocationUtil.fromToken(null, null)).isNull();
    }

    @Test
    void whenValidToken_thenExtractsLocation() {
        val source        = "policy \"test\" permit";
        val context       = parsePolicy(source);
        val policyElement = (PolicyOnlyElementContext) context.policyElement();
        val token         = policyElement.policy().saplName;

        val location = SourceLocationUtil.fromToken(token, context);

        assertThat(location).isNotNull();
        assertThat(location.line()).isEqualTo(1);
        assertThat(location.documentSource()).isEqualTo(source);
    }

    private SAPLParser.SaplContext parsePolicy(String source) {
        val charStream  = CharStreams.fromString(source);
        val lexer       = new SAPLLexer(charStream);
        val tokenStream = new CommonTokenStream(lexer);
        val parser      = new SAPLParser(tokenStream);
        return parser.sapl();
    }

}
