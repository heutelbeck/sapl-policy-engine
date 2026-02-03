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
package io.sapl.vaadin.lsp;

import java.io.Serial;

import io.sapl.api.SaplVersion;
import lombok.extern.slf4j.Slf4j;

/**
 * SAPLTest editor using CodeMirror 6 with Language Server Protocol (LSP)
 * integration.
 * This editor is pre-configured for the SAPLTest language used in policy
 * testing.
 */
@Slf4j
public class SaplTestEditorLsp extends SaplEditorLsp {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Creates a SAPLTest editor with default configuration.
     */
    public SaplTestEditorLsp() {
        this(new SaplEditorLspConfiguration());
    }

    /**
     * Creates a SAPLTest editor with the specified configuration.
     * The language is automatically set to "sapltest".
     *
     * @param config the editor configuration
     */
    public SaplTestEditorLsp(SaplEditorLspConfiguration config) {
        super(configureForSaplTest(config));
    }

    private static SaplEditorLspConfiguration configureForSaplTest(SaplEditorLspConfiguration config) {
        config.setLanguage("sapltest");
        return config;
    }
}
