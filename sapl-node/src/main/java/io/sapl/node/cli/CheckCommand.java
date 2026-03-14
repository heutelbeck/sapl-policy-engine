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

import org.springframework.boot.SpringApplication;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.val;
import picocli.CommandLine.Command;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evaluates a single authorization subscription and encodes the decision as a
 * process exit code. No output is written to stdout.
 * <p>
 * Exit codes:
 * <ul>
 * <li>0 - simple PERMIT (no obligations, no resource transformation)</li>
 * <li>1 - error during evaluation</li>
 * <li>2 - DENY</li>
 * <li>3 - NOT_APPLICABLE</li>
 * <li>4 - INDETERMINATE, or PERMIT with obligations/resource
 * transformation</li>
 * </ul>
 */
@Command(name = "check", description = "Check authorization and encode result as exit code", mixinStandardHelpOptions = true)
class CheckCommand extends AbstractPdpCommand {

    @Override
    public Integer call() {
        val err = spec.commandLine().getErr();

        val resolved = resolvePolicyConfiguration(err);
        if (resolved == null) {
            return 1;
        }

        val springArgs = buildSpringArgs(resolved);

        try (val context = bootHeadlessContext(springArgs)) {
            val pdp      = context.getBean(PolicyDecisionPoint.class);
            val mapper   = context.getBean(JsonMapper.class);
            val sub      = SubscriptionResolver.resolve(subscriptionInput, mapper);
            val decision = pdp.decideOnceBlocking(sub);
            val exitCode = toExitCode(decision);
            SpringApplication.exit(context);
            return exitCode;
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println(ERROR_EVALUATION_FAILED.formatted(e.getMessage()));
            return 1;
        }
    }

    static int toExitCode(AuthorizationDecision decision) {
        return switch (decision.decision()) {
        case PERMIT         -> isSimplePermit(decision) ? 0 : 4;
        case DENY           -> 2;
        case NOT_APPLICABLE -> 3;
        case INDETERMINATE  -> 4;
        };
    }

    private static boolean isSimplePermit(AuthorizationDecision decision) {
        return decision.obligations().isEmpty() && Value.UNDEFINED.equals(decision.resource());
    }

}
