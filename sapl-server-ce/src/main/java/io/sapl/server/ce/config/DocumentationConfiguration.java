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
package io.sapl.server.ce.config;

import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.JWTPolicyInformationPoint;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.documentation.LibraryDocumentationExtractor;
import io.sapl.extensions.mqtt.MqttFunctionLibrary;
import io.sapl.extensions.mqtt.MqttPolicyInformationPoint;
import io.sapl.functions.DefaultLibraries;
import io.sapl.functions.geo.GeographicFunctionLibrary;
import io.sapl.functions.geo.traccar.TraccarFunctionLibrary;
import io.sapl.pip.geo.traccar.TraccarPolicyInformationPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;

/**
 * Configuration for the documentation bundle that provides library
 * documentation
 * for the UI documentation view.
 */
@Configuration
class DocumentationConfiguration {

    /**
     * Creates a documentation bundle containing all function library and PIP
     * documentation.
     *
     * @return the complete documentation bundle
     */
    @Bean
    DocumentationBundle documentationBundle() {
        var libraries = new ArrayList<LibraryDocumentation>();

        // Extract documentation for default function libraries
        for (var libraryClass : DefaultLibraries.STATIC_LIBRARIES) {
            libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(libraryClass));
        }

        // Extract documentation for additional function libraries
        libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(GeographicFunctionLibrary.class));
        libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(MqttFunctionLibrary.class));
        libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(TraccarFunctionLibrary.class));

        // Extract documentation for Policy Information Points
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(TimePolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(HttpPolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(JWTPolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(TraccarPolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(MqttPolicyInformationPoint.class));

        return new DocumentationBundle(libraries);
    }
}
