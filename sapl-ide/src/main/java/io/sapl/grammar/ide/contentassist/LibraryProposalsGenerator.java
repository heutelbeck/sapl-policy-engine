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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.common.util.EList;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.pdp.config.PDPConfiguration;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class LibraryProposalsGenerator {

    public record Proposal(String fullyQualifiedName, String proposal, String documentation) {}

    public static List<Proposal> createAttributeProposals(ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        final var proposals           = new ArrayList<Proposal>();
        final var attributeContext    = pdpConfiguration.attributeContext();
        final var documentedTemplates = attributeContext.getDocumentedAttributeCodeTemplates();
        for (var documentedTemplate : documentedTemplates.entrySet()) {
            final var template           = documentedTemplate.getKey();
            final var documentation      = documentedTemplate.getValue();
            final var fullyQualifiedName = fullyQualifiedNameFromTemplate(template);
            proposals.addAll(
                    createAttributeProposals(fullyQualifiedName, template, documentation, context, pdpConfiguration));
        }
        return proposals;
    }

    private static List<Proposal> createAttributeProposals(String fullyQualifiedName, String template,
            String documentation, ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        final var attributeContext = pdpConfiguration.attributeContext();
        final var schemas          = attributeContext.getAttributeSchemas();
        return createProposals(schemas, fullyQualifiedName, template, documentation, context, pdpConfiguration);
    }

    /*
     * fsteps are the name fragments of function names
     */
    public static List<Proposal> createFStepsProposals(ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        final var proposals           = new ArrayList<Proposal>();
        final var functionContext     = pdpConfiguration.functionContext();
        final var documentedTemplates = functionContext.getDocumentedCodeTemplates();
        for (var documentedTemplate : documentedTemplates.entrySet()) {
            final var template           = documentedTemplate.getKey();
            final var documentation      = documentedTemplate.getValue();
            final var fullyQualifiedName = fullyQualifiedNameFromTemplate(template);
            proposals.addAll(
                    createFunctionProposals(fullyQualifiedName, template, documentation, context, pdpConfiguration));
        }
        return proposals;
    }

    private static List<Proposal> createFunctionProposals(String fullyQualifiedName, String template,
            String documentation, ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        final var functionContext = pdpConfiguration.functionContext();
        final var schemas         = functionContext.getFunctionSchemas();
        return createProposals(schemas, fullyQualifiedName, template, documentation, context, pdpConfiguration);
    }

    private static List<Proposal> createProposals(Map<String, JsonNode> schemas, String fullyQualifiedName,
            String template, String documentation, ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        final var proposals       = new ArrayList<Proposal>();
        final var proposalStrings = proposalsWithImportsForTemplate(template, context);
        proposalStrings.add(template);
        proposalStrings.forEach(p -> proposals.add(new Proposal(fullyQualifiedName, p, documentation)));
        final var schema = schemas.get(fullyQualifiedName);
        if (null != schema) {
            for (var prefix : proposalStrings) {
                var extendedProposals = SchemaProposalsGenerator.getCodeTemplates(prefix, schema,
                        pdpConfiguration.variables());
                extendedProposals.forEach(p -> proposals.add(new Proposal(fullyQualifiedName, p, documentation)));
            }
        }
        return proposals;
    }

    /**
     * This method strips < and > characters and removes a potential parameter list
     * in brackets from the tail end of a generated template.
     * <p>
     * This is explicitly not using String.replace as there is no need for regular
     * expression processing and this is much more efficient.
     *
     * @param template a code template
     * @return the fully qualified function name in the template
     */
    private static String fullyQualifiedNameFromTemplate(String template) {
        final var sb = new StringBuilder();
        for (int i = 0; i < template.length(); i++) {
            final var c = template.charAt(i);
            if (c == '(')
                break;
            if (template.charAt(i) != '<' && template.charAt(i) != '>') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Collection<String> proposalsWithImportsForTemplate(String template, ContentAssistContext context) {
        final var proposals = new ArrayList<String>();
        if (context.getRootModel() instanceof SAPL sapl) {
            final var imports = Objects.requireNonNullElse(sapl.getImports(), List.<Import>of());
            for (var anImport : imports) {
                if (anImport instanceof WildcardImport wildcardImport) {
                    proposalsWithWildcard(wildcardImport, template).ifPresent(proposals::add);
                } else if (anImport instanceof LibraryImport libraryImport) {
                    proposalsWithLibraryImport(libraryImport, template).ifPresent(proposals::add);
                } else {
                    proposalsWithImport(anImport, template).ifPresent(proposals::add);
                }
            }
        }
        return proposals;
    }

    private static Optional<String> proposalsWithImport(Import anImport, String template) {
        final var steps              = anImport.getLibSteps();
        final var functionName       = anImport.getFunctionName();
        final var prefix             = joinStepsToPrefix(steps) + functionName;
        final var fullyQualifiedName = fullyQualifiedNameFromTemplate(template);
        if (fullyQualifiedName.startsWith(prefix)) {
            return Optional.of(template.replaceFirst(prefix, functionName));
        } else {
            return Optional.empty();
        }
    }

    private static Optional<String> proposalsWithWildcard(WildcardImport wildCard, String template) {
        final var prefix             = joinStepsToPrefix(wildCard.getLibSteps());
        final var fullyQualifiedName = fullyQualifiedNameFromTemplate(template);
        if (fullyQualifiedName.startsWith(prefix)) {
            return Optional.of(template.replaceFirst(prefix, ""));
        } else {
            return Optional.empty();
        }
    }

    private static String joinStepsToPrefix(EList<String> steps) {
        return joinStepsToName(steps) + '.';
    }

    private static String joinStepsToName(EList<String> steps) {
        return String.join(".", steps);
    }

    private static Optional<String> proposalsWithLibraryImport(LibraryImport libImport, String template) {
        final var shortPrefix        = String.join(".", libImport.getLibSteps());
        final var prefix             = shortPrefix + '.';
        final var fullyQualifiedName = fullyQualifiedNameFromTemplate(template);
        if (fullyQualifiedName.startsWith(prefix)) {
            return Optional.of(template.replaceFirst(shortPrefix, libImport.getLibAlias()));
        } else {
            return Optional.empty();
        }
    }

}
