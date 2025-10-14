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
package io.sapl.spring.pdp.embedded;

import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PrpUpdateEventSource;
import io.sapl.prp.filesystem.FileSystemPrpUpdateEventSource;
import io.sapl.prp.resources.ResourcesPrpUpdateEventSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(EmbeddedPDPProperties.class)
public class PrpUpdateEventSourceAutoConfiguration {

    private final SAPLInterpreter interpreter;

    private final EmbeddedPDPProperties pdpProperties;

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PrpUpdateEventSource prpUpdateSource() {
        final var policiesFolder = pdpProperties.getPoliciesPath();
        if (pdpProperties.getPdpConfigType() == EmbeddedPDPProperties.PDPDataSource.FILESYSTEM) {
            log.info("creating embedded PDP sourcing and monitoring access policies from the filesystem: {}",
                    policiesFolder);
            return new FileSystemPrpUpdateEventSource(policiesFolder, interpreter);
        }
        log.info("creating embedded PDP sourcing access policies from fixed bundled resources at: {}", policiesFolder);
        return new ResourcesPrpUpdateEventSource(policiesFolder, interpreter);
    }

}
