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
package io.sapl.attributeapi.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "io.sapl.attribute-api")
public class AttributeApiSecurityProperties {
    private boolean allowNoAuth;
    private boolean allowBasicAuth;
    private boolean allowApiKeyAuth;
    private boolean allowOAuth2Auth;

    private String          defaultTenantId = "default";
    private List<UserEntry> users           = List.of();
    private OAuth2          oauth2          = new OAuth2();

    @Data
    public static class UserEntry {
        private String id;
        private String tenantId;
        private Basic  basic;
        private ApiKey key;
    }

    @Data
    public static class Basic {
        private String username;
        private String secret;
    }

    @Data
    public static class ApiKey {
        private String hash;
    }

    @Data
    public static class OAuth2 {
        private String oidcPdpIdClaim = "tenantId";
    }
}
