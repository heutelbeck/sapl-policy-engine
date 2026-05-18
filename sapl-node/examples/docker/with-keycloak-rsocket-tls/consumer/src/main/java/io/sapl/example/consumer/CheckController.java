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
package io.sapl.example.consumer;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * Minimal demonstration of a Spring Boot service that talks to a SAPL Node
 * over RSocket+TLS with an OAuth2 client_credentials-managed JWT. All auth
 * material is sourced from properties; this controller never sees the token.
 */
@RestController
@RequiredArgsConstructor
public class CheckController {

    private final StreamingPolicyDecisionPoint pdp;

    @GetMapping("/check")
    public String check(@RequestParam(defaultValue = "alice") String subject,
            @RequestParam(defaultValue = "read") String action,
            @RequestParam(defaultValue = "doc") String resource) {
        val subscription = AuthorizationSubscription.of(subject, action, resource);
        val decision     = pdp.decideOnce(subscription);
        return decision == null ? AuthorizationDecision.INDETERMINATE.getDecision().toString()
                : decision.getDecision().toString();
    }
}
