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
package io.sapl.pdp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeBrokerException;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.HeapAttributeStorage;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.JWTKeyProvider;
import io.sapl.attributes.libraries.JWTPolicyInformationPoint;
import io.sapl.attributes.libraries.ReactiveWebClient;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.DefaultLibraries;
import io.sapl.functions.libraries.JWTFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import lombok.val;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating a Policy Decision Point with configurable
 * function
 * libraries and policy information points.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * var pdpComponents = PDPBuilder.withDefaults(objectMapper, clock).withFunctionLibrary(MyCustomLibrary.class)
 *         .withFunctionLibraryInstance(new StatefulLibrary(dependency)).withPolicyInformationPoint(new MyCustomPIP())
 *         .build();
 *
 * var pdp = pdpComponents.pdp();
 * var configRegister = pdpComponents.configurationRegister();
 * configRegister.loadConfiguration(pdpConfiguration, false);
 * }</pre>
 */
public class PolicyDecisionPointBuilder {

    private final ObjectMapper mapper;
    private final Clock        clock;

    private boolean includeDefaultFunctionLibraries       = true;
    private boolean includeDefaultPolicyInformationPoints = true;

    private final List<Class<?>> staticFunctionLibraries       = new ArrayList<>();
    private final List<Object>   instantiatedFunctionLibraries = new ArrayList<>();
    private final List<Object>   policyInformationPoints       = new ArrayList<>();

    private AttributeStorage  attributeStorage;
    private IdFactory         idFactory;
    private WebClient.Builder webClientBuilder;

    private PolicyDecisionPointBuilder(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock  = clock;
    }

    /**
     * Creates a builder with default function libraries and policy information
     * points enabled, using a new ObjectMapper and system UTC clock.
     *
     * @return a new builder instance
     */
    public static PolicyDecisionPointBuilder withDefaults() {
        return new PolicyDecisionPointBuilder(new ObjectMapper(), Clock.systemUTC());
    }

    /**
     * Creates a builder with default function libraries and policy information
     * points enabled.
     *
     * @param mapper the ObjectMapper for JSON processing
     * @param clock the clock for time-based operations
     * @return a new builder instance
     */
    public static PolicyDecisionPointBuilder withDefaults(ObjectMapper mapper, Clock clock) {
        return new PolicyDecisionPointBuilder(mapper, clock);
    }

    /**
     * Creates a builder without any default libraries or PIPs,
     * using a new ObjectMapper and system UTC clock.
     *
     * @return a new builder instance with defaults disabled
     */
    public static PolicyDecisionPointBuilder withoutDefaults() {
        return withoutDefaults(new ObjectMapper(), Clock.systemUTC());
    }

    /**
     * Creates a builder without any default libraries or PIPs.
     * Use this for minimal configurations or testing.
     *
     * @param mapper the ObjectMapper for JSON processing
     * @param clock the clock for time-based operations
     * @return a new builder instance with defaults disabled
     */
    public static PolicyDecisionPointBuilder withoutDefaults(ObjectMapper mapper, Clock clock) {
        return new PolicyDecisionPointBuilder(mapper, clock).withoutDefaultFunctionLibraries()
                .withoutDefaultPolicyInformationPoints();
    }

    /**
     * Disables loading of default function libraries.
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withoutDefaultFunctionLibraries() {
        this.includeDefaultFunctionLibraries = false;
        return this;
    }

    /**
     * Disables loading of default policy information points.
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withoutDefaultPolicyInformationPoints() {
        this.includeDefaultPolicyInformationPoints = false;
        return this;
    }

    /**
     * Adds a static function library class. The library will be instantiated
     * using its no-arg constructor.
     *
     * @param libraryClass the function library class
     * @return this builder
     */
    public PolicyDecisionPointBuilder withFunctionLibrary(Class<?> libraryClass) {
        this.staticFunctionLibraries.add(libraryClass);
        return this;
    }

    /**
     * Adds an already instantiated function library. Use this for libraries
     * that require constructor dependencies.
     *
     * @param libraryInstance the function library instance
     * @return this builder
     */
    public PolicyDecisionPointBuilder withFunctionLibraryInstance(Object libraryInstance) {
        this.instantiatedFunctionLibraries.add(libraryInstance);
        return this;
    }

    /**
     * Adds a policy information point instance.
     *
     * @param pip the policy information point
     * @return this builder
     */
    public PolicyDecisionPointBuilder withPolicyInformationPoint(Object pip) {
        this.policyInformationPoints.add(pip);
        return this;
    }

    /**
     * Sets the attribute storage implementation. If not set, a heap-based
     * in-memory storage will be used.
     *
     * @param attributeStorage the attribute storage
     * @return this builder
     */
    public PolicyDecisionPointBuilder withAttributeStorage(AttributeStorage attributeStorage) {
        this.attributeStorage = attributeStorage;
        return this;
    }

    /**
     * Sets a custom ID factory for generating subscription IDs.
     * If not set, a UUID-based factory will be used.
     *
     * @param idFactory the ID factory
     * @return this builder
     */
    public PolicyDecisionPointBuilder withIdFactory(IdFactory idFactory) {
        this.idFactory = idFactory;
        return this;
    }

    /**
     * Sets a custom WebClient builder for HTTP-based PIPs.
     * If not set, the default WebClient.builder() will be used.
     *
     * @param webClientBuilder the WebClient builder
     * @return this builder
     */
    public PolicyDecisionPointBuilder withWebClientBuilder(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
        return this;
    }

    /**
     * Builds the PDP and all related components.
     *
     * @return the PDP components including the PDP and configuration register
     * @throws InitializationException if function library initialization fails
     * @throws AttributeBrokerException if PIP initialization fails
     */
    public PDPComponents build() throws InitializationException, AttributeBrokerException {
        val functionBroker        = buildFunctionBroker();
        val attributeBroker       = buildAttributeBroker();
        val configurationRegister = new ConfigurationRegister(functionBroker, attributeBroker);
        val pdp                   = new DynamicPolicyDecisionPoint(configurationRegister, resolveIdFactory());

        return new PDPComponents(pdp, configurationRegister, functionBroker, attributeBroker);
    }

    private FunctionBroker buildFunctionBroker() throws InitializationException {
        val functionBroker = new DefaultFunctionBroker();

        if (includeDefaultFunctionLibraries) {
            for (val lib : DefaultLibraries.STATIC_LIBRARIES) {
                functionBroker.loadStaticFunctionLibrary(lib);
            }
            functionBroker.loadInstantiatedFunctionLibrary(new JWTFunctionLibrary(mapper));
        }

        for (val lib : staticFunctionLibraries) {
            functionBroker.loadStaticFunctionLibrary(lib);
        }

        for (val lib : instantiatedFunctionLibraries) {
            functionBroker.loadInstantiatedFunctionLibrary(lib);
        }

        return functionBroker;
    }

    private AttributeBroker buildAttributeBroker() throws AttributeBrokerException {
        val storage             = attributeStorage != null ? attributeStorage : new HeapAttributeStorage();
        val attributeRepository = new InMemoryAttributeRepository(clock, storage);
        val attributeBroker     = new CachingAttributeBroker(attributeRepository);

        if (includeDefaultPolicyInformationPoints) {
            val reactiveWebClient = new ReactiveWebClient(mapper);
            val httpPip           = new HttpPolicyInformationPoint(reactiveWebClient);
            attributeBroker.loadPolicyInformationPointLibrary(httpPip);

            val webClient      = webClientBuilder != null ? webClientBuilder : WebClient.builder();
            val jwtKeyProvider = new JWTKeyProvider(webClient);
            val jwtPip         = new JWTPolicyInformationPoint(jwtKeyProvider);
            attributeBroker.loadPolicyInformationPointLibrary(jwtPip);

            val timePip = new TimePolicyInformationPoint(clock);
            attributeBroker.loadPolicyInformationPointLibrary(timePip);
        }

        for (val pip : policyInformationPoints) {
            attributeBroker.loadPolicyInformationPointLibrary(pip);
        }

        return attributeBroker;
    }

    private IdFactory resolveIdFactory() {
        return idFactory != null ? idFactory : new UUIDFactory();
    }

    /**
     * Contains all components created by the PDP builder.
     *
     * @param pdp the policy decision point
     * @param configurationRegister the configuration register for loading policies
     * @param functionBroker the function broker with loaded libraries
     * @param attributeBroker the attribute broker with loaded PIPs
     */
    public record PDPComponents(
            PolicyDecisionPoint pdp,
            ConfigurationRegister configurationRegister,
            FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {}
}
