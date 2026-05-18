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
package io.sapl.spring.pdp.embedded;

import io.sapl.api.model.jackson.SaplJacksonModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the core {@link SaplJacksonModule} so that any
 * {@code ObjectMapper} the application builds can serialize and
 * deserialize SAPL value/decision/subscription types. Active whenever
 * the embedded PDP module is on the classpath. Declared
 * {@link ConditionalOnMissingBean} so that an application that already
 * provides its own {@link SaplJacksonModule} bean (or that uses
 * {@code sapl-spring-boot-starter}, which provides the same module
 * plus PEP-side HTTP serializers) wins.
 */
@AutoConfiguration
public class PdpObjectMapperAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SaplJacksonModule saplJacksonModule() {
        return new SaplJacksonModule();
    }
}
