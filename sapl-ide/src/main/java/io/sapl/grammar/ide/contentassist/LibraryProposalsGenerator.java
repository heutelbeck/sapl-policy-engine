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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.common.util.EList;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.documentation.api.FunctionType;
import io.sapl.attributes.documentation.api.LibraryFunctionDocumentation;
import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;
import io.sapl.grammar.ide.contentassist.ContextAnalyzer.ContextAnalysisResult;
import io.sapl.grammar.ide.contentassist.ProposalCreator.Proposal;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.pip.LibraryEntryMetadata;
import io.sapl.pdp.config.PDPConfiguration;
import lombok.experimental.UtilityClass;

@UtilityClass
public class LibraryProposalsGenerator {

    public record DocumentedProposal(String proposal, String label, String documentation) {}

    public static Collection<Proposal> allEnvironmentAttributeSchemaExtensions(ContextAnalysisResult analysis,
            ContentAssistContext context, PDPConfiguration pdpConfiguration,
            PolicyInformationPointDocumentationProvider docsProvider) {
        final var proposals  = new HashSet<Proposal>();
        final var attributes = docsProvider.getAttributeMetatata();
        final var variables  = pdpConfiguration.variables();
        attributes.stream().filter(d -> d.type() == FunctionType.ENVIRONMENT_ATTRIBUTE)
                .filter(function -> aliasNamesOfFunctionFromImports(function.fullyQualifiedName(), context)
                        .contains(analysis.functionName()))
                .forEach(attribute -> proposals.addAll(entryForMetadata(analysis, attribute, variables)));
        return proposals;
    }

    public static Collection<Proposal> allAttributeSchemaExtensions(ContextAnalysisResult analysis,
            ContentAssistContext context, PDPConfiguration pdpConfiguration,
            PolicyInformationPointDocumentationProvider docsProvider) {
        final var proposals  = new HashSet<Proposal>();
        final var attributes = docsProvider.getAttributeMetatata();
        final var variables  = pdpConfiguration.variables();
        attributes.stream().filter(d -> d.type() == FunctionType.ATTRIBUTE)
                .filter(function -> aliasNamesOfFunctionFromImports(function.fullyQualifiedName(), context)
                        .contains(analysis.functionName()))
                .forEach(attribute -> proposals.addAll(entryForMetadata(analysis, attribute, variables)));
        return proposals;
    }

    public static Collection<Proposal> allFunctionSchemaExtensions(ContextAnalysisResult analysis,
            ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        final var proposals = new HashSet<Proposal>();
        final var functions = pdpConfiguration.functionContext().getFunctionMetatata();
        final var variables = pdpConfiguration.variables();
        functions.stream()
                .filter(function -> aliasNamesOfFunctionFromImports(function.fullyQualifiedName(), context)
                        .contains(analysis.functionName()))
                .forEach(function -> proposals.addAll(entryForMetadata(analysis, function, variables)));
        return proposals;
    }

    private List<Proposal> entryForMetadata(ContextAnalysisResult analysis, LibraryEntryMetadata metadata,
            Map<String, Val> variables) {
        final var proposals = new ArrayList<Proposal>();
        final var schema    = metadata.getFunctionSchema();
        if (null != schema) {
            SchemaProposalsGenerator.getCodeTemplates("", schema, variables)
                    .forEach(proposal -> ProposalCreator
                            .createNormalizedEntry(proposal, analysis.prefix(), analysis.ctxPrefix())
                            .ifPresent(proposals::add));
        }
        return proposals;
    }

    private List<Proposal> entryForMetadata(ContextAnalysisResult analysis, LibraryFunctionDocumentation metadata,
            Map<String, Val> variables) {
        final var proposals = new ArrayList<Proposal>();
        final var schema    = metadata.returnTypeSchema();
        if (null != schema) {
            SchemaProposalsGenerator.getCodeTemplates("", schema, variables)
                    .forEach(proposal -> ProposalCreator
                            .createNormalizedEntry(proposal, analysis.prefix(), analysis.ctxPrefix())
                            .ifPresent(proposals::add));
        }
        return proposals;
    }

    /**
     * Generates documented proposals for all PDP deployed attribute (step) finders
     * including aliased alternatives based on potential imports.
     *
     * @param analysis analysis of recommendation context
     * @param context The current ContentAssistContext context is needed to inspect
     * potentially defined imports in the document to resolve names correctly.
     * @param docsProvider documentation provider PIPs deployed in the PDP which
     * supply the functions that can be used as proposals.
     * @return a List of all attribute finder proposals with their aliased
     * alternatives based on imports.
     */
    public static Collection<Proposal> allAttributeFinders(ContextAnalysisResult analysis, ContentAssistContext context,
            PolicyInformationPointDocumentationProvider docsProvider) {
        final var proposals  = new ArrayList<Proposal>();
        final var attributes = docsProvider.getAttributeMetatata();
        attributes.stream().filter(d -> d.type() == FunctionType.ATTRIBUTE).forEach(attribute -> proposals.addAll(
                documentedProposalsForLibraryEntry(analysis.prefix(), analysis.ctxPrefix(), attribute, context)));
        return proposals;
    }

    /**
     * Generates documented proposals for all PDP deployed environment attribute
     * finders including aliased alternatives based on potential imports.
     *
     * @param analysis analysis of recommendation context
     * @param context The current ContentAssistContext context is needed to inspect
     * potentially defined imports in the document to resolve names correctly.
     * @param docsProvider the documentation provider PIPs deployed in the PDP which
     * supply the functions that can be used as proposals.
     * @return a List of all attribute finder proposals with their aliased
     * alternatives based on imports.
     */
    public static Collection<Proposal> allEnvironmentAttributeFinders(ContextAnalysisResult analysis,
            ContentAssistContext context, PolicyInformationPointDocumentationProvider docsProvider) {
        final var proposals  = new ArrayList<Proposal>();
        final var attributes = docsProvider.getAttributeMetatata();
        attributes.stream().filter(d -> d.type() == FunctionType.ENVIRONMENT_ATTRIBUTE)
                .forEach(attribute -> proposals.addAll(documentedProposalsForLibraryEntry(analysis.prefix(),
                        analysis.ctxPrefix(), attribute, context)));
        return proposals;
    }

    /**
     * Generates documented proposals for all PDP deployed functions including
     * aliased alternatives based on potential imports.
     *
     * @param analysis current context analysis
     * @param context The current ContentAssistContext context is needed to inspect
     * potentially defined imports in the document to resolve names correctly.
     * @param pdpConfiguration The PDPConfiguration pdpConfiguration supplies the
     * libraries deployed in the PDP which supply the functions that can be used as
     * proposals.
     * @return a List of all function proposals with their aliased alternatives
     * based on imports.
     */
    public static Collection<Proposal> allFunctions(ContextAnalysisResult analysis, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        final var proposals = new ArrayList<Proposal>();
        final var functions = pdpConfiguration.functionContext().getFunctionMetatata();
        functions.forEach(function -> proposals.addAll(
                documentedProposalsForLibraryEntry(analysis.prefix(), analysis.ctxPrefix(), function, context)));
        return proposals;
    }

    /**
     * Generates the proposals with aliases for one function or attribute finder.
     *
     * @param prefix only add proposals starting with this prefix, but remove prefix
     * from proposal.
     * @param ctxPrefix actual Prefix in Context
     * @param function the metadata describing one specific function or attribute
     * finder from a library.
     * @param context The current ContentAssistContext context is needed to inspect
     * potentially defined imports in the document to resolve names correctly.
     * @return a List of all proposals for the supplied function with their aliased
     * alternatives based on imports.
     */
    private static Collection<Proposal> documentedProposalsForLibraryEntry(String prefix, String ctxPrefix,
            LibraryEntryMetadata function, ContentAssistContext context) {
        final var proposals = new ArrayList<Proposal>();
        final var aliases   = aliasNamesOfFunctionFromImports(function.fullyQualifiedName(), context);
        aliases.forEach(alias -> ProposalCreator
                .createNormalizedEntry(function.getCodeTemplate(alias), prefix, ctxPrefix).ifPresent(proposals::add));
        return proposals;
    }

    private static List<Proposal> documentedProposalsForLibraryEntry(String prefix, String ctxPrefix,
            LibraryFunctionDocumentation function, ContentAssistContext context) {
        final var proposals = new ArrayList<Proposal>();
        final var aliases   = aliasNamesOfFunctionFromImports(function.fullyQualifiedName(), context);
        aliases.forEach(alias -> ProposalCreator
                .createNormalizedEntry(function.aliasCodeTemplate(alias), prefix, ctxPrefix).ifPresent(proposals::add));
        return proposals;
    }

    /**
     * Creates all aliases, including the original, names for a function or
     * attribute finder.
     *
     * @param fullyQualifiedName the fully qualified name of a function or attribute
     * finder
     * @param context The current ContentAssistContext context is needed to inspect
     * potentially defined imports in the document to resolve names correctly.
     * @return a List with the original name and all possible aliases based on
     * imports.
     */
    private static Collection<String> aliasNamesOfFunctionFromImports(String fullyQualifiedName,
            ContentAssistContext context) {
        final var aliases = new ArrayList<String>();
        aliases.add(fullyQualifiedName);
        if (context.getRootModel() instanceof final SAPL sapl) {
            final var imports = Objects.requireNonNullElse(sapl.getImports(), List.<Import>of());
            for (final var anImport : imports) {
                resolveImport(anImport, fullyQualifiedName).ifPresent(aliases::add);
            }
        }
        return aliases;
    }

    /**
     * Generates an alias for a fully qualified name if the import is applicable.
     *
     * @param anImport an import statement
     * @param fullyQualifiedName the original fully qualified name of the function
     * @return an Optional containing an alias for the function, if the import was
     * applicable. Else returns empty Optional.
     */
    private static Optional<String> resolveImport(Import anImport, String fullyQualifiedName) {
        final var steps                      = joinStepsToPrefix(anImport.getLibSteps());
        final var functionName               = anImport.getFunctionName();
        final var fullyQualifiedNameInImport = steps + functionName;
        final var alias                      = anImport.getFunctionAlias();
        if (null == alias && fullyQualifiedName.startsWith(steps) && functionName != null) {
            return Optional.of(functionName);
        } else if (fullyQualifiedName.equals(fullyQualifiedNameInImport)) {
            return Optional.of(alias);
        }
        return Optional.empty();
    }

    /**
     * Joins the steps to a string, separating steps by '.', appending a '.' at the
     * end.
     *
     * @param steps some steps
     * @return step names joined by '.' with '.' after last step as well.
     */
    private static String joinStepsToPrefix(EList<String> steps) {
        return joinStepsToName(steps) + '.';
    }

    /**
     * Joins the steps to a string, separating steps by '.'.
     *
     * @param steps some steps
     * @return step names joined by '.'
     */
    private static String joinStepsToName(EList<String> steps) {
        return String.join(".", steps);
    }

}
