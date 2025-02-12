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

import java.util.List;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import io.sapl.api.pdp.AuthorizationSubscriptionInterceptor;
import io.sapl.api.pdp.TracedDecisionInterceptor;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.pdp.config.fixed.FixedFunctionsAndAttributesPDPConfigurationProvider;
import io.sapl.prp.PolicyRetrievalPointSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
public class PDPConfigurationProviderAutoConfiguration {

    private final AttributeContext                           attributeCtx;
    private final FunctionContext                            functionCtx;
    private final VariablesAndCombinatorSource               combinatorProvider;
    private final List<AuthorizationSubscriptionInterceptor> subscriptionInterceptors;
    private final List<TracedDecisionInterceptor>            decisionInterceptors;
    private final PolicyRetrievalPointSource                 policyRetrievalPointSource;

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PDPConfigurationProvider pdpConfigurationProvider() {
        log.debug(
                "Deploying PDP configuration provider with AttributeContext: {} FunctionContext: {} VariablesAndCombinatorSource: {} #SubscriptionIntercptors: {} #DecisionInterceptors: {}",
                attributeCtx.getClass().getSimpleName(), functionCtx.getClass().getSimpleName(), combinatorProvider,
                subscriptionInterceptors.size(), decisionInterceptors.size());
        return new FixedFunctionsAndAttributesPDPConfigurationProvider(attributeCtx, functionCtx, combinatorProvider,
                subscriptionInterceptors, decisionInterceptors, policyRetrievalPointSource);
    }

}
