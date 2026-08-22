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
package io.sapl.attributeapigui.config;

import io.sapl.attributeapigui.connection.ConnectionMode;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

@Data
@ConfigurationProperties(prefix = "io.sapl.attribute-api-gui.connection")
public class AttributeApiConnectionProperties implements InitializingBean {

    private static final String ERROR_BASE_URL_NOT_SET = "The base url is not set. Please set io.sapl.attribute-api-gui.connection.base-url via settings";
    private static final String ERROR_MALFORMED_URL    = "The given url is not a valid url.";

    private String         baseUrl;
    private ConnectionMode method = ConnectionMode.NONE;
    private String         username;
    private String         password;
    private String         apiKey;

    @Override
    public void afterPropertiesSet() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(ERROR_BASE_URL_NOT_SET);
        }

        try {
            URI uri = URI.create(baseUrl);

            if (!isValidBaseUrl(uri)) {
                throw new IllegalStateException(ERROR_MALFORMED_URL + baseUrl);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(ERROR_MALFORMED_URL + baseUrl, e);
        }
    }

    private boolean isValidBaseUrl(URI uri) {
        return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
    }
}
