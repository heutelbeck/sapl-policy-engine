/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.pdpcontroller;

import io.sapl.api.model.jackson.SaplJacksonModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that registers the SaplJacksonModule for JSON
 * serialization and deserialization of SAPL types.
 */
@AutoConfiguration
public class SaplJacksonAutoConfiguration {

    /**
     * Registers the SaplJacksonModule bean if not already present.
     *
     * @return the SAPL Jackson module
     */
    @Bean
    @ConditionalOnMissingBean
    public SaplJacksonModule saplJacksonModule() {
        return new SaplJacksonModule();
    }
}
