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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.Value;
import io.sapl.documentation.LibraryDocumentationExtractor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * Factory for creating content assist configurations from broker registrations.
 * Extracts documentation from all registered function libraries and policy
 * information points, creating a configuration suitable for IDE content assist
 * features.
 * <p>
 * This factory is reusable across different applications (playground,
 * server-ce,
 * web-editor demo) that need to provide content assist for SAPL policy editing.
 */
@Slf4j
@UtilityClass
public class ContentAssistConfigurationFactory {

    /**
     * Creates a content assist configuration from the given brokers.
     * Extracts documentation from all registered libraries in both brokers.
     *
     * @param pdpId unique identifier for this PDP configuration
     * @param configurationId identifier for this specific configuration version
     * @param variables environment variables available during evaluation
     * @param functionBroker broker containing registered function libraries
     * @param attributeBroker broker containing registered policy information points
     * @return the content assist configuration
     */
    public static ContentAssistPDPConfiguration createConfiguration(String pdpId, String configurationId,
            Map<String, Value> variables, FunctionBroker functionBroker, AttributeBroker attributeBroker) {

        val documentationBundle = extractDocumentation(functionBroker, attributeBroker);

        return new ContentAssistPDPConfiguration(pdpId, configurationId, variables, documentationBundle, functionBroker,
                attributeBroker);
    }

    /**
     * Creates a content assist configuration source that returns a single
     * configuration for any configuration ID.
     * Useful for simple setups where only one configuration is needed.
     *
     * @param pdpId unique identifier for this PDP configuration
     * @param configurationId identifier for this specific configuration version
     * @param variables environment variables available during evaluation
     * @param functionBroker broker containing registered function libraries
     * @param attributeBroker broker containing registered policy information points
     * @return a configuration source returning the created configuration
     */
    public static ContentAssistConfigurationSource createSource(String pdpId, String configurationId,
            Map<String, Value> variables, FunctionBroker functionBroker, AttributeBroker attributeBroker) {

        val configuration = createConfiguration(pdpId, configurationId, variables, functionBroker, attributeBroker);
        return id -> Optional.of(configuration);
    }

    /**
     * Extracts documentation from all registered libraries in the given brokers.
     *
     * @param functionBroker broker containing registered function libraries
     * @param attributeBroker broker containing registered policy information points
     * @return documentation bundle containing all extracted documentation
     */
    public static DocumentationBundle extractDocumentation(FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {

        val allDocumentation = new ArrayList<LibraryDocumentation>();

        for (var libraryClass : functionBroker.getRegisteredLibraries()) {
            if (libraryClass.isAnnotationPresent(FunctionLibrary.class)) {
                try {
                    allDocumentation.add(LibraryDocumentationExtractor.extractFunctionLibrary(libraryClass));
                } catch (Exception exception) {
                    log.debug("Failed to extract documentation from function library {}: {}", libraryClass.getName(),
                            exception.getMessage());
                }
            }
        }

        for (var pipClass : attributeBroker.getRegisteredLibraries()) {
            if (pipClass.isAnnotationPresent(PolicyInformationPoint.class)) {
                try {
                    allDocumentation.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(pipClass));
                } catch (Exception exception) {
                    log.debug("Failed to extract documentation from PIP {}: {}", pipClass.getName(),
                            exception.getMessage());
                }
            }
        }

        return new DocumentationBundle(allDocumentation);
    }

}
