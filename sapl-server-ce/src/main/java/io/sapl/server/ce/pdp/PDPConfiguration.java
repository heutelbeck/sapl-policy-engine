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
package io.sapl.server.ce.pdp;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.DynamicPolicyDecisionPoint;
import io.sapl.pdp.ThreadLocalRandomIdFactory;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
@Conditional(SetupFinishedCondition.class)
class PDPConfiguration {

    @Bean
    PolicyDecisionPoint policyDecisionPoint(CEConfigurationSource configurationSource) {
        return new DynamicPolicyDecisionPoint(configurationSource, new ThreadLocalRandomIdFactory(),
                context -> Mono.just(DynamicPolicyDecisionPoint.DEFAULT_PDP_ID));
    }

}
