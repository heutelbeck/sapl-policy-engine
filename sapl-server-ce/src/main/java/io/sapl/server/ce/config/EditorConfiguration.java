/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.sapl.grammar.web.SAPLServlet;
import io.sapl.vaadin.JsonEditorConfiguration;
import io.sapl.vaadin.SaplEditorConfiguration;

/**
 * Collection of initialization methods for Spring beans for editors.
 */
@Configuration
@ComponentScan("io.sapl.grammar.ide.contentassist")
class EditorConfiguration {
    /**
     * Registers the bean {@link ServletRegistrationBean} with generic type
     * {@link SAPLServlet}.
     *
     * @return the value
     */
    @Bean
    ServletRegistrationBean<SAPLServlet> registerXTextRegistrationBean() {
        ServletRegistrationBean<SAPLServlet> registration = new ServletRegistrationBean<>(new SAPLServlet(),
                "/xtext-service/*");
        registration.setName("XtextServices");
        registration.setAsyncSupported(true);
        return registration;
    }

    /**
     * Registers the bean {@link SaplEditorConfiguration}.
     *
     * @return the value
     */
    @Bean
    SaplEditorConfiguration registerSaplEditorConfiguration() {
        SaplEditorConfiguration saplEditorConfiguration = new SaplEditorConfiguration();
        saplEditorConfiguration.setAutoCloseBrackets(true);
        saplEditorConfiguration.setHasLineNumbers(true);
        saplEditorConfiguration.setMatchBrackets(true);
        saplEditorConfiguration.setTextUpdateDelay(1);

        return saplEditorConfiguration;
    }

    @Bean
    JsonEditorConfiguration registerJsonEditorConfiguration() {
        JsonEditorConfiguration configuration = new JsonEditorConfiguration();
        configuration.setAutoCloseBrackets(true);
        configuration.setHasLineNumbers(true);
        configuration.setMatchBrackets(true);
        configuration.setTextUpdateDelay(1);

        return configuration;
    }
}
