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
package io.sapl.grammar.ui.contentassist;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.SchemaValidationLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.grammar.ide.contentassist.SAPLContentProposalProvider;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.combinators.CombiningAlgorithmFactory;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.pip.TimePolicyInformationPoint;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.util.Map;
import java.util.function.UnaryOperator;

public class SAPLUiContentProposalProvider extends SAPLContentProposalProvider {

    @Override
    protected PDPConfigurationProvider getPDPConfigurationProvider() {
        try {
            var attributeContext = new AnnotationAttributeContext();
            attributeContext.loadPolicyInformationPoint(new TimePolicyInformationPoint(Clock.systemUTC()));

            var functionContext = new AnnotationFunctionContext();
            functionContext.loadLibrary(FilterFunctionLibrary.class);
            functionContext.loadLibrary(StandardFunctionLibrary.class);
            functionContext.loadLibrary(TemporalFunctionLibrary.class);
            functionContext.loadLibrary(SchemaValidationLibrary.class);

            var pdpConfiguration = new PDPConfiguration(attributeContext, functionContext, Map.of(),
                    CombiningAlgorithmFactory.getCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES),
                    UnaryOperator.identity(), UnaryOperator.identity());
            return () -> Flux.just(pdpConfiguration);
        } catch (InitializationException e) {
            throw new RuntimeException(e);
        }
    }

}
