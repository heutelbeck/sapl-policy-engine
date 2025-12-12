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
package io.sapl.languageserver;

import java.time.Clock;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.DefaultLibraries;
import io.sapl.grammar.ide.contentassist.ContentAssistConfigurationFactory;
import io.sapl.grammar.ide.contentassist.ContentAssistConfigurationSource;

/**
 * Configuration for the SAPL Language Server providing content assist support.
 */
@Configuration
public class LanguageServerConfiguration {

    @Bean
    FunctionBroker functionBroker() {
        var broker = new DefaultFunctionBroker();
        for (var libraryClass : DefaultLibraries.STATIC_LIBRARIES) {
            broker.loadStaticFunctionLibrary(libraryClass);
        }
        return broker;
    }

    @Bean
    AttributeBroker attributeBroker() {
        var repository = new InMemoryAttributeRepository(Clock.systemUTC());
        var broker     = new CachingAttributeBroker(repository);
        broker.loadPolicyInformationPointLibrary(new TimePolicyInformationPoint(Clock.systemUTC()));
        return broker;
    }

    @Bean
    ContentAssistConfigurationSource contentAssistConfigurationSource(FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {
        return ContentAssistConfigurationFactory.createSource("lsp", "1", Map.of(), functionBroker, attributeBroker);
    }

    @Bean
    DocumentationBundle documentationBundle(FunctionBroker functionBroker, AttributeBroker attributeBroker) {
        return ContentAssistConfigurationFactory.extractDocumentation(functionBroker, attributeBroker);
    }

}
