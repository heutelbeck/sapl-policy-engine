/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.pdp.embedded;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.prp.resources.ResourcesPrpUpdateEventSource;
import io.sapl.reimpl.prp.GenericInMemoryIndexedPolicyRetrievalPoint;
import io.sapl.reimpl.prp.filesystem.FileSystemPrpUpdateEventSource;
import io.sapl.reimpl.prp.index.canonical.CanonicalImmutableParsedDocumentIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URISyntaxException;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ComponentScan("io.sapl.spring")
@EnableConfigurationProperties(EmbeddedPDPProperties.class)
public class PRPAutoConfiguration {

    private final SAPLInterpreter interpreter;
    private final EmbeddedPDPProperties pdpProperties;

    @Bean
    @ConditionalOnMissingBean
    public PolicyRetrievalPoint policyRetrievalPoint()
            throws IOException, URISyntaxException, PolicyEvaluationException {
        var policiesFolder = pdpProperties.getPoliciesPath();
        //		var seedIndex = new NaiveImmutableParsedDocumentIndex();
        var seedIndex = new CanonicalImmutableParsedDocumentIndex();
        if (pdpProperties.getPdpConfigType() == EmbeddedPDPProperties.PDPDataSource.FILESYSTEM) {
            log.info("creating embedded PDP sourcing and monitoring access policies from the filesystem: {}",
                    policiesFolder);
            var source = new FileSystemPrpUpdateEventSource(policiesFolder, interpreter);
            return new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);
        } else {
            log.info("creating embedded PDP sourcing access policies from fixed bundled resources at: {}",
                    policiesFolder);
            var source = new ResourcesPrpUpdateEventSource(policiesFolder, interpreter);
            return new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);
        }
    }
}
