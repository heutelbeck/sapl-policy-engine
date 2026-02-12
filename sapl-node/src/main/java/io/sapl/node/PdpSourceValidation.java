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

import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.PDPDataSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * Validates that the configured PDP data source is supported by SAPL Node.
 *
 * <p>
 * SAPL Node loads policies from the filesystem and does not support the
 * RESOURCES data source, which requires classpath scanning via ClassGraph.
 * This validation fails fast with a clear error message instead of letting
 * the application fail later with a cryptic ClassGraph error.
 */
@Configuration
@RequiredArgsConstructor
class PdpSourceValidation {

    static final String ERROR_RESOURCES_NOT_SUPPORTED = "SAPL Node does not support RESOURCES as PDP configuration source. "
            + "Use DIRECTORY, MULTI_DIRECTORY, or BUNDLES instead. "
            + "Set 'io.sapl.pdp.embedded.pdp-config-type' to a supported value.";

    private final EmbeddedPDPProperties properties;

    @PostConstruct
    void validatePdpConfigType() {
        if (properties.getPdpConfigType() == PDPDataSource.RESOURCES) {
            throw new IllegalStateException(ERROR_RESOURCES_NOT_SUPPORTED);
        }
    }

}
