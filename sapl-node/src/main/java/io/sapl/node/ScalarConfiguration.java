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
package io.sapl.node;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.scalar.maven.core.ScalarProperties;
import com.scalar.maven.webmvc.ScalarWebMvcController;
import com.scalar.maven.webmvc.SpringBootScalarProperties;

import jakarta.servlet.http.HttpServletRequest;
import lombok.val;

/**
 * Registers the Scalar MVC controller. The auto-config in scalar-webmvc
 * 0.6.34 declares the controller bean inside a plain {@code @Configuration}
 * whose {@code @Bean} method does not get invoked under our application's
 * narrowed {@code @ComponentScan}. Wiring the controller explicitly here
 * sidesteps the issue.
 * <p>
 * The controller also applies the SAPL theme: the classpath stylesheet
 * {@code static/css/sapl-scalar-theme.css} is loaded once at startup and
 * injected as the rendered page's {@code customCss}, so the Scalar
 * reference UI matches the sapl.io palette.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SpringBootScalarProperties.class)
class ScalarConfiguration {

    private static final String SAPL_SCALAR_THEME_CSS = "static/css/sapl-scalar-theme.css";

    @Bean
    ScalarWebMvcController scalarWebMvcController() {
        val themeCss = loadThemeCss();
        return new ScalarWebMvcController() {
            @Override
            protected ScalarProperties configureProperties(ScalarProperties properties, HttpServletRequest request) {
                if (themeCss != null) {
                    properties.setCustomCss(themeCss);
                }
                return properties;
            }
        };
    }

    private static String loadThemeCss() {
        try (InputStream in = new ClassPathResource(SAPL_SCALAR_THEME_CSS).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Maps {@code .yaml} and {@code .yml} to {@code application/yaml} so the
     * static {@code /openapi/pdp-http.yaml} resource is served with a media
     * type Scalar's spec loader recognises. Jetty's default mappings serve
     * unknown extensions as {@code application/octet-stream}, which Scalar
     * silently drops.
     */
    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> openApiYamlMimeMapping() {
        return factory -> {
            val mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("yaml", "application/yaml");
            mappings.add("yml", "application/yaml");
            factory.setMimeMappings(mappings);
        };
    }

}
