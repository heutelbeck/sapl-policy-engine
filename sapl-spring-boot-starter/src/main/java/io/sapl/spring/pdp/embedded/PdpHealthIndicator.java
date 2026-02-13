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
package io.sapl.spring.pdp.embedded;

import io.sapl.pdp.configuration.PdpState;
import io.sapl.pdp.configuration.PdpStatus;
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot health indicator that reports the operational status of all
 * configured PDP instances. Each PDP's state (LOADED, STALE, EMPTY) is
 * mapped to an overall health status:
 * <ul>
 * <li>All LOADED: UP</li>
 * <li>Any STALE: UP (with warning details)</li>
 * <li>Any ERROR or no PDPs: DOWN</li>
 * </ul>
 */
@RequiredArgsConstructor
class PdpHealthIndicator implements HealthIndicator {

    private final PdpVoterSource pdpVoterSource;

    @Override
    public Health health() {
        val statuses = pdpVoterSource.getAllPdpStatuses();

        if (statuses.isEmpty()) {
            return Health.down().withDetail("pdps", Map.of()).build();
        }

        val pdpDetails = new LinkedHashMap<String, Map<String, Object>>();
        var hasError   = false;
        var hasStale   = false;

        for (val entry : statuses.entrySet()) {
            pdpDetails.put(entry.getKey(), toDetailMap(entry.getValue()));
            switch (entry.getValue().state()) {
            case ERROR  -> hasError = true;
            case STALE  -> hasStale = true;
            case LOADED -> { /* no action */ }
            }
        }

        val builder = hasError ? Health.down() : Health.up();
        builder.withDetail("pdps", pdpDetails);

        if (hasStale && !hasError) {
            builder.withDetail("warning", "One or more PDPs are serving stale policies");
        }

        return builder.build();
    }

    private static Map<String, Object> toDetailMap(PdpStatus status) {
        val detail = new LinkedHashMap<String, Object>();
        detail.put("state", status.state().name());

        if (status.configurationId() != null) {
            detail.put("configurationId", status.configurationId());
        }
        if (status.combiningAlgorithm() != null) {
            detail.put("combiningAlgorithm", status.combiningAlgorithm());
        }
        detail.put("documentCount", status.documentCount());
        if (status.lastSuccessfulLoad() != null) {
            detail.put("lastSuccessfulLoad", status.lastSuccessfulLoad().toString());
        }
        if (status.lastFailedLoad() != null) {
            detail.put("lastFailedLoad", status.lastFailedLoad().toString());
        }
        if (status.lastError() != null) {
            detail.put("lastError", status.lastError());
        }

        return detail;
    }

}
