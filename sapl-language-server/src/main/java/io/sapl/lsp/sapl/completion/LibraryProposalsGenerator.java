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
package io.sapl.lsp.sapl.completion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.grammar.antlr.SAPLParser.ImportStatementContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.lsp.configuration.LSPConfiguration;
import io.sapl.lsp.sapl.completion.ContextAnalyzer.ContextAnalysisResult;
import lombok.experimental.UtilityClass;

/**
 * Generates schema-based property proposals for function and attribute return
 * values.
 *
 * When the cursor is after a function call (e.g., `time.now().`), this class
 * looks up the return type schema of the function and generates property path
 * suggestions.
 */
@UtilityClass
public class LibraryProposalsGenerator {

    /**
     * Generates schema-based proposals for function return values.
     * Called when cursor is at `function().` position.
     *
     * @param analysis the context analysis containing the function name
     * @param sapl the SAPL document parse tree (for import resolution)
     * @param config the LSP configuration containing documentation bundle
     * @return set of property path proposals based on the function's return schema
     */
    public static Set<String> allFunctionSchemaExtensions(ContextAnalysisResult analysis, SaplContext sapl,
            LSPConfiguration config) {
        var proposals = new HashSet<String>();

        for (var library : config.documentationBundle().functionLibraries()) {
            for (var entry : library.entries()) {
                var fullyQualifiedName = library.name() + '.' + entry.name();
                var aliases            = aliasNamesForFunction(fullyQualifiedName, sapl);
                if (aliases.contains(analysis.functionName())) {
                    proposals.addAll(schemaProposalsForEntry(entry, config));
                }
            }
        }
        return proposals;
    }

    /**
     * Generates schema-based proposals for entity attribute return values.
     * Called when cursor is at `expr.<attr>.` position.
     *
     * @param analysis the context analysis containing the attribute name
     * @param sapl the SAPL document parse tree (for import resolution)
     * @param config the LSP configuration containing documentation bundle
     * @return set of property path proposals based on the attribute's return schema
     */
    public static Set<String> allAttributeSchemaExtensions(ContextAnalysisResult analysis, SaplContext sapl,
            LSPConfiguration config) {
        var proposals = new HashSet<String>();

        for (var pip : config.documentationBundle().policyInformationPoints()) {
            for (var entry : pip.entries()) {
                if (entry.type() == EntryType.ATTRIBUTE) {
                    var fullyQualifiedName = pip.name() + '.' + entry.name();
                    var aliases            = aliasNamesForFunction(fullyQualifiedName, sapl);
                    if (aliases.contains(analysis.functionName())) {
                        proposals.addAll(schemaProposalsForEntry(entry, config));
                    }
                }
            }
        }
        return proposals;
    }

    /**
     * Generates schema-based proposals for environment attribute return values.
     * Called when cursor is at `|<attr>.` position.
     *
     * @param analysis the context analysis containing the attribute name
     * @param sapl the SAPL document parse tree (for import resolution)
     * @param config the LSP configuration containing documentation bundle
     * @return set of property path proposals based on the attribute's return schema
     */
    public static Set<String> allEnvironmentAttributeSchemaExtensions(ContextAnalysisResult analysis, SaplContext sapl,
            LSPConfiguration config) {
        var proposals = new HashSet<String>();

        for (var pip : config.documentationBundle().policyInformationPoints()) {
            for (var entry : pip.entries()) {
                if (entry.type() == EntryType.ENVIRONMENT_ATTRIBUTE) {
                    var fullyQualifiedName = pip.name() + '.' + entry.name();
                    var aliases            = aliasNamesForFunction(fullyQualifiedName, sapl);
                    if (aliases.contains(analysis.functionName())) {
                        proposals.addAll(schemaProposalsForEntry(entry, config));
                    }
                }
            }
        }
        return proposals;
    }

    /**
     * Generates property path proposals from an entry's return type schema.
     * Strips leading dots since these extensions are used after a dot is already
     * present (e.g., `time.now().` -> offer `year`, not `.year`).
     */
    private static Set<String> schemaProposalsForEntry(EntryDocumentation entry, LSPConfiguration config) {
        var proposals    = new HashSet<String>();
        var schemaString = entry.schema();
        if (schemaString != null && !schemaString.isBlank()) {
            var schema = ValueJsonMarshaller.json(schemaString);
            if (!(schema instanceof ErrorValue)) {
                for (var template : SchemaProposalsGenerator.getCodeTemplates("", schema, config.variables())) {
                    // Strip leading dot since cursor is already after a dot
                    if (template.startsWith(".")) {
                        proposals.add(template.substring(1));
                    } else {
                        proposals.add(template);
                    }
                }
            }
        }
        return proposals;
    }

    /**
     * Returns all aliases for a function including the original fully qualified
     * name and any import-based aliases.
     *
     * @param fullyQualifiedName the FQN like "time.now"
     * @param sapl the SAPL document for reading imports
     * @return list containing FQN and any aliases from imports
     */
    public static List<String> aliasNamesForFunction(String fullyQualifiedName, SaplContext sapl) {
        var aliases = new ArrayList<String>();
        aliases.add(fullyQualifiedName);

        if (sapl != null && sapl.importStatement() != null) {
            for (var importStmt : sapl.importStatement()) {
                resolveImport(importStmt, fullyQualifiedName).ifPresent(aliases::add);
            }
        }
        return aliases;
    }

    /**
     * Resolves an import statement to determine if it provides an alias for the
     * given FQN.
     *
     * Import forms:
     * - `import time` - makes all time.X functions available as X
     * - `import time.now` - makes time.now available as now
     * - `import time.now as currentTime` - makes time.now available as currentTime
     */
    private static Optional<String> resolveImport(ImportStatementContext importStmt, String fullyQualifiedName) {
        var libSteps  = importStmt.libSteps.stream().map(id -> id.getText()).collect(Collectors.joining("."));
        var libPrefix = libSteps + ".";

        var functionName = importStmt.functionName != null ? importStmt.functionName.getText() : null;
        var alias        = importStmt.functionAlias != null ? importStmt.functionAlias.getText() : null;

        if (alias == null && fullyQualifiedName.startsWith(libPrefix) && functionName != null) {
            // `import lib.func` without alias - func becomes available without prefix
            return Optional.of(functionName);
        }

        if (functionName != null) {
            var fullyQualifiedNameInImport = libSteps + "." + functionName;
            if (fullyQualifiedName.equals(fullyQualifiedNameInImport)) {
                if (alias != null) {
                    // `import lib.func as alias` - alias replaces FQN
                    return Optional.of(alias);
                } else {
                    // `import lib.func` - func available without prefix
                    return Optional.of(functionName);
                }
            }
        } else if (fullyQualifiedName.startsWith(libPrefix)) {
            // `import lib` - everything in lib available with short name
            var shortName = fullyQualifiedName.substring(libPrefix.length());
            return Optional.of(shortName);
        }

        return Optional.empty();
    }

}
