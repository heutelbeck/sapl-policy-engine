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
package io.sapl.spring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for automatic JWT bearer token injection into
 * authorization subscription secrets.
 */
@Data
@ConfigurationProperties(prefix = "io.sapl.jwt")
public class SaplJwtProperties {

    /**
     * Whether to automatically inject the bearer token from
     * JwtAuthenticationToken into authorization subscription secrets. Defaults
     * to false. Requires explicit opt-in because passing a bearer token across
     * the PEP/PDP boundary is a security trade-off.
     */
    private boolean injectToken = false;

    /**
     * The key name under which the JWT token is stored in subscription secrets.
     * Must match the secretsKey configured in the JWT PIP configuration in
     * pdp.json. Defaults to "jwt".
     */
    private String secretsKey = "jwt";

}
