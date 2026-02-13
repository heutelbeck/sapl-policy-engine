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
package io.sapl.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.info.Info;

import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.IndexType;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.PDPDataSource;
import lombok.val;

@DisplayName("SaplNodeInfoContributor")
@ExtendWith(MockitoExtension.class)
class SaplNodeInfoContributorTests {

    @Mock
    EmbeddedPDPProperties pdpProperties;

    @Test
    @DisplayName("contributes PDP configuration details to info endpoint")
    @SuppressWarnings("unchecked")
    void whenContribute_thenAddsSaplDetails() {
        when(pdpProperties.getPdpConfigType()).thenReturn(PDPDataSource.DIRECTORY);
        when(pdpProperties.getIndex()).thenReturn(IndexType.NAIVE);
        when(pdpProperties.getConfigPath()).thenReturn("/pdp/data");
        when(pdpProperties.getPoliciesPath()).thenReturn("/pdp/data");

        val contributor = new SaplNodeInfoContributor(pdpProperties);
        val builder     = new Info.Builder();

        contributor.contribute(builder);

        val info = builder.build();
        val sapl = (Map<String, Object>) info.getDetails().get("sapl");

        assertThat(sapl).containsEntry("configType", "DIRECTORY").containsEntry("index", "NAIVE")
                .containsEntry("configPath", "/pdp/data").containsEntry("policiesPath", "/pdp/data");
    }

}
