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
package io.sapl.grammar.ide.contentassist;

import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.grammar.ide.contentassist.ContextAnalyzer.ContextAnalysisResult;
import io.sapl.grammar.ide.contentassist.ProposalCreator.Proposal;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.SAPL;
import lombok.experimental.UtilityClass;
import org.eclipse.emf.common.util.EList;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Generates content assist proposals for SAPL functions and attribute finders
 * based on the registered libraries in the PDP configuration.
 */
@UtilityClass
public class LibraryProposalsGenerator {

    /**
     * Generates schema-based proposals for environment attribute return values.
     * These proposals extend the current expression based on the attribute's return
     * type schema.
     *
     * @param analysis
     * the context analysis containing the current function name and prefix
     * @param context
     * the content assist context for resolving imports
     * @param pdpConfiguration
     * the PDP configuration containing documentation and variables
     *
     * @return set of proposals based on matching attribute schemas
     */
    public static Set<Proposal> allEnvironmentAttributeSchemaExtensions(ContextAnalysisResult analysis,
            ContentAssistContext context, ContentAssistPDPConfiguration pdpConfiguration) {
        var proposals = new HashSet<Proposal>();
        var variables = pdpConfiguration.variables();

        for (var pip : pdpConfiguration.documentationBundle().policyInformationPoints()) {
            for (var entry : pip.entries()) {
                if (entry.type() == EntryType.ENVIRONMENT_ATTRIBUTE) {
                    var fullyQualifiedName = pip.name() + '.' + entry.name();
                    var aliases            = aliasNamesOfFunctionFromImports(fullyQualifiedName, context);
                    if (aliases.contains(analysis.functionName())) {
                        proposals.addAll(schemaProposalsForEntry(analysis, entry, variables));
                    }
                }
            }
        }
        return proposals;
    }

    /**
     * Generates schema-based proposals for entity attribute return values.
     *
     * @param analysis
     * the context analysis containing the current function name and prefix
     * @param context
     * the content assist context for resolving imports
     * @param pdpConfiguration
     * the PDP configuration containing documentation and variables
     *
     * @return set of proposals based on matching attribute schemas
     */
    public static Set<Proposal> allAttributeSchemaExtensions(ContextAnalysisResult analysis,
            ContentAssistContext context, ContentAssistPDPConfiguration pdpConfiguration) {
        var proposals = new HashSet<Proposal>();
        var variables = pdpConfiguration.variables();

        for (var pip : pdpConfiguration.documentationBundle().policyInformationPoints()) {
            for (var entry : pip.entries()) {
                if (entry.type() == EntryType.ATTRIBUTE) {
                    var fullyQualifiedName = pip.name() + '.' + entry.name();
                    var aliases            = aliasNamesOfFunctionFromImports(fullyQualifiedName, context);
                    if (aliases.contains(analysis.functionName())) {
                        proposals.addAll(schemaProposalsForEntry(analysis, entry, variables));
                    }
                }
            }
        }
        return proposals;
    }

    /**
     * Generates schema-based proposals for function return values.
     *
     * @param analysis
     * the context analysis containing the current function name and prefix
     * @param context
     * the content assist context for resolving imports
     * @param pdpConfiguration
     * the PDP configuration containing documentation and variables
     *
     * @return set of proposals based on matching function schemas
     */
    public static Set<Proposal> allFunctionSchemaExtensions(ContextAnalysisResult analysis,
            ContentAssistContext context, ContentAssistPDPConfiguration pdpConfiguration) {
        var proposals = new HashSet<Proposal>();
        var variables = pdpConfiguration.variables();

        for (var library : pdpConfiguration.documentationBundle().functionLibraries()) {
            for (var entry : library.entries()) {
                var fullyQualifiedName = library.name() + '.' + entry.name();
                var aliases            = aliasNamesOfFunctionFromImports(fullyQualifiedName, context);
                if (aliases.contains(analysis.functionName())) {
                    proposals.addAll(schemaProposalsForEntry(analysis, entry, variables));
                }
            }
        }
        return proposals;
    }

    private static List<Proposal> schemaProposalsForEntry(ContextAnalysisResult analysis, EntryDocumentation entry,
            Map<String, Value> variables) {
        var proposals    = new ArrayList<Proposal>();
        var schemaString = entry.schema();
        if (schemaString != null && !schemaString.isBlank()) {
            var schema = ValueJsonMarshaller.json(schemaString);
            if (!(schema instanceof io.sapl.api.model.ErrorValue)) {
                SchemaProposalsGenerator.getCodeTemplates("", schema, variables)
                        .forEach(proposal -> ProposalCreator
                                .createNormalizedEntry(proposal, analysis.prefix(), analysis.ctxPrefix())
                                .ifPresent(proposals::add));
            }
        }
        return proposals;
    }

    /**
     * Generates documented proposals for all PDP deployed attribute (step) finders
     * including aliased alternatives based on potential imports.
     *
     * @param analysis
     * analysis of recommendation context
     * @param context
     * the content assist context for resolving imports
     * @param pdpConfiguration
     * the PDP configuration containing registered PIPs
     *
     * @return a list of all attribute finder proposals with their aliased
     * alternatives based on imports
     */
    public static List<Proposal> allAttributeFinders(ContextAnalysisResult analysis, ContentAssistContext context,
            ContentAssistPDPConfiguration pdpConfiguration) {
        var proposals = new ArrayList<Proposal>();

        for (var pip : pdpConfiguration.documentationBundle().policyInformationPoints()) {
            for (var entry : pip.entries()) {
                if (entry.type() == EntryType.ATTRIBUTE) {
                    proposals.addAll(proposalsForEntry(analysis, pip, entry, context));
                }
            }
        }
        return proposals;
    }

    /**
     * Generates documented proposals for all PDP deployed environment attribute
     * finders including aliased alternatives based on potential imports.
     *
     * @param analysis
     * analysis of recommendation context
     * @param context
     * the content assist context for resolving imports
     * @param pdpConfiguration
     * the PDP configuration containing registered PIPs
     *
     * @return a list of all environment attribute finder proposals with their
     * aliased alternatives based on imports
     */
    public static List<Proposal> allEnvironmentAttributeFinders(ContextAnalysisResult analysis,
            ContentAssistContext context, ContentAssistPDPConfiguration pdpConfiguration) {
        var proposals = new ArrayList<Proposal>();

        for (var pip : pdpConfiguration.documentationBundle().policyInformationPoints()) {
            for (var entry : pip.entries()) {
                if (entry.type() == EntryType.ENVIRONMENT_ATTRIBUTE) {
                    proposals.addAll(proposalsForEntry(analysis, pip, entry, context));
                }
            }
        }
        return proposals;
    }

    /**
     * Generates documented proposals for all PDP deployed functions including
     * aliased alternatives based on potential imports.
     *
     * @param analysis
     * current context analysis
     * @param context
     * the content assist context for resolving imports
     * @param pdpConfiguration
     * the PDP configuration containing registered function libraries
     *
     * @return a list of all function proposals with their aliased alternatives
     * based on imports
     */
    public static List<Proposal> allFunctions(ContextAnalysisResult analysis, ContentAssistContext context,
            ContentAssistPDPConfiguration pdpConfiguration) {
        var proposals = new ArrayList<Proposal>();

        for (var library : pdpConfiguration.documentationBundle().functionLibraries()) {
            for (var entry : library.entries()) {
                proposals.addAll(proposalsForEntry(analysis, library, entry, context));
            }
        }
        return proposals;
    }

    /**
     * Generates proposals with aliases for one function or attribute finder entry.
     *
     * @param analysis
     * the context analysis containing prefix information
     * @param library
     * the library containing this entry
     * @param entry
     * the entry documentation
     * @param context
     * the content assist context for resolving imports
     *
     * @return a list of proposals for the entry with aliased alternatives
     */
    private static List<Proposal> proposalsForEntry(ContextAnalysisResult analysis, LibraryDocumentation library,
            EntryDocumentation entry, ContentAssistContext context) {
        var proposals          = new ArrayList<Proposal>();
        var fullyQualifiedName = library.name() + '.' + entry.name();
        var aliases            = aliasNamesOfFunctionFromImports(fullyQualifiedName, context);

        for (var alias : aliases) {
            var codeTemplate = alias.equals(fullyQualifiedName) ? entry.codeTemplate(library.name())
                    : entry.codeTemplateWithAlias(alias);
            ProposalCreator
                    .createNormalizedEntry(codeTemplate, analysis.prefix(), analysis.ctxPrefix(), entry.documentation())
                    .ifPresent(proposals::add);
        }
        return proposals;
    }

    /**
     * Creates all aliases, including the original, names for a function or
     * attribute finder.
     *
     * @param fullyQualifiedName
     * the fully qualified name of a function or attribute finder
     * @param context
     * the content assist context for inspecting defined imports
     *
     * @return a list with the original name and all possible aliases based on
     * imports
     */
    private static List<String> aliasNamesOfFunctionFromImports(String fullyQualifiedName,
            ContentAssistContext context) {
        var aliases = new ArrayList<String>();
        aliases.add(fullyQualifiedName);
        if (context.getRootModel() instanceof SAPL sapl) {
            var imports = Objects.requireNonNullElse(sapl.getImports(), List.<Import>of());
            for (var anImport : imports) {
                resolveImport(anImport, fullyQualifiedName).ifPresent(aliases::add);
            }
        }
        return aliases;
    }

    /**
     * Generates an alias for a fully qualified name if the import is applicable.
     *
     * @param anImport
     * an import statement
     * @param fullyQualifiedName
     * the original fully qualified name of the function
     *
     * @return an Optional containing an alias for the function, if the import was
     * applicable, otherwise empty
     */
    private static Optional<String> resolveImport(Import anImport, String fullyQualifiedName) {
        var steps                      = joinStepsToPrefix(anImport.getLibSteps());
        var functionName               = anImport.getFunctionName();
        var fullyQualifiedNameInImport = steps + functionName;
        var alias                      = anImport.getFunctionAlias();
        if (alias == null && fullyQualifiedName.startsWith(steps) && functionName != null) {
            return Optional.of(functionName);
        } else if (fullyQualifiedName.equals(fullyQualifiedNameInImport)) {
            return Optional.ofNullable(alias);
        }
        return Optional.empty();
    }

    /**
     * Joins the steps to a string, separating steps by '.', appending a '.' at the
     * end.
     *
     * @param steps
     * some steps
     *
     * @return step names joined by '.' with '.' after last step as well
     */
    private static String joinStepsToPrefix(EList<String> steps) {
        return joinStepsToName(steps) + '.';
    }

    /**
     * Joins the steps to a string, separating steps by '.'.
     *
     * @param steps
     * some steps
     *
     * @return step names joined by '.'
     */
    private static String joinStepsToName(EList<String> steps) {
        return String.join(".", steps);
    }

}
