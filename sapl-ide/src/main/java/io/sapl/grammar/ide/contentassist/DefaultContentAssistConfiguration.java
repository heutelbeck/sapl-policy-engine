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
import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.documentation.LibraryDocumentationExtractor;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.DefaultLibraries;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default configuration for content assist in the SAPL IDE and language server.
 * Provides documentation and function/attribute brokers for all default SAPL
 * function libraries and Policy Information Points.
 * <p>
 * This class is not annotated with {@code @Configuration} to avoid conflicts
 * when used in Spring Boot test contexts that provide their own configuration.
 * Use the {@link Factory} class to create instances in non-Spring contexts like
 * Eclipse, or manually import this configuration in Spring contexts where
 * needed.
 */
@Slf4j
public class DefaultContentAssistConfiguration {

    /**
     * Creates a content assist configuration source that extracts documentation
     * from all default SAPL function libraries and PIPs, and loads them into
     * working brokers for expression evaluation.
     *
     * @return a configuration source with default library documentation and brokers
     */
    public ContentAssistConfigurationSource contentAssistConfigurationSource() {
        var functionBroker   = createFunctionBroker();
        var attributeBroker  = createAttributeBroker();
        var functionDocs     = extractFunctionLibraryDocumentation();
        var pipDocs          = extractPolicyInformationPointDocumentation();
        var allDocumentation = new ArrayList<>(functionDocs);
        allDocumentation.addAll(pipDocs);

        var documentationBundle = new DocumentationBundle(List.copyOf(allDocumentation));

        var configuration = new ContentAssistPDPConfiguration("defaultPdp", "default", Map.of(), documentationBundle,
                functionBroker, attributeBroker);

        log.info("Loaded content assist configuration with {} function libraries and {} PIPs", functionDocs.size(),
                pipDocs.size());

        return configId -> Optional.of(configuration);
    }

    private FunctionBroker createFunctionBroker() {
        var broker = new DefaultFunctionBroker();
        for (var libraryClass : DefaultLibraries.STATIC_LIBRARIES) {
            try {
                broker.loadStaticFunctionLibrary(libraryClass);
            } catch (Exception exception) {
                log.warn("Failed to load function library: {}", libraryClass.getName(), exception);
            }
        }
        return broker;
    }

    private AttributeBroker createAttributeBroker() {
        var repository = new InMemoryAttributeRepository(Clock.systemUTC());
        var broker     = new CachingAttributeBroker(repository);
        try {
            broker.loadPolicyInformationPointLibrary(new TimePolicyInformationPoint(Clock.systemUTC()));
        } catch (Exception exception) {
            log.warn("Failed to load TimePolicyInformationPoint", exception);
        }
        return broker;
    }

    private List<LibraryDocumentation> extractFunctionLibraryDocumentation() {
        var functionDocs = new ArrayList<LibraryDocumentation>();
        for (var libraryClass : DefaultLibraries.STATIC_LIBRARIES) {
            try {
                var doc = LibraryDocumentationExtractor.extractFunctionLibrary(libraryClass);
                functionDocs.add(doc);
            } catch (Exception exception) {
                log.warn("Failed to extract documentation from function library: {}", libraryClass.getName(),
                        exception);
            }
        }
        return functionDocs;
    }

    private List<LibraryDocumentation> extractPolicyInformationPointDocumentation() {
        var pipDocs = new ArrayList<LibraryDocumentation>();
        try {
            var doc = LibraryDocumentationExtractor.extractPolicyInformationPoint(TimePolicyInformationPoint.class);
            pipDocs.add(doc);
        } catch (Exception exception) {
            log.warn("Failed to extract documentation from TimePolicyInformationPoint", exception);
        }
        return pipDocs;
    }

    /**
     * Factory class for creating content assist configurations without Spring.
     * Use this for Eclipse plugin and other non-Spring contexts.
     */
    @UtilityClass
    public static class Factory {

        /**
         * Creates a content assist configuration source with all default libraries
         * loaded.
         *
         * @return a configuration source ready for content assist
         */
        public static ContentAssistConfigurationSource create() {
            var instance = new DefaultContentAssistConfiguration();
            return instance.contentAssistConfigurationSource();
        }

    }

}
