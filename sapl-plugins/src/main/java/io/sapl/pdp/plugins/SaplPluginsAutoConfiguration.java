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
package io.sapl.pdp.plugins;

import io.sapl.api.functions.FunctionLibraryProvider;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;

/**
 * Auto-configuration for PF4J-based SAPL plugin hot-reloading.
 * <p>
 * Ordered {@code beforeName} the embedded PDP auto-configuration (referenced by
 * name to avoid a compile dependency on {@code sapl-spring-pdp}) so the
 * {@link PluginsSource} bean below is registered first. The embedded PDP's
 * default {@code StaticPluginsSource} bean is {@code @ConditionalOnMissingBean}
 * and therefore backs off, and its {@code PdpVoterSource} subscribes to the
 * hot-reloading source instead.
 */
@Slf4j
@AutoConfiguration(beforeName = "io.sapl.spring.pdp.embedded.PDPAutoConfiguration")
@EnableConfigurationProperties(SaplPluginsProperties.class)
@ConditionalOnProperty(prefix = "io.sapl.pdp.plugins", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SaplPluginsAutoConfiguration {

    @Bean
    SaplPluginManager saplPluginManager(SaplPluginsProperties properties) {
        return new SaplPluginManager(properties);
    }

    /**
     * The hot-reloading plugins source, exposed as a {@link PluginsSource} so it
     * overrides the embedded PDP's default static source via
     * {@code @ConditionalOnMissingBean}. The injected
     * {@link PolicyInformationPointAttributeBroker} is the same singleton the PDP
     * uses, so plugin PIPs are reconciled against the live broker.
     *
     * @param manager the plugin manager providing the contributions
     * @param properties the plugin configuration (watched directory)
     * @param attributeBroker the live broker plugin PIPs are loaded into
     * @param functionLibraryProviders host function-library providers
     * @param decisionInterceptorProvider host decision interceptors
     * @param lifecycleListenerProvider host subscription lifecycle listeners
     * @return the hot-reloading plugins source
     */
    @Bean
    @ConditionalOnMissingBean
    PluginsSource pluginsSource(SaplPluginManager manager, SaplPluginsProperties properties,
            PolicyInformationPointAttributeBroker attributeBroker,
            ObjectProvider<FunctionLibraryProvider> functionLibraryProviders,
            ObjectProvider<DecisionInterceptor> decisionInterceptorProvider,
            ObjectProvider<SubscriptionLifecycleListener> lifecycleListenerProvider) {

        // Host-provided base libraries kept across reloads. The plugin manager is
        // itself a FunctionLibraryProvider bean and is excluded here: the source
        // adds its (reloadable) libraries on top of the base set on every
        // republish, so including it would double-count plugin libraries.
        val baseFunctionLibraries = new ArrayList<Object>();
        functionLibraryProviders.orderedStream().filter(provider -> provider != manager)
                .forEach(provider -> baseFunctionLibraries.addAll(provider.functionLibraries()));

        val decisionInterceptors = decisionInterceptorProvider.orderedStream().toList();
        val lifecycleListeners   = lifecycleListenerProvider.orderedStream().toList();

        log.info("Enabling hot-reloading SAPL plugins from directory: {}", properties.getPluginsPath());
        return new HotReloadingPluginsSource(properties.getPluginsPath(), manager, attributeBroker, 0, true,
                baseFunctionLibraries, decisionInterceptors, lifecycleListeners);
    }
}
