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
package io.sapl.playground;

import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalPointSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

@Slf4j
public class PlaygroundPolicyRetrievalPointSource implements PolicyRetrievalPointSource {

    private final SAPLInterpreter parser;

    private final Sinks.Many<PolicyRetrievalPoint> prpSink = Sinks.many().replay().latest();
    private final Flux<PolicyRetrievalPoint>       prpFlux = prpSink.asFlux();

    public PlaygroundPolicyRetrievalPointSource(SAPLInterpreter parser) {
        this.parser = parser;
        updatePrp(List.of());
    }

    public void updatePrp(List<String> documents) {
        val prp = new PlaygroundPolicyRetrievalPoint(documents, parser);
        prpSink.tryEmitNext(prp);
    }

    @Override
    public Flux<PolicyRetrievalPoint> policyRetrievalPoint() {
        return prpFlux;
    }

    @Override
    @PreDestroy
    public void dispose() {
        prpSink.tryEmitComplete();
    }
}
