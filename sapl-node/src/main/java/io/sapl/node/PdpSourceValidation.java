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

    static final String ERROR_RESOURCES_NOT_SUPPORTED  = "SAPL Node refused to start. The RESOURCES policy source is not supported.";
    static final String ACTION_RESOURCES_NOT_SUPPORTED = """
            RESOURCES requires classpath scanning, which the SAPL Node binary
            does not ship. Use one of:

              io.sapl.pdp.embedded.pdp-config-type=DIRECTORY        single-directory layout
              io.sapl.pdp.embedded.pdp-config-type=MULTI_DIRECTORY  one subdirectory per tenant
              io.sapl.pdp.embedded.pdp-config-type=BUNDLES          signed bundle files

            See https://sapl.io/docs/latest/7_2_Configuration for the full
            policy source reference.""";

    private final EmbeddedPDPProperties properties;

    @PostConstruct
    void validatePdpConfigType() {
        if (properties.getPdpConfigType() == PDPDataSource.RESOURCES) {
            throw new SaplStartupConfigurationException(ERROR_RESOURCES_NOT_SUPPORTED, ACTION_RESOURCES_NOT_SUPPORTED);
        }
    }

}
