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

import java.nio.file.Path;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

/**
 * Shared CLI options for commands that evaluate authorization subscriptions.
 * Used as a picocli {@code @Mixin} on each command.
 */
class PdpOptions {

    @ArgGroup(exclusive = false, heading = "%nRemote Connection:%n")
    RemoteConnectionOptions remoteConnection;

    @ArgGroup(exclusive = true, heading = "%nPolicy Source:%n")
    PolicySourceOptions policySource;

    @ArgGroup(exclusive = true, heading = "%nBundle Verification:%n")
    BundleVerificationOptions bundleVerification;

    @ArgGroup(exclusive = true, multiplicity = "0..1", heading = "%nSubscription Input:%n")
    SubscriptionInputOptions subscriptionInput;

    @Option(names = "--trace", description = "Print the full policy evaluation trace to stderr")
    boolean trace;

    @Option(names = "--json-report", description = "Print a machine-readable JSON evaluation report to stderr")
    boolean jsonReport;

    @Option(names = "--text-report", description = "Print a human-readable text evaluation report to stderr")
    boolean textReport;

    Path saplHomeOverride;

    void setSaplHomeOverride(Path path) {
        this.saplHomeOverride = path;
    }

}
