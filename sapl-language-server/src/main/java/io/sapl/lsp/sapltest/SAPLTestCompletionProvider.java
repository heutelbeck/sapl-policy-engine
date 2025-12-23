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

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Position;

import io.sapl.lsp.configuration.ConfigurationManager;
import io.sapl.lsp.core.ParsedDocument;

/**
 * Provides code completions for SAPLTest documents.
 * Currently supports keyword completions.
 */
public class SAPLTestCompletionProvider {

    private static final List<String> STRUCTURE_KEYWORDS = List.of("requirement", "scenario", "given", "when", "then",
            "expect");
    private static final List<String> DECISION_KEYWORDS  = List.of("permit", "deny", "indeterminate", "not-applicable",
            "decision");
    private static final List<String> MOCK_KEYWORDS      = List.of("function", "attribute", "maps", "to", "emits",
            "stream", "virtual-time", "error", "once", "times", "is", "called", "timing", "of");
    private static final List<String> IMPORT_KEYWORDS    = List.of("pip", "static-pip", "function-library",
            "static-function-library");
    private static final List<String> PDP_KEYWORDS       = List.of("pdp", "variables", "combining-algorithm",
            "configuration", "policy", "set", "policies");
    private static final List<String> ALGORITHM_KEYWORDS = List.of("deny-overrides", "permit-overrides",
            "only-one-applicable", "deny-unless-permit", "permit-unless-deny");
    private static final List<String> AUTH_KEYWORDS      = List.of("subject", "action", "resource", "environment",
            "attempts", "on", "in");
    private static final List<String> MATCHER_KEYWORDS   = List.of("matching", "any", "equals", "containing", "key",
            "value", "with", "where", "and");
    private static final List<String> TYPE_KEYWORDS      = List.of("null", "text", "number", "boolean", "array",
            "object");
    private static final List<String> STRING_KEYWORDS    = List.of("blank", "empty", "null-or-empty", "null-or-blank",
            "equal", "compressed", "whitespace", "case-insensitive", "regex", "starting", "ending", "length", "order");
    private static final List<String> EXPECT_KEYWORDS    = List.of("no-event", "for", "wait", "obligation", "advice",
            "obligations");
    private static final List<String> LITERAL_KEYWORDS   = List.of("true", "false", "undefined");

    /**
     * Provides completion items for a position in a document.
     *
     * @param document the parsed document
     * @param position the cursor position
     * @param configurationManager the configuration manager
     * @return list of completion items
     */
    public List<CompletionItem> provideCompletions(ParsedDocument document, Position position,
            ConfigurationManager configurationManager) {
        var completions = new ArrayList<CompletionItem>();

        // Add structure keywords
        STRUCTURE_KEYWORDS.forEach(kw -> completions.add(createKeywordCompletion(kw, "Structure keyword")));

        // Add decision keywords
        DECISION_KEYWORDS.forEach(kw -> completions.add(createKeywordCompletion(kw, "Decision type")));

        // Add mock keywords
        MOCK_KEYWORDS.forEach(kw -> completions.add(createKeywordCompletion(kw, "Mock definition keyword")));

        // Add import keywords
        IMPORT_KEYWORDS.forEach(kw -> completions.add(createKeywordCompletion(kw, "Import type")));

        // Add PDP keywords
        PDP_KEYWORDS.forEach(kw -> completions.add(createKeywordCompletion(kw, "PDP configuration keyword")));

        // Add algorithm keywords
        ALGORITHM_KEYWORDS.forEach(kw -> completions.add(createKeywordCompletion(kw, "Combining algorithm")));

        // Add authorization keywords
        AUTH_KEYWORDS.forEach(kw -> completions.add(createKeywordCompletion(kw, "Authorization keyword")));

        // Add matcher keywords
        MATCHER_KEYWORDS.forEach(kw -> completions.add(createKeywordCompletion(kw, "Matcher keyword")));

        // Add type keywords
        TYPE_KEYWORDS.forEach(kw -> completions.add(createKeywordCompletion(kw, "Type matcher")));

        // Add string matcher keywords
        STRING_KEYWORDS.forEach(kw -> completions.add(createKeywordCompletion(kw, "String matcher")));

        // Add expectation keywords
        EXPECT_KEYWORDS.forEach(kw -> completions.add(createKeywordCompletion(kw, "Expectation keyword")));

        // Add literal keywords
        LITERAL_KEYWORDS.forEach(kw -> completions.add(createKeywordCompletion(kw, "Literal value")));

        return completions;
    }

    /**
     * Creates a completion item for a keyword.
     *
     * @param keyword the keyword
     * @param detail the detail description
     * @return the completion item
     */
    private CompletionItem createKeywordCompletion(String keyword, String detail) {
        var item = new CompletionItem(keyword);
        item.setKind(CompletionItemKind.Keyword);
        item.setDetail(detail);
        return item;
    }

}
