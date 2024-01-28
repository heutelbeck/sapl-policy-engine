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
package io.sapl.grammar.ide.contentassist;

import java.util.Map;
import java.util.function.UnaryOperator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.combinators.CombiningAlgorithmFactory;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import reactor.core.publisher.Flux;

@ComponentScan
@Configuration
class SAPLIdeSpringTestConfiguration {

    @Bean
    PDPConfigurationProvider pdpConfiguration() throws InitializationException {
        var attributeContext              = new TestAttributeContext();
        var functionContext               = new TestFunctionContext();
        var staticPlaygroundConfiguration = new PDPConfiguration(attributeContext, functionContext, Map.of(),
                CombiningAlgorithmFactory.getCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES),
                UnaryOperator.identity(), UnaryOperator.identity());
        return new PDPConfigurationProvider() {
            @Override
            public Flux<PDPConfiguration> pdpConfiguration() {
                return Flux.just(staticPlaygroundConfiguration);
            }
        };
    }

}
