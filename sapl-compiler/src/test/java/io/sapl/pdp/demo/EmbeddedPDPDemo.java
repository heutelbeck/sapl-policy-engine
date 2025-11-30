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
package io.sapl.pdp.demo;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

/**
 * Demonstration application for the embedded PDP with directory-based policy
 * loading.
 * <p>
 * This demo sets up a PDP that monitors a policy directory for policy files and
 * subscribes to authorization decisions.
 * When policies change, new decisions are automatically emitted.
 * <p>
 * Usage:
 * <ol>
 * <li>Run with optional path argument: {@code java EmbeddedPDPDemo [path]}</li>
 * <li>If no path provided, defaults to {@code ./policies}</li>
 * <li>Modify .sapl files in the directory to see decisions update</li>
 * <li>Press Ctrl+C to stop</li>
 * </ol>
 */
@Slf4j
public class EmbeddedPDPDemo {

    private static final String DEFAULT_POLICY_DIRECTORY = "./policies";

    public static void main(String[] args) throws Exception {
        var policyDirectory = Path.of(args.length > 0 ? args[0] : DEFAULT_POLICY_DIRECTORY);

        log.info("=".repeat(70));
        log.info("SAPL Embedded PDP Demo");
        log.info("=".repeat(70));
        log.info("Policy directory: " + policyDirectory.toAbsolutePath());
        log.info("Modify .sapl files in this directory to see decisions change.");
        log.info("Press Ctrl+C to stop.");
        log.info("=".repeat(70));

        var components = PolicyDecisionPointBuilder.withDefaults().withDirectorySource(policyDirectory).build();

        var pdp = components.pdp();

        log.info("PDP initialized. Creating authorization subscription...");

        var subscription = new AuthorizationSubscription(Value.of("alice"), Value.of("read"),
                Value.of("secret-document"), Value.UNDEFINED);

        log.info("Subscription: subject='alice', action='read', resource='secret-document'");
        log.info("-".repeat(70));
        log.info("Waiting for authorization decisions...");
        log.info("");

        pdp.decide(subscription).doOnNext(decision -> {
            log.info("DECISION: " + decision.decision());
            if (!decision.obligations().isEmpty()) {
                log.info("  Obligations: " + decision.obligations());
            }
            if (!decision.advice().isEmpty()) {
                log.info("  Advice: " + decision.advice());
            }
            if (decision.resource() != null && !Value.UNDEFINED.equals(decision.resource())) {
                log.info("  Resource: " + decision.resource());
            }
            log.info("");
        }).doOnError(error -> log.info("ERROR: " + error.getMessage()))
                .doOnComplete(() -> log.info("Stream completed (unexpected).")).blockLast();

        var source = components.source();
        if (source != null) {
            source.dispose();
        }
    }
}
