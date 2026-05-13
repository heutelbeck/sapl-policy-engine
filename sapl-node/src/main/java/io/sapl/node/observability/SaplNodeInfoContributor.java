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
package io.sapl.node.observability;

import java.util.LinkedHashMap;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * Contributes SAPL PDP configuration details to the {@code /actuator/info}
 * endpoint.
 */
@Component
@RequiredArgsConstructor
class SaplNodeInfoContributor implements InfoContributor {

    private final EmbeddedPDPProperties pdpProperties;

    @Override
    public void contribute(Info.Builder builder) {
        // Use LinkedHashMap rather than Map.of: getConfigPath() / getPoliciesPath()
        // are null in REMOTE_BUNDLES mode, and Map.of rejects null values with NPE,
        // which would crash the actuator info endpoint.
        val details = new LinkedHashMap<String, Object>();
        details.put("configType", pdpProperties.getPdpConfigType().name());
        details.put("configPath", pdpProperties.getConfigPath());
        details.put("policiesPath", pdpProperties.getPoliciesPath());
        builder.withDetail("sapl", details);
    }

}
