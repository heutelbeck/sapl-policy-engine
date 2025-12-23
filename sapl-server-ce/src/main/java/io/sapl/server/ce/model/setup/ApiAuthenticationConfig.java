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
package io.sapl.server.ce.model.setup;

import lombok.Getter;
import lombok.Setter;

import java.net.URI;

@Getter
@Setter
public class ApiAuthenticationConfig {
    static final String BASICAUTHENABLED_PATH     = "io.sapl.server.allowBasicAuth";
    static final String APIKEYAUTHENABLED_PATH    = "io.sapl.server.allowApiKeyAuth";
    static final String APIKEYCACHINGENABLED_PATH = "io.sapl.server.apiKeyCaching.enabled";
    static final String APIKEYCACHINGEXPIRE_PATH  = "io.sapl.server.apiKeyCaching.expire";
    static final String APIKEYCACHINGMAXSIZE_PATH = "io.sapl.server.apiKeyCaching.maxSize";
    static final String OUATH2ENABLED_PATH        = "io.sapl.server.allowOauth2Auth";
    static final String OAUTH2RESOURCESERVER_PATH = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    private boolean basicAuthEnabled      = false;
    private boolean apiKeyAuthEnabled     = false;
    private boolean apiKeyCachingEnabled  = false;
    private int     apiKeyCachingExpires  = 300;
    private int     apiKeyCachingMaxSize  = 10000;
    private boolean oAuth2AuthEnabled     = false;
    private String  oAuth2RessourceServer = "";
    private boolean saved                 = false;

    public boolean isValidConfig() {
        return (!apiKeyAuthEnabled || isValidApiKeyAuthConfig()) && (!oAuth2AuthEnabled || isValidOAuth2Config());
    }

    private boolean isValidApiKeyAuthConfig() {
        return !apiKeyCachingEnabled || (apiKeyCachingExpires > 0 && apiKeyCachingMaxSize > 0);
    }

    private boolean isValidOAuth2Config() {
        return !oAuth2RessourceServer.isEmpty() && isValidOAuth2RessourceServerUrl();
    }

    public boolean isValidOAuth2RessourceServerUrl() {
        URI.create(this.oAuth2RessourceServer);
        return true;
    }

}
