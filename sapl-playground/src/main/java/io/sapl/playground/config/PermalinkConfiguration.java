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
package io.sapl.playground.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for permalink generation. Controls the base URL used
 * when creating shareable links.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "sapl.playground")
public class PermalinkConfiguration {

    /**
     * Base URL for generating permalinks. Should include protocol and hostname,
     * without trailing slash. Example:
     * <a href="http://localhost:8080">http://localhost:8080</a> or
     * <a href="https://playground.sapl.io">https://playground.sapl.io</a>
     */
    private String baseUrl = "http://localhost:8080";
}
