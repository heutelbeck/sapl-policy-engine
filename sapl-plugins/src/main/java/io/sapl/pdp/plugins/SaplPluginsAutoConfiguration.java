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

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionLibraryProvider;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties;
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
 * Each collaborator is exposed as its own Spring bean so the container manages
 * each lifecycle individually: the {@link SaplPluginManager} that loads
 * contributions from plugin JARs, and the {@link PluginsSource} that publishes
 * them into the PDP. The {@link PluginsSource} holds real resources (the plugin
 * manager and the directory watcher) and is closed by Spring on context
 * shutdown.
 * <p>
 * This auto-configuration is ordered {@code beforeName} the embedded PDP
 * auto-configuration (referenced by name to avoid a compile dependency on
 * {@code sapl-spring-pdp}) so the {@link PluginsSource} bean is registered
 * first. The embedded PDP's default {@code StaticPluginsSource} bean is
 * {@link ConditionalOnMissingBean} and therefore backs off, and its
 * {@code PdpVoterSource} subscribes to the hot-reloading source instead. The
 * {@link PluginsSource} bean is itself {@link ConditionalOnMissingBean} so an
 * application can replace it without opting out of the rest of the
 * auto-configuration.
 * <p>
 * The hot-reloading source discovers and republishes:
 * <ul>
 * <li>Function libraries contributed by plugins (recompiled into a fresh
 * {@link FunctionBroker} on each reload)</li>
 * <li>Policy information points contributed by plugins (reconciled against the
 * live {@link PolicyInformationPointAttributeBroker})</li>
 * <li>Host {@link DecisionInterceptor} beans carried in every bundle</li>
 * <li>Host {@link SubscriptionLifecycleListener} beans carried in every
 * bundle</li>
 * </ul>
 * <p>
 * Enablement is controlled via the {@code io.sapl.pdp.plugins.enabled} property
 * (enabled by default); the watched directory and further settings are bound
 * through {@link SaplPluginsProperties} with prefix
 * {@code io.sapl.pdp.plugins}.
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

    @Bean
    @ConditionalOnMissingBean
    PluginsSource pluginsSource(SaplPluginManager manager, EmbeddedPDPProperties properties,
            PolicyInformationPointAttributeBroker attributeBroker,
            ObjectProvider<DecisionInterceptor> decisionInterceptorProvider,
            ObjectProvider<SubscriptionLifecycleListener> lifecycleListenerProvider) {

        val decisionInterceptors = decisionInterceptorProvider.orderedStream().toList();
        val lifecycleListeners   = lifecycleListenerProvider.orderedStream().toList();
        if (!decisionInterceptors.isEmpty() || !lifecycleListeners.isEmpty()) {
            log.debug("Registering {} decision interceptors and {} lifecycle listeners.", decisionInterceptors.size(),
                    lifecycleListeners.size());
        }

        return new HotReloadingPluginsSource(manager, attributeBroker, properties.getFunctionCacheSize(), true,
                decisionInterceptors, lifecycleListeners);
    }
}
