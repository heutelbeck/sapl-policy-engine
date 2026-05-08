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
package io.sapl.server.docs;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

/**
 * Seeds Scalar UI and springdoc defaults into the environment with the lowest
 * precedence: operator-supplied configuration always wins, but if nothing is
 * configured the SAPL-server defaults take effect (Scalar mounts at `/`,
 * springdoc serves at `/v3/api-docs`, swagger-ui is disabled).
 * <p>
 * Implemented as an {@link EnvironmentPostProcessor} (registered in
 * `META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports`)
 * so the values are available before {@code @ConfigurationProperties} binding
 * happens during auto-configuration.
 */
public class SaplWebfluxEndpointDefaultsPostProcessor implements EnvironmentPostProcessor {

    private static final String DEFAULTS_RESOURCE    = "io/sapl/server/sapl-webflux-endpoint-defaults.properties";
    private static final String PROPERTY_SOURCE_NAME = "sapl-webflux-endpoint-defaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            final Properties properties = PropertiesLoaderUtils
                    .loadProperties(new ClassPathResource(DEFAULTS_RESOURCE));
            environment.getPropertySources().addLast(new PropertiesPropertySource(PROPERTY_SOURCE_NAME, properties));
        } catch (IOException e) {
            throw new IllegalStateException("Could not load SAPL webflux endpoint defaults from " + DEFAULTS_RESOURCE,
                    e);
        }
    }
}
