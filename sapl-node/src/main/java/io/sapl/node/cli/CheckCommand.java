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

import static io.sapl.node.cli.PdpSetup.ERROR_REMOTE_CONNECTION;

import java.util.concurrent.Callable;

import javax.net.ssl.SSLException;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

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
class CheckCommand implements Callable<Integer> {

    static final String ERROR_EVALUATION_FAILED = "Error: Evaluation failed: %s.";

    @Spec
    CommandSpec spec;

    @Mixin
    PdpOptions pdpOptions;

    @Override
    public Integer call() {
        val      err   = spec.commandLine().getErr();
        PdpSetup setup = null;
        try {
            setup = PdpSetup.open(pdpOptions, err);
            if (setup == null)
                return 1;
            val sub      = SubscriptionResolver.resolve(pdpOptions.subscriptionInput, setup.mapper());
            val decision = setup.pdp().decideOnceBlocking(sub);
            return toExitCode(decision);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        } catch (SSLException e) {
            err.println(ERROR_REMOTE_CONNECTION.formatted(e.getMessage()));
            return 1;
        } catch (RuntimeException e) {
            err.println(ERROR_EVALUATION_FAILED.formatted(e.getMessage()));
            return 1;
        } finally {
            if (setup != null)
                setup.shutdown();
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
