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
package io.sapl.lsp.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.JWTPolicyInformationPoint;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.attributes.libraries.X509PolicyInformationPoint;
import io.sapl.documentation.LibraryDocumentationExtractor;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.DefaultLibraries;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads all standard SAPL function libraries and policy information points.
 * Creates a complete LSPConfiguration with documentation and the function
 * broker for all built-in libraries.
 */
@Slf4j
@UtilityClass
public class StandardLibrariesLoader {

    private static final List<Class<?>> POLICY_INFORMATION_POINTS = List.of(TimePolicyInformationPoint.class,
            HttpPolicyInformationPoint.class, JWTPolicyInformationPoint.class, X509PolicyInformationPoint.class);

    /**
     * Creates an LSPConfiguration with all standard libraries loaded.
     *
     * @param configurationId the configuration identifier
     * @return a fully configured LSPConfiguration with all standard libraries
     */
    public static LSPConfiguration loadStandardConfiguration(String configurationId) {
        var functionBroker = createFunctionBroker();
        var documentation  = createDocumentationBundle();

        log.info("Loaded {} function libraries and {} PIPs for LSP configuration", DefaultLibraries.defaults().size(),
                POLICY_INFORMATION_POINTS.size());

        return new LSPConfiguration(configurationId, documentation, Map.of(), functionBroker);
    }

    private static FunctionBroker createFunctionBroker() {
        var broker = new DefaultFunctionBroker();
        for (var library : DefaultLibraries.defaults()) {
            try {
                broker.load(library);
            } catch (Exception e) {
                log.warn("Failed to load function library {}: {}", library.getClass().getSimpleName(), e.getMessage());
            }
        }
        return broker;
    }

    private static DocumentationBundle createDocumentationBundle() {
        var libraries = new ArrayList<LibraryDocumentation>();

        for (var library : DefaultLibraries.defaults()) {
            try {
                libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(library.getClass()));
            } catch (Exception e) {
                log.warn("Failed to extract documentation from {}: {}", library.getClass().getSimpleName(),
                        e.getMessage());
            }
        }

        for (var pipClass : POLICY_INFORMATION_POINTS) {
            try {
                libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(pipClass));
            } catch (Exception e) {
                log.warn("Failed to extract documentation from {}: {}", pipClass.getSimpleName(), e.getMessage());
            }
        }

        return new DocumentationBundle(libraries);
    }

}
