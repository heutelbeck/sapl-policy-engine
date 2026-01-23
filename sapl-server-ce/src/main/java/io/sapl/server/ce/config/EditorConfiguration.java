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
package io.sapl.server.ce.config;

import io.sapl.vaadin.lsp.JsonEditorConfiguration;
import io.sapl.vaadin.lsp.SaplEditorLspConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the SAPL and JSON LSP-based editors.
 * The WebSocket URL for LSP is not set here as it must be determined
 * dynamically
 * based on the current request context in each view.
 */
@Configuration
class EditorConfiguration {

    /**
     * Registers the bean {@link SaplEditorLspConfiguration} for SAPL policy
     * editing.
     * Note: The wsUrl is intentionally not set here. Views must set it dynamically
     * based on the current page URL to support deployment on any host/port.
     *
     * @return the editor configuration
     */
    @Bean
    SaplEditorLspConfiguration saplEditorLspConfiguration() {
        var configuration = new SaplEditorLspConfiguration();
        configuration.setHasLineNumbers(true);
        configuration.setAutocompleteTrigger(SaplEditorLspConfiguration.AutocompleteTrigger.ON_TYPING);
        configuration.setAutocompleteDelay(300);
        return configuration;
    }

    /**
     * Registers the bean {@link JsonEditorConfiguration} for JSON editing.
     *
     * @return the editor configuration
     */
    @Bean
    JsonEditorConfiguration jsonEditorLspConfiguration() {
        var configuration = new JsonEditorConfiguration();
        configuration.setHasLineNumbers(true);
        configuration.setLint(true);
        return configuration;
    }
}
