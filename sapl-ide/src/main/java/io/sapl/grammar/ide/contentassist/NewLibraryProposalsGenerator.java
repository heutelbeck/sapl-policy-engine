/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.common.util.EList;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;

import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.interpreter.pip.AttributeFinderMetadata;
import io.sapl.interpreter.pip.LibraryEntryMetadata;
import io.sapl.pdp.config.PDPConfiguration;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class NewLibraryProposalsGenerator {

    public record DocumentedProposal(String proposal, String label, String documentation) {}

    /**
     * Generates documented proposals for all PDP deployed environment attribute
     * finders including aliased alternatives based on potential imports.
     *
     * @param context The current ContentAssistContext context is needed to inspect
     * potentially defined imports in the document to resolve names correctly.
     * @param pdpConfiguration The PDPConfiguration pdpConfiguration supplies the
     * PIPs deployed in the PDP which supply the functions that can be used as
     * proposals.
     * @return a List of all attribute finder proposals with their aliased
     * alternatives based on imports.
     */
    public static Collection<DocumentedProposal> allEnvironmentAttributeFinders(ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        final var proposals  = new ArrayList<DocumentedProposal>();
        final var attributes = pdpConfiguration.attributeContext().getAttributeMetatata();
        attributes.stream().filter(AttributeFinderMetadata::isEnvironmentAttribute)
                .forEach(attribute -> proposals.addAll(documentedProposalsForLibraryEntry(attribute, context)));
        return proposals;
    }

    /**
     * Generates documented proposals for all PDP deployed functions including
     * aliased alternatives based on potential imports.
     *
     * @param context The current ContentAssistContext context is needed to inspect
     * potentially defined imports in the document to resolve names correctly.
     * @param pdpConfiguration The PDPConfiguration pdpConfiguration supplies the
     * libraries deployed in the PDP which supply the functions that can be used as
     * proposals.
     * @return a List of all function proposals with their aliased alternatives
     * based on imports.
     */
    public static Collection<DocumentedProposal> allFunctions(ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        final var proposals = new ArrayList<DocumentedProposal>();
        final var functions = pdpConfiguration.functionContext().getFunctionMetatata();
        functions.forEach(function -> proposals.addAll(documentedProposalsForLibraryEntry(function, context)));
        return proposals;
    }

    /**
     * Generates the proposals with aliases for one function or attribute finder.
     *
     * @param function the metadata describing one specific function or attribute
     * finder from a library.
     * @param context The current ContentAssistContext context is needed to inspect
     * potentially defined imports in the document to resolve names correctly.
     * @return a List of all proposals for the supplied function with their aliased
     * alternatives based on imports.
     */
    private static Collection<DocumentedProposal> documentedProposalsForLibraryEntry(LibraryEntryMetadata function,
            ContentAssistContext context) {
        final var proposals = new ArrayList<DocumentedProposal>();
        final var aliases   = aliasNamesOfFunctionFromImports(function.fullyQualifiedName(), context);
        aliases.forEach(alias -> proposals.add(new DocumentedProposal(function.getCodeTemplate(alias),
                function.getDocumentationCodeTemplate(alias), function.getDocumentation())));
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
        if (context.getRootModel() instanceof SAPL sapl) {
            final var imports = Objects.requireNonNullElse(sapl.getImports(), List.<Import>of());
            for (var anImport : imports) {
                if (anImport instanceof WildcardImport wildcardImport) {
                    wildcardAlias(wildcardImport, fullyQualifiedName).ifPresent(aliases::add);
                } else if (anImport instanceof LibraryImport libraryImport) {
                    libraryImportAlias(libraryImport, fullyQualifiedName).ifPresent(aliases::add);
                } else {
                    importAlias(anImport, fullyQualifiedName).ifPresent(aliases::add);
                }
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
    private static Optional<String> importAlias(Import anImport, String fullyQualifiedName) {
        final var steps        = anImport.getLibSteps();
        final var functionName = anImport.getFunctionName();
        final var prefix       = joinStepsToPrefix(steps) + functionName;
        if (fullyQualifiedName.startsWith(prefix)) {
            return Optional.of(fullyQualifiedName.replaceFirst(prefix, functionName));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Generates an alias for a fully qualified name if the import is applicable.
     *
     * @param anImport a library import statement
     * @param fullyQualifiedName the original fully qualified name of the function
     * @return an Optional containing an alias for the function, if the import was
     * applicable. Else returns empty Optional.
     */
    private static Optional<String> libraryImportAlias(LibraryImport libraryImport, String fullyQualifiedName) {
        final var shortPrefix = String.join(".", libraryImport.getLibSteps());
        final var prefix      = shortPrefix + '.';
        if (fullyQualifiedName.startsWith(prefix)) {
            return Optional.of(fullyQualifiedName.replaceFirst(shortPrefix, libraryImport.getLibAlias()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Generates an alias for a fully qualified name if the import is applicable.
     *
     * @param anImport a wild-card import statement
     * @param fullyQualifiedName the original fully qualified name of the function
     * @return an Optional containing an alias for the function, if the import was
     * applicable. Else returns empty Optional.
     */
    private static Optional<String> wildcardAlias(WildcardImport wildcardImport, String fullyQualifiedName) {
        final var prefix = joinStepsToPrefix(wildcardImport.getLibSteps());
        if (fullyQualifiedName.startsWith(prefix)) {
            return Optional.of(fullyQualifiedName.replaceFirst(prefix, ""));
        } else {
            return Optional.empty();
        }
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
