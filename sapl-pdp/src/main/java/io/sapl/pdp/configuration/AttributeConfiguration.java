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
package io.sapl.pdp.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.configuration.source.PDPConfigurationSource;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.util.Arrays;

@Slf4j
@Configuration
public class AttributeConfiguration {
    @Bean
    @Primary
    AttributeRepository attributeRepository(PDPConfigurationSource source) {
        return new RoutingAttributeRepository(source);
    }

    // the broker bean to load all PIP's annotated with PolicyInformationPoint
    @Bean
    AttributeBroker attributeBroker(AttributeRepository repository, ApplicationContext ctx) {
        val pipBeans = Arrays.stream(ctx.getBeanNamesForAnnotation(PolicyInformationPoint.class)).map(ctx::getBean)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        return PolicyDecisionPointBuilder.buildPolicyInformationPointAttributeBroker(Clock.systemUTC(),
                JsonMapper.builder().build(), true, pipBeans, repository);
    }

    @Bean
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

}
