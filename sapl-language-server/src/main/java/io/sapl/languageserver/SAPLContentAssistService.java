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
package io.sapl.languageserver;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.contentassist.ContentAssistService;

/**
 * Custom content assist service that ensures documentation is sent to LSP
 * clients as markdown. By default, Xtext may send documentation as plain text,
 * which prevents proper rendering in IDEs like Eclipse (LSP4E) and IntelliJ.
 * This service explicitly sets the MarkupContent kind to MARKDOWN for all
 * completion item documentation.
 */
public class SAPLContentAssistService extends ContentAssistService {

    @Override
    protected CompletionItem toCompletionItem(ContentAssistEntry entry, int caretOffset, Position caretPosition,
            Document document) {
        var item = super.toCompletionItem(entry, caretOffset, caretPosition, document);

        var documentation = entry.getDocumentation();
        if (documentation != null && !documentation.isEmpty()) {
            var markupContent = new MarkupContent();
            markupContent.setKind(MarkupKind.MARKDOWN);
            markupContent.setValue(documentation);
            item.setDocumentation(markupContent);
        }
        return item;
    }

}
