/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.prp.GenericInMemoryIndexedPolicyRetrievalPointSource;
import io.sapl.prp.PolicyRetrievalPointSource;
import io.sapl.prp.PrpUpdateEventSource;
import io.sapl.prp.index.UpdateEventDrivenPolicyRetrievalPoint;
import io.sapl.prp.index.canonical.CanonicalImmutableParsedDocumentIndex;
import io.sapl.prp.index.naive.NaiveImmutableParsedDocumentIndex;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.IndexType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(EmbeddedPDPProperties.class)
public class PRPAutoConfiguration {

    private final EmbeddedPDPProperties pdpProperties;
    private final PrpUpdateEventSource  eventSource;
    private final FunctionContext       functionContext;
    private final AttributeContext      attributeContext;

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PolicyRetrievalPointSource policyRetrievalPoint() throws PolicyEvaluationException {
        log.info("Using index type: {}", pdpProperties.getIndex());
        UpdateEventDrivenPolicyRetrievalPoint seedIndex;
        if (pdpProperties.getIndex() == IndexType.NAIVE) {
            seedIndex = new NaiveImmutableParsedDocumentIndex();
        } else {
            // This index type has to normalize function calls based on import statements
            // Variables do not need to be bound here. Thus, this hind of static PDP
            // scoped
            // evaluation context is sufficient. Variables will be bound later in the
            // subscription scoped EvaluationContext handed over for lookup.
            seedIndex = new CanonicalImmutableParsedDocumentIndex(attributeContext, functionContext);
        }
        return new GenericInMemoryIndexedPolicyRetrievalPointSource(seedIndex, eventSource);
    }

}
