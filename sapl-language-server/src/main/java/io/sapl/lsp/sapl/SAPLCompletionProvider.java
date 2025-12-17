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
package io.sapl.lsp.sapl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;

import io.sapl.api.documentation.EntryType;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser.BasicEnvironmentAttributeContext;
import io.sapl.grammar.antlr.SAPLParser.BasicEnvironmentHeadAttributeContext;
import io.sapl.grammar.antlr.SAPLParser.BasicFunctionContext;
import io.sapl.grammar.antlr.SAPLParser.CombiningAlgorithmContext;
import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.ImportStatementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyBodyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.grammar.antlr.SAPLParser.SchemaStatementContext;
import io.sapl.grammar.antlr.SAPLParser.StatementContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionContext;
import io.sapl.lsp.configuration.ConfigurationManager;
import io.sapl.lsp.configuration.LSPConfiguration;
import io.sapl.lsp.core.ParsedDocument;
import io.sapl.lsp.sapl.completion.ContextAnalyzer;
import io.sapl.lsp.sapl.completion.LibraryProposalsGenerator;
import io.sapl.lsp.sapl.completion.SnippetConverter;
import io.sapl.lsp.sapl.completion.VariablesProposalsGenerator;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides completion items for SAPL documents.
 * Context-aware keyword, function, attribute, and variable completions.
 */
@Slf4j
public class SAPLCompletionProvider {

    // Document-level keywords (before any policy/set)
    private static final Set<String> DOCUMENT_START_KEYWORDS = Set.of("import", "set", "policy");

    // After imports, before policy element
    private static final Set<String> AFTER_IMPORTS_KEYWORDS = Set.of("set", "policy");

    // Combining algorithms for policy sets
    private static final Set<String> COMBINING_ALGORITHMS = Set.of("deny-overrides", "permit-overrides",
            "first-applicable", "only-one-applicable", "deny-unless-permit", "permit-unless-deny");

    // After combining algorithm in policy set
    private static final Set<String> POLICY_SET_BODY_KEYWORDS = Set.of("for", "var", "policy");

    // After "policy name" - entitlement
    private static final Set<String> ENTITLEMENT_KEYWORDS = Set.of("permit", "deny");

    // After entitlement - policy structure keywords
    private static final Set<String> POLICY_STRUCTURE_KEYWORDS = Set.of("where", "obligation", "advice", "transform");

    // Inside policy body (after where) - statement-level
    private static final Set<String> BODY_STATEMENT_KEYWORDS = Set.of("var");

    // Expression-only keywords (inside any expression context)
    private static final Set<String> EXPRESSION_KEYWORDS = Set.of("true", "false", "null", "undefined", "in", "each");

    // Subscription variables available in expressions
    private static final Set<String> SUBSCRIPTION_VARIABLES = Set.of("subject", "action", "resource", "environment");

    /**
     * Provides completion items for a position in a document.
     */
    public List<CompletionItem> provideCompletions(ParsedDocument document, Position position,
            ConfigurationManager configurationManager) {
        var items   = new ArrayList<CompletionItem>();
        var context = analyzeContext(document, position);
        var config  = configurationManager.getConfigurationForUri(document.getUri());
        var prefix  = extractPrefix(document.getContent(), position);

        log.debug("Completion context: {}, prefix: '{}'", context, prefix);

        switch (context) {
        case DOCUMENT_START -> addKeywordCompletions(items, DOCUMENT_START_KEYWORDS);

        case AFTER_IMPORTS -> addKeywordCompletions(items, AFTER_IMPORTS_KEYWORDS);

        case IMPORT_PATH -> addImportCompletions(items, config);

        case COMBINING_ALGORITHM -> addKeywordCompletions(items, COMBINING_ALGORITHMS);

        case POLICY_SET_BODY -> {
            addKeywordCompletions(items, POLICY_SET_BODY_KEYWORDS);
            addExpressionCompletions(items, document, position, config);
        }

        case ENTITLEMENT -> addKeywordCompletions(items, ENTITLEMENT_KEYWORDS);

        case POLICY_AFTER_ENTITLEMENT -> {
            // After permit/deny - can have target expression or structure keywords
            addKeywordCompletions(items, POLICY_STRUCTURE_KEYWORDS);
            addExpressionCompletions(items, document, position, config);
        }

        case POLICY_BODY_START -> {
            // Start of where clause - var or expression
            addKeywordCompletions(items, BODY_STATEMENT_KEYWORDS);
            addExpressionCompletions(items, document, position, config);
        }

        case POLICY_BODY_AFTER_STATEMENT -> {
            // After a statement in body - can continue with var, expression, or end body
            addKeywordCompletions(items, BODY_STATEMENT_KEYWORDS);
            addKeywordCompletions(items, Set.of("obligation", "advice", "transform"));
            addExpressionCompletions(items, document, position, config);
        }

        case EXPRESSION -> addExpressionCompletions(items, document, position, config);

        case ENVIRONMENT_ATTRIBUTE -> addAttributeCompletions(items, config, true);

        case ATTRIBUTE -> addAttributeCompletions(items, config, false);

        case FUNCTION_CALL -> addFunctionCompletions(items, config);

        case UNKNOWN -> {
            // Fallback - provide expression completions only (safe default)
            addExpressionCompletions(items, document, position, config);
        }
        }

        // Filter items by prefix for expression contexts only (not structural)
        if (shouldFilterByPrefix(context, prefix)) {
            return filterByPrefix(items, prefix);
        }
        return items;
    }

    /**
     * Determines if prefix filtering should be applied for the given context.
     * Structural contexts (imports, keywords) should not filter by prefix
     * since the prefix may be a keyword being typed.
     */
    private boolean shouldFilterByPrefix(CompletionContext context, String prefix) {
        if (prefix.isEmpty()) {
            return false;
        }

        // Don't filter in structural contexts where prefix might be a keyword
        return switch (context) {
        case DOCUMENT_START, AFTER_IMPORTS, IMPORT_PATH, COMBINING_ALGORITHM,
                ENTITLEMENT                                                                                                                                         ->
            false;
        case EXPRESSION, FUNCTION_CALL, ENVIRONMENT_ATTRIBUTE, ATTRIBUTE, POLICY_SET_BODY, POLICY_AFTER_ENTITLEMENT,
                POLICY_BODY_START,
                POLICY_BODY_AFTER_STATEMENT                                                                                                                         ->
            !isKeyword(prefix);
        case UNKNOWN                                                                                                                                                ->
            false;
        };
    }

    /**
     * Checks if a string is a SAPL keyword that should not be used for filtering.
     */
    private boolean isKeyword(String text) {
        return DOCUMENT_START_KEYWORDS.contains(text) || AFTER_IMPORTS_KEYWORDS.contains(text)
                || COMBINING_ALGORITHMS.contains(text) || POLICY_SET_BODY_KEYWORDS.contains(text)
                || ENTITLEMENT_KEYWORDS.contains(text) || POLICY_STRUCTURE_KEYWORDS.contains(text)
                || BODY_STATEMENT_KEYWORDS.contains(text) || EXPRESSION_KEYWORDS.contains(text);
    }

    /**
     * Extracts the identifier prefix before the cursor position.
     * Returns the word characters (including dots for qualified names) before the
     * cursor, but only if it starts with a letter or underscore.
     * A prefix that starts with a dot (like after time.now().) returns empty
     * since the dot is a trigger character, not part of an identifier.
     */
    private String extractPrefix(String content, Position position) {
        var lines = content.split("\n", -1);
        if (position.getLine() >= lines.length) {
            return "";
        }

        var line   = lines[position.getLine()];
        var column = Math.min(position.getCharacter(), line.length());

        // Scan backwards to find the start of the identifier
        var start = column;
        while (start > 0) {
            var ch = line.charAt(start - 1);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.') {
                start--;
            } else {
                break;
            }
        }

        var prefix = line.substring(start, column);

        // If prefix starts with a dot, it's a trigger character context, not a filter
        // prefix
        // For example, after "time.now()." the prefix should be empty
        // But for "time.now().ye" the prefix should be "ye"
        if (prefix.startsWith(".")) {
            prefix = prefix.substring(1);
        }

        return prefix;
    }

    /**
     * Filters completion items by prefix.
     * Uses case-insensitive prefix matching on label or insertText.
     */
    private List<CompletionItem> filterByPrefix(List<CompletionItem> items, String prefix) {
        if (prefix.isEmpty()) {
            return items;
        }

        var lowerPrefix = prefix.toLowerCase();
        return items.stream().filter(item -> {
            var label = item.getLabel();
            var text  = item.getInsertText();

            // Match against label (primary) or insertText
            if (label != null && label.toLowerCase().startsWith(lowerPrefix)) {
                return true;
            }
            if (text != null && text.toLowerCase().startsWith(lowerPrefix)) {
                return true;
            }
            return false;
        }).toList();
    }

    /**
     * Adds completions appropriate for expression contexts.
     * This includes expression keywords, variables, functions, and schema
     * extensions after function/attribute calls.
     */
    private void addExpressionCompletions(List<CompletionItem> items, ParsedDocument document, Position position,
            LSPConfiguration config) {
        // Check if we're in a schema extension context (after function/attribute call)
        addSchemaExtensionCompletions(items, document, position, config);

        // Standard expression completions
        addKeywordCompletions(items, EXPRESSION_KEYWORDS);
        addVariableCompletions(items, document, position, config);
        addFunctionCompletions(items, config);
    }

    /**
     * Adds schema-based completions after function or attribute calls.
     * For example, after `time.now().` offers year, month, day, etc.
     */
    private void addSchemaExtensionCompletions(List<CompletionItem> items, ParsedDocument document, Position position,
            LSPConfiguration config) {
        var tokens   = document.getTokens();
        var sapl     = getSaplContext(document);
        var analysis = ContextAnalyzer.analyze(tokens, position);

        Set<String> schemaProposals = Set.of();

        switch (analysis.type()) {
        case FUNCTION              ->
            schemaProposals = LibraryProposalsGenerator.allFunctionSchemaExtensions(analysis, sapl, config);
        case ATTRIBUTE             ->
            schemaProposals = LibraryProposalsGenerator.allAttributeSchemaExtensions(analysis, sapl, config);
        case ENVIRONMENT_ATTRIBUTE ->
            schemaProposals = LibraryProposalsGenerator.allEnvironmentAttributeSchemaExtensions(analysis, sapl, config);
        default                    -> { /* No schema extensions */ }
        }

        for (var proposal : schemaProposals) {
            var item = new CompletionItem(proposal);
            item.setKind(CompletionItemKind.Property);
            item.setDetail("Schema property (from " + analysis.functionName() + ")");
            item.setInsertText(proposal);
            items.add(item);
        }
    }

    /**
     * Analyzes the context at the given position.
     */
    private CompletionContext analyzeContext(ParsedDocument document, Position position) {
        var tokens       = document.getTokens();
        var line         = position.getLine() + 1;
        var column       = position.getCharacter();
        var tokenContext = findTokensAroundCursor(tokens, line, column);

        // Token-based context detection (highest priority for specific triggers)
        if (tokenContext.tokenAtCursor() != null) {
            var context = analyzeTokenBasedContext(tokenContext, tokens, position);
            if (context != CompletionContext.UNKNOWN) {
                return context;
            }
        }

        // Parse tree based context detection
        var parseTree = document.getParseTree();
        if (parseTree != null) {
            var contextNode = findDeepestContextAtPosition(parseTree, line, column);
            if (contextNode != null) {
                return analyzeParseTreeContext(contextNode, line, column);
            }
        }

        // No tokens yet - document start
        if (tokenContext.tokenAtCursor() == null) {
            return CompletionContext.DOCUMENT_START;
        }

        // Default to expression context (safe - no structural keywords)
        return CompletionContext.EXPRESSION;
    }

    /**
     * Record to hold tokens around the cursor position.
     */
    private record TokenContext(Token tokenAtCursor, Token previousToken, Token twoTokensBack) {}

    /**
     * Finds the tokens around the cursor position.
     */
    private TokenContext findTokensAroundCursor(List<Token> tokens, int line, int column) {
        Token tokenAtCursor = null;
        Token previousToken = null;
        Token twoTokensBack = null;

        for (var token : tokens) {
            if (token.getType() == Token.EOF) {
                break;
            }
            if (token.getChannel() != Token.HIDDEN_CHANNEL) {
                if (token.getLine() < line || (token.getLine() == line && token.getCharPositionInLine() <= column)) {
                    twoTokensBack = previousToken;
                    previousToken = tokenAtCursor;
                    tokenAtCursor = token;
                } else {
                    break;
                }
            }
        }

        return new TokenContext(tokenAtCursor, previousToken, twoTokensBack);
    }

    /**
     * Analyzes token-based context detection.
     */
    private CompletionContext analyzeTokenBasedContext(TokenContext ctx, List<Token> tokens, Position position) {
        var tokenAtCursor = ctx.tokenAtCursor();
        var tokenType     = tokenAtCursor.getType();

        // Check for schema extension context (after function/attribute call)
        if (isSchemaExtensionContext(ctx)) {
            return CompletionContext.EXPRESSION;
        }

        // Check for attribute context
        if (isAttributeTrigger(tokenType)) {
            return CompletionContext.ENVIRONMENT_ATTRIBUTE;
        }

        // Check for import context
        if (isImportContext(ctx, tokens, position)) {
            return CompletionContext.IMPORT_PATH;
        }

        // Check for structural keyword contexts
        return analyzeStructuralKeywordContext(ctx);
    }

    /**
     * Checks if cursor is in schema extension context (after function/attribute
     * call).
     */
    private boolean isSchemaExtensionContext(TokenContext ctx) {
        var tokenAtCursor = ctx.tokenAtCursor();
        var previousToken = ctx.previousToken();
        var twoTokensBack = ctx.twoTokensBack();

        // After a dot following ) or > (e.g., `time.now().` or `<auth.user>.`)
        if (tokenAtCursor.getType() == SAPLLexer.DOT && previousToken != null) {
            var prevType = previousToken.getType();
            if (prevType == SAPLLexer.RPAREN || prevType == SAPLLexer.GT) {
                return true;
            }
        }

        // Typing after a dot following ) or > (e.g., `time.now().ye`)
        if (previousToken != null && previousToken.getType() == SAPLLexer.DOT && twoTokensBack != null) {
            var twoBackType = twoTokensBack.getType();
            if (twoBackType == SAPLLexer.RPAREN || twoBackType == SAPLLexer.GT) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if token type is an attribute trigger (< or |<).
     */
    private boolean isAttributeTrigger(int tokenType) {
        return tokenType == SAPLLexer.LT || tokenType == SAPLLexer.PIPE_LT;
    }

    /**
     * Checks if cursor is in import context.
     */
    private boolean isImportContext(TokenContext ctx, List<Token> tokens, Position position) {
        var tokenAtCursor = ctx.tokenAtCursor();
        var previousToken = ctx.previousToken();
        var tokenType     = tokenAtCursor.getType();

        // After import keyword
        if (tokenType == SAPLLexer.IMPORT) {
            return true;
        }

        // Typing after import (e.g., `import ti`)
        if (previousToken != null && previousToken.getType() == SAPLLexer.IMPORT) {
            return true;
        }

        // After dot in import path (e.g., `import time.`)
        if (tokenType == SAPLLexer.DOT && hasImportTokenInHistory(tokens, position)) {
            return true;
        }

        // Typing after dot in import path (e.g., `import time.no`)
        if (previousToken != null && previousToken.getType() == SAPLLexer.DOT
                && hasImportTokenInHistory(tokens, position)) {
            return true;
        }

        return false;
    }

    /**
     * Analyzes structural keyword context (set, policy, permit, deny, where, etc.).
     */
    private CompletionContext analyzeStructuralKeywordContext(TokenContext ctx) {
        var tokenAtCursor = ctx.tokenAtCursor();
        var previousToken = ctx.previousToken();
        var tokenType     = tokenAtCursor.getType();

        // After set keyword - need combining algorithm
        if (tokenType == SAPLLexer.SET) {
            return CompletionContext.COMBINING_ALGORITHM;
        }

        // After policy keyword followed by string - need entitlement
        if (tokenType == SAPLLexer.STRING && previousToken != null && previousToken.getType() == SAPLLexer.POLICY) {
            return CompletionContext.ENTITLEMENT;
        }

        // After permit or deny
        if (tokenType == SAPLLexer.PERMIT || tokenType == SAPLLexer.DENY) {
            return CompletionContext.POLICY_AFTER_ENTITLEMENT;
        }

        // After where keyword
        if (tokenType == SAPLLexer.WHERE) {
            return CompletionContext.POLICY_BODY_START;
        }

        // After semicolon or typing after semicolon
        if (tokenType == SAPLLexer.SEMI || (previousToken != null && previousToken.getType() == SAPLLexer.SEMI)) {
            return CompletionContext.POLICY_BODY_AFTER_STATEMENT;
        }

        // After obligation, advice, or transform keyword - expression context
        if (tokenType == SAPLLexer.OBLIGATION || tokenType == SAPLLexer.ADVICE || tokenType == SAPLLexer.TRANSFORM) {
            return CompletionContext.EXPRESSION;
        }

        return CompletionContext.UNKNOWN;
    }

    /**
     * Finds the deepest parse tree node containing the position.
     */
    private ParseTree findDeepestContextAtPosition(ParseTree tree, int line, int column) {
        if (tree instanceof TerminalNode terminal) {
            var token = terminal.getSymbol();
            // Skip EOF token - we want the actual content token before EOF
            if (token.getType() == Token.EOF) {
                return null;
            }
            if (token.getLine() == line && token.getCharPositionInLine() <= column
                    && column <= token.getCharPositionInLine() + token.getText().length()) {
                return tree;
            }
            return null;
        }

        // Find deepest matching child
        ParseTree deepestMatch = null;
        for (var i = 0; i < tree.getChildCount(); i++) {
            var child = tree.getChild(i);
            var match = findDeepestContextAtPosition(child, line, column);
            if (match != null) {
                deepestMatch = match;
            }
        }

        // If no child matched but we're a rule context, check if position is within our
        // bounds
        if (deepestMatch == null && tree instanceof ParserRuleContext ruleContext) {
            var start = ruleContext.getStart();
            var stop  = ruleContext.getStop();
            if (start != null && stop != null) {
                if (isPositionWithinTokenRange(line, column, start, stop)) {
                    return tree;
                }
            }
        }

        return deepestMatch;
    }

    private boolean isPositionWithinTokenRange(int line, int column, Token start, Token stop) {
        if (line < start.getLine() || line > stop.getLine()) {
            return false;
        }
        if (line == start.getLine() && column < start.getCharPositionInLine()) {
            return false;
        }
        if (line == stop.getLine() && column > stop.getCharPositionInLine() + stop.getText().length()) {
            return false;
        }
        return true;
    }

    /**
     * Checks if IMPORT token exists in token history on the same line before
     * position.
     * This helps detect when we're inside an import statement path.
     */
    private boolean hasImportTokenInHistory(List<Token> tokens, Position position) {
        var line = position.getLine() + 1;
        var col  = position.getCharacter();

        // Scan tokens on the same line before cursor for IMPORT token
        // But stop if we encounter structural keywords (set, policy, permit, etc.)
        // which indicate we've moved past the import statement
        for (var token : tokens) {
            if (token.getType() == Token.EOF) {
                break;
            }
            if (token.getLine() > line || (token.getLine() == line && token.getCharPositionInLine() >= col)) {
                break;
            }
            if (token.getType() == SAPLLexer.IMPORT) {
                return true;
            }
            // If we encounter structural keywords, we're past imports
            if (token.getType() == SAPLLexer.SET || token.getType() == SAPLLexer.POLICY
                    || token.getType() == SAPLLexer.PERMIT || token.getType() == SAPLLexer.DENY
                    || token.getType() == SAPLLexer.WHERE) {
                return false;
            }
        }
        return false;
    }

    /**
     * Analyzes parse tree context with detailed grammar awareness.
     */
    private CompletionContext analyzeParseTreeContext(ParseTree node, int line, int column) {
        var current = node;
        while (current != null) {
            // Environment attribute context
            if (current instanceof BasicEnvironmentAttributeContext
                    || current instanceof BasicEnvironmentHeadAttributeContext) {
                return CompletionContext.ENVIRONMENT_ATTRIBUTE;
            }

            // Function call context
            if (current instanceof BasicFunctionContext) {
                return CompletionContext.FUNCTION_CALL;
            }

            // Inside any expression - only expression completions
            if (current instanceof ExpressionContext) {
                return CompletionContext.EXPRESSION;
            }

            // Inside value definition (var x = ...) - expression context for the value
            if (current instanceof ValueDefinitionContext) {
                return CompletionContext.EXPRESSION;
            }

            // Inside a statement in policy body
            if (current instanceof StatementContext) {
                return CompletionContext.EXPRESSION;
            }

            // Policy body context - check if at start or after statement
            if (current instanceof PolicyBodyContext) {
                return CompletionContext.POLICY_BODY_AFTER_STATEMENT;
            }

            // Policy context - determine where in the policy
            if (current instanceof PolicyContext policyContext) {
                return analyzePolicyContext(policyContext, line, column);
            }

            // Policy set context
            if (current instanceof PolicySetContext policySetContext) {
                return analyzePolicySetContext(policySetContext, line, column);
            }

            // Combining algorithm context
            if (current instanceof CombiningAlgorithmContext) {
                return CompletionContext.COMBINING_ALGORITHM;
            }

            // Import statement
            if (current instanceof ImportStatementContext) {
                return CompletionContext.IMPORT_PATH;
            }

            // Top-level document
            if (current instanceof SaplContext) {
                return CompletionContext.AFTER_IMPORTS;
            }

            current = current.getParent();
        }

        return CompletionContext.UNKNOWN;
    }

    /**
     * Determines context within a policy rule.
     */
    private CompletionContext analyzePolicyContext(PolicyContext policy, int line, int column) {
        // Check if we're in the body
        if (policy.policyBody() != null) {
            var body = policy.policyBody();
            if (body.getStart() != null && body.getStop() != null) {
                if (isPositionWithinTokenRange(line, column, body.getStart(), body.getStop())) {
                    return CompletionContext.POLICY_BODY_AFTER_STATEMENT;
                }
            }
        }

        // Check if after entitlement
        var entitlement = policy.entitlement();
        if (entitlement != null && entitlement.getStop() != null) {
            var entitlementEnd = entitlement.getStop();
            if (line > entitlementEnd.getLine() || (line == entitlementEnd.getLine()
                    && column > entitlementEnd.getCharPositionInLine() + entitlementEnd.getText().length())) {
                return CompletionContext.POLICY_AFTER_ENTITLEMENT;
            }
        }

        return CompletionContext.ENTITLEMENT;
    }

    /**
     * Determines context within a policy set.
     */
    private CompletionContext analyzePolicySetContext(PolicySetContext policySet, int line, int column) {
        // Check if we have a combining algorithm yet
        if (policySet.combiningAlgorithm() == null) {
            return CompletionContext.COMBINING_ALGORITHM;
        }

        return CompletionContext.POLICY_SET_BODY;
    }

    /**
     * Adds keyword completions.
     */
    private void addKeywordCompletions(List<CompletionItem> items, Set<String> keywords) {
        for (var keyword : keywords) {
            var item = new CompletionItem(keyword);
            item.setKind(CompletionItemKind.Keyword);
            item.setInsertText(keyword);
            items.add(item);
        }
    }

    /**
     * Adds variable completions with schema-aware expansions.
     * Uses VariablesProposalsGenerator to provide:
     * - Authorization subscription elements (subject, action, resource,
     * environment)
     * - Environment variables from configuration
     * - In-scope value definitions from policy body and set header
     * - Schema path expansions for all of the above
     */
    private void addVariableCompletions(List<CompletionItem> items, ParsedDocument document, Position position,
            LSPConfiguration config) {
        var saplParseTree = getSaplContext(document);
        if (saplParseTree == null) {
            // Fallback to simple variable completions without schema support
            addSimpleVariableCompletions(items, config);
            return;
        }

        var cursorOffset    = calculateCursorOffset(document.getContent(), position);
        var inSchemaContext = isInSchemaContext(document, position);
        var proposals       = VariablesProposalsGenerator.variableProposalsForContext(saplParseTree, cursorOffset,
                config, inSchemaContext);

        for (var proposal : proposals) {
            var item = new CompletionItem(proposal);
            item.setKind(CompletionItemKind.Variable);
            item.setInsertText(proposal);

            // Distinguish between base variables and schema expansions
            if (proposal.contains(".") || proposal.contains("[")) {
                item.setDetail("Schema path");
            } else if (SUBSCRIPTION_VARIABLES.contains(proposal)) {
                item.setDetail("Authorization subscription element");
            } else if (config.variables().containsKey(proposal)) {
                item.setDetail("Environment variable");
            } else {
                item.setDetail("Value definition");
            }

            items.add(item);
        }
    }

    /**
     * Simple variable completions fallback when parse tree is unavailable.
     */
    private void addSimpleVariableCompletions(List<CompletionItem> items, LSPConfiguration config) {
        for (var variable : SUBSCRIPTION_VARIABLES) {
            var item = new CompletionItem(variable);
            item.setKind(CompletionItemKind.Variable);
            item.setDetail("Authorization subscription element");
            item.setInsertText(variable);
            items.add(item);
        }

        for (var entry : config.variables().entrySet()) {
            var item = new CompletionItem(entry.getKey());
            item.setKind(CompletionItemKind.Variable);
            item.setDetail("Environment variable");
            item.setInsertText(entry.getKey());
            items.add(item);
        }
    }

    /**
     * Gets the SaplContext from a ParsedDocument.
     */
    private SaplContext getSaplContext(ParsedDocument document) {
        if (document instanceof SAPLParsedDocument saplDocument) {
            return saplDocument.getSaplParseTree();
        }
        var parseTree = document.getParseTree();
        if (parseTree instanceof SaplContext sapl) {
            return sapl;
        }
        return null;
    }

    /**
     * Calculates the character offset for a position in the document content.
     */
    private int calculateCursorOffset(String content, Position position) {
        var lines  = content.split("\n", -1);
        var offset = 0;
        for (var lineNumber = 0; lineNumber < position.getLine() && lineNumber < lines.length; lineNumber++) {
            offset += lines[lineNumber].length() + 1; // +1 for newline
        }
        offset += position.getCharacter();
        return offset;
    }

    /**
     * Determines if the cursor is within a schema statement expression.
     * In schema context, only environment variables should be offered
     * (not subscription elements or other value definitions).
     */
    private boolean isInSchemaContext(ParsedDocument document, Position position) {
        var sapl = getSaplContext(document);
        if (sapl == null || sapl.schemaStatement().isEmpty()) {
            return false;
        }

        var line   = position.getLine() + 1;
        var column = position.getCharacter();

        for (var schemaStatement : sapl.schemaStatement()) {
            var schemaExpression = schemaStatement.schemaExpression;
            if (schemaExpression != null) {
                var start = schemaExpression.getStart();
                var stop  = schemaExpression.getStop();
                if (start != null && stop != null && isPositionWithinTokenRange(line, column, start, stop)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds function completions with LSP snippet syntax for tab-stop navigation.
     */
    private void addFunctionCompletions(List<CompletionItem> items, LSPConfiguration config) {
        var bundle = config.documentationBundle();

        for (var library : bundle.functionLibraries()) {
            for (var entry : library.entries()) {
                if (entry.type() == EntryType.FUNCTION) {
                    var plainTemplate = entry.codeTemplate(library.name());
                    var snippet       = SnippetConverter.toSnippet(entry, library.name());
                    var item          = new CompletionItem(library.name() + "." + entry.name());
                    item.setKind(CompletionItemKind.Function);
                    item.setDetail(plainTemplate);
                    item.setInsertText(snippet);
                    item.setInsertTextFormat(InsertTextFormat.Snippet);

                    if (entry.documentation() != null) {
                        var doc = new MarkupContent();
                        doc.setKind(MarkupKind.MARKDOWN);
                        doc.setValue(entry.documentation());
                        item.setDocumentation(doc);
                    }

                    items.add(item);
                }
            }
        }
    }

    /**
     * Adds attribute completions with LSP snippet syntax for tab-stop navigation.
     */
    private void addAttributeCompletions(List<CompletionItem> items, LSPConfiguration config, boolean environmentOnly) {
        var bundle = config.documentationBundle();

        for (var pip : bundle.policyInformationPoints()) {
            for (var entry : pip.entries()) {
                var isEnvironment = entry.type() == EntryType.ENVIRONMENT_ATTRIBUTE;
                if (environmentOnly && !isEnvironment) {
                    continue;
                }
                if (!environmentOnly && entry.type() != EntryType.ATTRIBUTE) {
                    continue;
                }

                var plainTemplate = entry.codeTemplate(pip.name());
                var snippet       = SnippetConverter.toSnippet(entry, pip.name());
                // Label includes <> to match user typing pattern (e.g., <time.now>)
                var label = "<" + pip.name() + "." + entry.name() + ">";
                var item  = new CompletionItem(label);
                item.setKind(CompletionItemKind.Property);
                item.setDetail(plainTemplate);
                item.setInsertText(snippet);
                item.setInsertTextFormat(InsertTextFormat.Snippet);

                if (entry.documentation() != null) {
                    var doc = new MarkupContent();
                    doc.setKind(MarkupKind.MARKDOWN);
                    doc.setValue(entry.documentation());
                    item.setDocumentation(doc);
                }

                items.add(item);
            }
        }
    }

    /**
     * Adds import completions.
     */
    private void addImportCompletions(List<CompletionItem> items, LSPConfiguration config) {
        var bundle = config.documentationBundle();

        for (var library : bundle.functionLibraries()) {
            var item = new CompletionItem(library.name());
            item.setKind(CompletionItemKind.Module);
            item.setDetail("Function library");
            item.setInsertText(library.name());
            items.add(item);

            for (var entry : library.entries()) {
                var fqn     = library.name() + "." + entry.name();
                var fqnItem = new CompletionItem(fqn);
                fqnItem.setKind(CompletionItemKind.Function);
                fqnItem.setDetail(entry.codeTemplate(library.name()));
                fqnItem.setInsertText(fqn);
                items.add(fqnItem);
            }
        }

        for (var pip : bundle.policyInformationPoints()) {
            var item = new CompletionItem(pip.name());
            item.setKind(CompletionItemKind.Module);
            item.setDetail("Policy Information Point");
            item.setInsertText(pip.name());
            items.add(item);
        }
    }

    /**
     * Completion context types - fine-grained for proper keyword scoping.
     */
    private enum CompletionContext {
        /** Empty document or before any content */
        DOCUMENT_START,
        /** After import statements, before policy element */
        AFTER_IMPORTS,
        /** Inside import statement path */
        IMPORT_PATH,
        /** After 'set' keyword - need combining algorithm */
        COMBINING_ALGORITHM,
        /** Inside policy set body */
        POLICY_SET_BODY,
        /** After 'policy "name"' - need permit/deny */
        ENTITLEMENT,
        /** After entitlement - target expression or structure keywords */
        POLICY_AFTER_ENTITLEMENT,
        /** After 'where' - start of body */
        POLICY_BODY_START,
        /** After semicolon in body - next statement */
        POLICY_BODY_AFTER_STATEMENT,
        /** Inside any expression - only expression elements */
        EXPRESSION,
        /** Inside environment attribute <...> */
        ENVIRONMENT_ATTRIBUTE,
        /** Inside regular attribute access */
        ATTRIBUTE,
        /** Inside function call */
        FUNCTION_CALL,
        /** Unknown context */
        UNKNOWN
    }

}
