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

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.JWTKeyProvider;
import io.sapl.attributes.libraries.JWTPolicyInformationPoint;
import io.sapl.attributes.libraries.ReactiveWebClient;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.attributes.libraries.X509PolicyInformationPoint;
import io.sapl.documentation.LibraryDocumentationExtractor;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.ArrayFunctionLibrary;
import io.sapl.functions.libraries.BitwiseFunctionLibrary;
import io.sapl.functions.libraries.CidrFunctionLibrary;
import io.sapl.functions.libraries.CsvFunctionLibrary;
import io.sapl.functions.libraries.DigestFunctionLibrary;
import io.sapl.functions.libraries.EncodingFunctionLibrary;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.functions.libraries.GraphFunctionLibrary;
import io.sapl.functions.libraries.GraphQLFunctionLibrary;
import io.sapl.functions.libraries.JWTFunctionLibrary;
import io.sapl.functions.libraries.JsonFunctionLibrary;
import io.sapl.functions.libraries.KeysFunctionLibrary;
import io.sapl.functions.libraries.MacFunctionLibrary;
import io.sapl.functions.libraries.MathFunctionLibrary;
import io.sapl.functions.libraries.NumeralFunctionLibrary;
import io.sapl.functions.libraries.ObjectFunctionLibrary;
import io.sapl.functions.libraries.PatternsFunctionLibrary;
import io.sapl.functions.libraries.PermissionsFunctionLibrary;
import io.sapl.functions.libraries.ReflectionFunctionLibrary;
import io.sapl.functions.libraries.SanitizationFunctionLibrary;
import io.sapl.functions.libraries.SaplFunctionLibrary;
import io.sapl.functions.libraries.SchemaValidationLibrary;
import io.sapl.functions.libraries.SemVerFunctionLibrary;
import io.sapl.functions.libraries.SignatureFunctionLibrary;
import io.sapl.functions.libraries.StandardFunctionLibrary;
import io.sapl.functions.libraries.StringFunctionLibrary;
import io.sapl.functions.libraries.TemporalFunctionLibrary;
import io.sapl.functions.libraries.TomlFunctionLibrary;
import io.sapl.functions.libraries.UnitsFunctionLibrary;
import io.sapl.functions.libraries.UuidFunctionLibrary;
import io.sapl.functions.libraries.X509FunctionLibrary;
import io.sapl.functions.libraries.XmlFunctionLibrary;
import io.sapl.functions.libraries.YamlFunctionLibrary;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;

/**
 * Loads all standard SAPL function libraries and policy information points.
 * Creates a complete LSPConfiguration with documentation and brokers for all
 * built-in libraries.
 */
@Slf4j
@UtilityClass
public class StandardLibrariesLoader {

    private static final List<Class<?>> FUNCTION_LIBRARIES = List.of(ArrayFunctionLibrary.class,
            BitwiseFunctionLibrary.class, CidrFunctionLibrary.class, CsvFunctionLibrary.class,
            DigestFunctionLibrary.class, EncodingFunctionLibrary.class, FilterFunctionLibrary.class,
            GraphFunctionLibrary.class, GraphQLFunctionLibrary.class, JsonFunctionLibrary.class,
            JWTFunctionLibrary.class, KeysFunctionLibrary.class, MacFunctionLibrary.class, MathFunctionLibrary.class,
            NumeralFunctionLibrary.class, ObjectFunctionLibrary.class, PatternsFunctionLibrary.class,
            PermissionsFunctionLibrary.class, ReflectionFunctionLibrary.class, SanitizationFunctionLibrary.class,
            SaplFunctionLibrary.class, SchemaValidationLibrary.class, SemVerFunctionLibrary.class,
            SignatureFunctionLibrary.class, StandardFunctionLibrary.class, StringFunctionLibrary.class,
            TemporalFunctionLibrary.class, TomlFunctionLibrary.class, UnitsFunctionLibrary.class,
            UuidFunctionLibrary.class, X509FunctionLibrary.class, XmlFunctionLibrary.class, YamlFunctionLibrary.class);

    private static final List<Class<?>> POLICY_INFORMATION_POINTS = List.of(TimePolicyInformationPoint.class,
            HttpPolicyInformationPoint.class, JWTPolicyInformationPoint.class, X509PolicyInformationPoint.class);

    private static final String ERROR_EXTERNAL_DATA_SOURCE_BLOCKED = "Access to external data sources is not available in the language server.";

    private static final Value PIP_CALL_BLOCKED = Value.error(ERROR_EXTERNAL_DATA_SOURCE_BLOCKED);

    /**
     * Creates an LSPConfiguration with all standard libraries loaded.
     *
     * @param configurationId the configuration identifier
     * @return a fully configured LSPConfiguration with all standard libraries
     */
    public static LSPConfiguration loadStandardConfiguration(String configurationId) {
        var functionBroker  = createFunctionBroker();
        var attributeBroker = createAttributeBroker();
        var documentation   = createDocumentationBundle();

        log.info("Loaded {} function libraries and {} PIPs for LSP configuration", FUNCTION_LIBRARIES.size(),
                POLICY_INFORMATION_POINTS.size());

        return new LSPConfiguration(configurationId, documentation, Map.of(), functionBroker, attributeBroker);
    }

    private static FunctionBroker createFunctionBroker() {
        var broker = new DefaultFunctionBroker();
        for (var libraryClass : FUNCTION_LIBRARIES) {
            try {
                broker.loadStaticFunctionLibrary(libraryClass);
            } catch (Exception e) {
                log.warn("Failed to load function library {}: {}", libraryClass.getSimpleName(), e.getMessage());
            }
        }
        return broker;
    }

    private static AttributeBroker createAttributeBroker() {
        var clock      = Clock.systemUTC();
        var repository = new InMemoryAttributeRepository(clock);
        var broker     = new CachingAttributeBroker(repository);

        try {
            broker.loadPolicyInformationPointLibrary(new TimePolicyInformationPoint(clock));
        } catch (Exception e) {
            log.warn("Failed to load TimePolicyInformationPoint: {}", e.getMessage());
        }

        try {
            var webClient = new DummyReactiveWebClient(JsonMapper.builder().build());
            broker.loadPolicyInformationPointLibrary(new HttpPolicyInformationPoint(webClient));
        } catch (Exception e) {
            log.warn("Failed to load HttpPolicyInformationPoint: {}", e.getMessage());
        }

        try {
            broker.loadPolicyInformationPointLibrary(new JWTPolicyInformationPoint(new DummyJWTKeyProvider()));
        } catch (Exception e) {
            log.warn("Failed to load JWTPolicyInformationPoint: {}", e.getMessage());
        }

        try {
            broker.loadPolicyInformationPointLibrary(new X509PolicyInformationPoint(clock));
        } catch (Exception e) {
            log.warn("Failed to load X509PolicyInformationPoint: {}", e.getMessage());
        }

        return broker;
    }

    private static DocumentationBundle createDocumentationBundle() {
        var libraries = new ArrayList<LibraryDocumentation>();

        for (var libraryClass : FUNCTION_LIBRARIES) {
            try {
                libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(libraryClass));
            } catch (Exception e) {
                log.warn("Failed to extract documentation from {}: {}", libraryClass.getSimpleName(), e.getMessage());
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

    private static class DummyReactiveWebClient extends ReactiveWebClient {

        DummyReactiveWebClient(JsonMapper mapper) {
            super(mapper);
        }

        @Override
        public Flux<Value> httpRequest(HttpMethod method, ObjectValue requestSettings) {
            return Flux.just(PIP_CALL_BLOCKED);
        }

        @Override
        public Flux<Value> consumeWebSocket(ObjectValue requestSettings) {
            return Flux.just(PIP_CALL_BLOCKED);
        }
    }

    private static class DummyJWTKeyProvider extends JWTKeyProvider {

        DummyJWTKeyProvider() {
            super();
        }

        @Override
        public Mono<RSAPublicKey> provide(String kid, JsonNode publicKeyServer) {
            return Mono.error(new UnsupportedOperationException(ERROR_EXTERNAL_DATA_SOURCE_BLOCKED));
        }
    }

}
