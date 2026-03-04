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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SelectionRange;

import io.sapl.lsp.core.ParsedDocument;
import io.sapl.lsp.core.SelectionRangeBuilder;
import lombok.val;

/**
 * Provides selection ranges for SAPLTest documents.
 */
class SAPLTestSelectionRangeProvider {

    List<SelectionRange> provideSelectionRanges(ParsedDocument document, List<Position> positions) {
        if (!(document instanceof SAPLTestParsedDocument saplTestDocument)) {
            return List.of();
        }
        val tree    = saplTestDocument.getSaplTestParseTree();
        val results = new ArrayList<SelectionRange>();
        for (val position : positions) {
            val selectionRange = SelectionRangeBuilder.buildSelectionRange(tree, position);
            if (selectionRange != null) {
                results.add(selectionRange);
            }
        }
        return results;
    }

}
