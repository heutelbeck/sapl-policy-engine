/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.lt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "io.sapl.server-lt")
public class SAPLServerLTProperties {

    // authentication methods
    private boolean allowNoAuth     = false;
    private boolean allowBasicAuth  = true;
    private boolean allowApiKeyAuth = false;
    private boolean allowOauth2Auth = false;

    // Basic authentication
    private String key    = "";
    private String secret = "";

    // API Key authentication
    private String       apiKeyHeaderName = "API_KEY";
    private List<String> allowedApiKeys   = List.of();

    public List<String> getAllowedApiKeys() {
        return Collections.unmodifiableList(allowedApiKeys);
    }

    public void setAllowedApiKeys(Collection<String> allowedApiKeys) {
        for (String apiKey : allowedApiKeys) {
            assertIsValidApiKey(apiKey);
        }
        this.allowedApiKeys = new ArrayList<>(allowedApiKeys);
    }

    private void assertIsValidApiKey(String key) {
        if (key.length() < SecretGenerator.MIN_API_KEY_LENGTH) {
            throw new IllegalStateException("Detected short API key in configuration. API key must be at least "
                    + SecretGenerator.MIN_API_KEY_LENGTH + " characters long.");
        }
    }
}
