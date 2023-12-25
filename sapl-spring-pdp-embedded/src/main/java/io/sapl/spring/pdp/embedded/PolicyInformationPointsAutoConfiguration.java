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
package io.sapl.spring.pdp.embedded;

import java.time.Clock;
import java.util.List;

import io.sapl.pip.SchemaTestPIP;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import io.sapl.api.pip.PolicyInformationPointSupplier;
import io.sapl.pip.TimePolicyInformationPoint;

@AutoConfiguration
public class PolicyInformationPointsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PolicyInformationPointSupplier timePolicyInformationPointSupplier(Clock clock) {
        return () -> List.of(new TimePolicyInformationPoint(clock), new SchemaTestPIP());
    }

}
