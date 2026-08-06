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
package io.sapl.pdp;

import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.pip.PipLoadException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class PdpConfiguration {

    @Bean
    @ConditionalOnMissingBean(StreamingPolicyDecisionPoint.class)
    public PDPComponents pdpComponents(AttributeBroker attributeBroker) throws PipLoadException {
        return PolicyDecisionPointBuilder.withDefaults()
                .withDirectorySource(Path.of(System.getProperty("user.home"), ".sapl", "policies"))
                .withAttributeBroker(attributeBroker).build();
    }

    @Bean
    @ConditionalOnMissingBean(StreamingPolicyDecisionPoint.class)
    public BlockingPolicyDecisionPoint pdp(PDPComponents components) {
        return components.pdp();
    }
}
