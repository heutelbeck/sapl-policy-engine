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

import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Evaluates a single authorization subscription against policies and prints the
 * decision as JSON to stdout.
 */
@Command(name = "decide-once", description = "Evaluate a single authorization decision", mixinStandardHelpOptions = true)
class DecideOnceCommand implements Callable<Integer> {

    static final String ERROR_EVALUATION_FAILED = "Error: Evaluation failed: %s.";

    @Spec
    CommandSpec spec;

    @Mixin
    PdpOptions pdpOptions;

    @Override
    public Integer call() {
        val      err   = spec.commandLine().getErr();
        val      out   = spec.commandLine().getOut();
        PdpSetup setup = null;
        try {
            setup = PdpSetup.open(pdpOptions, err);
            if (setup == null)
                return 1;
            val subscription = SubscriptionResolver.resolve(pdpOptions.subscriptionInput, setup.mapper());
            val decision     = setup.pdp().decideOnceBlocking(subscription);
            out.println(setup.mapper().writeValueAsString(decision));
            return 0;
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

}
