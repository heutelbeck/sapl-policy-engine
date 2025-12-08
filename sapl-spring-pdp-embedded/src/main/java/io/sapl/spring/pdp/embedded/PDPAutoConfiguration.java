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
package io.sapl.spring.pdp.embedded;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.functions.FunctionLibraryClassProvider;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.internal.TracedDecisionInterceptor;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import io.sapl.pdp.configuration.PDPConfigurationSource;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.PDPDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Auto-configuration for the embedded Policy Decision Point.
 * <p>
 * This configuration creates a {@link PolicyDecisionPoint} using the
 * {@link PolicyDecisionPointBuilder}. It automatically discovers and registers:
 * <ul>
 * <li>Custom function libraries (beans annotated with
 * {@link FunctionLibrary})</li>
 * <li>Custom policy information points (beans annotated with
 * {@link PolicyInformationPoint})</li>
 * <li>Decision interceptors (beans implementing
 * {@link TracedDecisionInterceptor})</li>
 * </ul>
 * <p>
 * Configuration is controlled via {@link EmbeddedPDPProperties} with prefix
 * {@code io.sapl.pdp.embedded}.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(EmbeddedPDPProperties.class)
public class PDPAutoConfiguration {

    private PDPComponents pdpComponents;

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PolicyDecisionPoint policyDecisionPoint(ObjectMapper mapper, Clock clock,
            ObjectProvider<TracedDecisionInterceptor> interceptorProvider,
            ObjectProvider<FunctionLibraryClassProvider> functionLibraryClassProviders,
            ApplicationContext applicationContext, EmbeddedPDPProperties properties) {

        log.info("Deploying embedded Policy Decision Point. Source: {}, Path: {}", properties.getPdpConfigType(),
                properties.getPoliciesPath());

        val builder = PolicyDecisionPointBuilder.withDefaults(mapper, clock);

        // Collect static function library classes from providers
        val functionLibraryClasses = collectFunctionLibraryClasses(functionLibraryClassProviders);
        if (!functionLibraryClasses.isEmpty()) {
            log.debug("Registering {} static function library classes", functionLibraryClasses.size());
            builder.withFunctionLibraries(functionLibraryClasses);
        }

        // Collect custom function libraries from application context
        val functionLibraries = collectFunctionLibraries(applicationContext);
        if (!functionLibraries.isEmpty()) {
            log.debug("Registering {} custom function library instances", functionLibraries.size());
            builder.withFunctionLibraryInstances(functionLibraries);
        }

        // Collect custom PIPs from application context
        val pips = collectPolicyInformationPoints(applicationContext);
        if (!pips.isEmpty()) {
            log.debug("Registering {} custom policy information points", pips.size());
            builder.withPolicyInformationPoints(pips);
        }

        // Collect interceptors
        val interceptors = interceptorProvider.orderedStream().toList();
        if (!interceptors.isEmpty()) {
            log.debug("Registering {} decision interceptors: {}.", interceptors.size(),
                    interceptors.stream().map(i -> i.getClass().getSimpleName()).toList());
            builder.withInterceptors(interceptors);
        }

        // Configure policy source based on properties
        configureSource(builder, properties);

        // Build and store components for cleanup
        this.pdpComponents = builder.build();

        return pdpComponents.pdp();
    }

    private void configureSource(PolicyDecisionPointBuilder builder, EmbeddedPDPProperties properties) {
        if (properties.getPdpConfigType() == PDPDataSource.FILESYSTEM) {
            val path         = PDPConfigurationSource.resolveHomeFolderIfPresent(properties.getPoliciesPath());
            val resolvedPath = path.toAbsolutePath().normalize();
            log.info("Loading policies from filesystem: {}", resolvedPath);
            builder.withDirectorySource(path);
        } else {
            val resourcePath = properties.getPoliciesPath();
            log.info("Loading policies from resources: {}", resourcePath);
            builder.withResourcesSource(resourcePath);
        }
    }

    private List<Object> collectFunctionLibraries(ApplicationContext context) {
        val libraries = new ArrayList<>();
        val beanNames = context.getBeanNamesForAnnotation(FunctionLibrary.class);
        for (val beanName : beanNames) {
            val bean = context.getBean(beanName);
            log.debug("Found function library bean: {} ({})", beanName, bean.getClass().getSimpleName());
            libraries.add(bean);
        }
        return libraries;
    }

    private List<Object> collectPolicyInformationPoints(ApplicationContext context) {
        val pips      = new ArrayList<>();
        val beanNames = context.getBeanNamesForAnnotation(PolicyInformationPoint.class);
        for (val beanName : beanNames) {
            val bean = context.getBean(beanName);
            log.debug("Found PIP bean: {} ({})", beanName, bean.getClass().getSimpleName());
            pips.add(bean);
        }
        return pips;
    }

    private List<Class<?>> collectFunctionLibraryClasses(ObjectProvider<FunctionLibraryClassProvider> providers) {
        val classes = new ArrayList<Class<?>>();
        providers.orderedStream().forEach(provider -> {
            val providerClasses = provider.functionLibraryClasses();
            log.debug("Found {} function library classes from provider {}", providerClasses.size(),
                    provider.getClass().getSimpleName());
            classes.addAll(providerClasses);
        });
        return classes;
    }

    @PreDestroy
    void cleanup() {
        if (pdpComponents != null) {
            log.debug("Disposing PDP resources");
            pdpComponents.dispose();
        }
    }

}
