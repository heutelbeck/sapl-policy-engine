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

import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.lsp.configuration.ConfigurationManager;

@DisplayName("SAPL hover provider")
class SAPLHoverProviderTests {

    private final SAPLHoverProvider    provider             = new SAPLHoverProvider();
    private final ConfigurationManager configurationManager = new ConfigurationManager();

    @Nested
    @DisplayName("function hover")
    class FunctionHover {

        @Test
        @DisplayName("returns hover for known standard function")
        void whenHoveringOverStandardFunction_thenReturnsHover() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                        standard.length("hello") > 3;
                    """);

            var hover = provider.provideHover(document, new Position(2, 14), configurationManager);

            assertThat(hover).isNotNull();
            assertThat(hover.getContents().getRight().getValue()).contains("length");
        }

        @Test
        @DisplayName("returns null for cursor on keyword")
        void whenHoveringOverKeyword_thenReturnsNull() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                    """);

            var hover = provider.provideHover(document, new Position(1, 2), configurationManager);

            assertThat(hover).isNull();
        }

    }

    @Nested
    @DisplayName("attribute hover")
    class AttributeHover {

        @Test
        @DisplayName("returns hover for known environment attribute")
        void whenHoveringOverEnvironmentAttribute_thenReturnsHover() {
            var document = new SAPLParsedDocument("test.sapl", """
                    policy "test"
                    permit
                        <time.now> != undefined;
                    """);

            var hover = provider.provideHover(document, new Position(2, 10), configurationManager);

            assertThat(hover).isNotNull();
            assertThat(hover.getContents().getRight().getValue()).contains("now");
        }

    }

}
