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
package io.sapl.node.cli;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.RequiredArgsConstructor;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evaluates a single authorization subscription and prints the decision as JSON
 * to stdout.
 * <p>
 * This class is intentionally NOT a Spring {@code @Component}. The AOT
 * processor evaluates {@code @Profile} at build time and bakes the result into
 * the native image. Since the native image is built without the {@code cli}
 * profile, a {@code @Profile("cli")} bean would be permanently excluded.
 * Instead, {@link io.sapl.node.SaplNodeApplication#runSpringCli} instantiates
 * this class manually after the context starts.
 */
@RequiredArgsConstructor
public class DecideOnceRunner {

    private static final String ERROR_MISSING_FLAG_VALUE = "Flag '%s' requires a value.";

    private final PolicyDecisionPoint pdp;
    private final JsonMapper          mapper;

    /**
     * Parses the command-line arguments, evaluates the subscription, and prints
     * the decision.
     *
     * @param args the raw command-line arguments
     */
    public void run(String[] args) {
        val subscription = parseSubscription(args);
        val decision     = pdp.decideOnceBlocking(subscription);
        System.out.println(mapper.writeValueAsString(decision));
    }

    private AuthorizationSubscription parseSubscription(String[] args) {
        String subject     = null;
        String action      = null;
        String resource    = null;
        String environment = null;
        for (var i = 0; i < args.length; i++) {
            switch (args[i]) {
            case "-s" -> subject = requireValue(args, ++i, "-s");
            case "-a" -> action = requireValue(args, ++i, "-a");
            case "-r" -> resource = requireValue(args, ++i, "-r");
            case "-e" -> environment = requireValue(args, ++i, "-e");
            default   -> { /* skip decide-once and unknown args */ }
            }
        }
        return AuthorizationSubscription.of(subject, action, resource, environment);
    }

    private String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(ERROR_MISSING_FLAG_VALUE.formatted(flag));
        }
        return args[index];
    }
}
