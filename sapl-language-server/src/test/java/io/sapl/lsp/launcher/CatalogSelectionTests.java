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
package io.sapl.lsp.launcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.documentation.LibraryType;
import io.sapl.api.documentation.ParameterDocumentation;
import io.sapl.lsp.server.SAPLLanguageServer;
import lombok.val;

@DisplayName("Host catalog selection at the real embedded language server")
class CatalogSelectionTests {
    private static final String URI = "file:///ordinary/catalog.sapl";

    @Test
    @DisplayName("an absent catalog uses local classes while an empty catalog offers no libraries")
    void whenCatalogIsAbsentOrEmptyThenTheSelectionsAreDistinct() throws Exception {
        val standard = initialized(null);
        val empty    = initialized(new DocumentationBundle(List.of()));
        assertThat(labels(standard, "policy \"p\" permit json.\u00a7;", CompletionItemKind.Function))
                .containsExactlyInAnyOrder("json.jsonToVal", "json.valToJson");
        assertThat(labels(empty, "policy \"p\" permit json.\u00a7;", CompletionItemKind.Function)).isEmpty();
    }

    @Test
    @DisplayName("host metadata drives names and declared fields without loading plugins")
    void whenHostsChooseDifferentCatalogsThenTheirMetadataAndLocalSchemaFunctionsAreIndependent() throws Exception {
        val desk  = initialized(deskCatalog());
        val empty = initialized(new DocumentationBundle(List.of()));
        assertThat(labels(desk, "policy \"p\" permit desk.\u00a7;", CompletionItemKind.Function))
                .containsExactly("desk.lookup");
        assertThat(labels(empty, "policy \"p\" permit desk.\u00a7;", CompletionItemKind.Function)).isEmpty();
        assertThat(labels(desk, "policy \"p\" permit json.\u00a7;", CompletionItemKind.Function)).isEmpty();
        assertThat(labels(desk, "policy \"p\" permit desk.lookup(1).\u00a7;", CompletionItemKind.Property))
                .containsExactly("answer");
        assertThat(labels(empty, "policy \"p\" permit desk.lookup(1).\u00a7;", CompletionItemKind.Property)).isEmpty();
        for (val server : List.of(desk, empty)) {
            for (val schema : List.of("{properties:{room:{}}}", "json.jsonToVal('{\"properties\":{\"room\":{}}}')")) {
                assertThat(labels(server, "subject schema " + schema + " policy \"p\" permit subject.\u00a7;",
                        CompletionItemKind.Variable)).containsExactly("subject.room");
            }
            for (val unknown : List.of("desk.schema()", "subject", "<desk.temperature>")) {
                assertThat(labels(server, "subject schema " + unknown + " policy \"p\" permit subject.\u00a7;",
                        CompletionItemKind.Variable)).isEmpty();
            }
        }
        assertThat(labels(desk, "policy \"p\" permit desk.\u00a7;", CompletionItemKind.Function))
                .containsExactly("desk.lookup");
    }

    private static SAPLLanguageServer initialized(DocumentationBundle documentation) throws Exception {
        val server = SAPLLanguageServerLauncher.createEmbeddedServer(documentation);
        server.initialize(new InitializeParams()).get(5, TimeUnit.SECONDS);
        return server;
    }

    private static List<String> labels(SAPLLanguageServer server, String marked, CompletionItemKind kind)
            throws Exception {
        val cursor = marked.indexOf('\u00a7');
        val text   = marked.replace("\u00a7", "");
        server.getTextDocumentService()
                .didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "sapl", 1, text)));
        val items = server.getTextDocumentService()
                .completion(new CompletionParams(new TextDocumentIdentifier(URI), new Position(0, cursor)))
                .get(5, TimeUnit.SECONDS).getLeft();
        return items.stream().filter(item -> item.getKind() == kind).map(CompletionItem::getLabel).toList();
    }

    private static DocumentationBundle deskCatalog() {
        val lookup = new EntryDocumentation(EntryType.FUNCTION, "lookup", "Desk details.",
                "{\"properties\":{\"answer\":{\"type\":\"string\"}}}", List.of(ParameterDocumentation.untyped("id")));
        return new DocumentationBundle(List.of(new LibraryDocumentation(LibraryType.FUNCTION_LIBRARY, "desk",
                "Local desk metadata.", "Local metadata only.", List.of(lookup))));
    }
}
