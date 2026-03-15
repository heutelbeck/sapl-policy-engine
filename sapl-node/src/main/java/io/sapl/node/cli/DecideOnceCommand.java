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
// @formatter:off
@Command(
    name = "decide-once",
    mixinStandardHelpOptions = true,
    description = { """
        Evaluate a single authorization decision and print the result as JSON.

        Evaluates the authorization subscription against policies once and prints
        the full authorization decision to stdout. The output is a JSON object
        containing the decision (PERMIT, DENY, NOT_APPLICABLE, INDETERMINATE),
        any obligations, advice, and resource transformations.

        The subscription can be provided via named flags (-s, -a, -r) or a JSON
        file (-f). Named flag values must be valid JSON (strings must be
        quoted, e.g., '"alice"'). Use -f - to read from stdin.

        By default, policies are loaded from the current directory. Use --dir for
        a specific directory, --bundle for a bundle file, or --remote to
        query a running PDP server.
        """ },
    exitCodeListHeading = "%nExit Codes:%n",
    exitCodeList = {
        " 0:Decision printed successfully",
        " 1:Error during evaluation"
    },
    footerHeading = "%nExamples:%n",
    footer = { """
          sapl decide-once --dir ./policies -s '"alice"' -a '"read"' -r '"doc"'

          sapl decide-once -f request.json --bundle policies.saplbundle

          echo '{"subject":"alice","action":"read","resource":"doc"}' | sapl decide-once -f -

          sapl decide-once --remote --token $SAPL_TOKEN -s '{"role":"admin"}' -a '"write"' -r '"config"'
        """ }
)
// @formatter:on
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
