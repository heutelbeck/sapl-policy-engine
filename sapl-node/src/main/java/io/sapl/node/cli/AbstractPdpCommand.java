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

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import io.sapl.node.SaplNodeApplication;
import io.sapl.node.cli.PolicySourceResolver.ResolvedPolicy;
import lombok.val;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Shared infrastructure for CLI commands that evaluate authorization
 * subscriptions against a local PDP.
 */
abstract class AbstractPdpCommand implements Callable<Integer> {

    static final String ERROR_EVALUATION_FAILED = "Error: Evaluation failed: %s.";

    @Spec
    CommandSpec spec;

    @ArgGroup(exclusive = true)
    PolicySourceOptions policySource;

    @ArgGroup(exclusive = true)
    BundleVerificationOptions bundleVerification;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    SubscriptionInputOptions subscriptionInput;

    @Option(names = "--trace", description = "Print full evaluation trace to stderr")
    boolean trace;

    @Option(names = "--json-report", description = "Print JSON evaluation report to stderr")
    boolean jsonReport;

    @Option(names = "--text-report", description = "Print text evaluation report to stderr")
    boolean textReport;

    private Path saplHomeOverride;

    void setSaplHomeOverride(Path path) {
        this.saplHomeOverride = path;
    }

    ResolvedPolicy resolvePolicyConfiguration(PrintWriter err) {
        return PolicySourceResolver.resolve(policySource, bundleVerification, saplHomeOverride, err);
    }

    String[] buildSpringArgs(ResolvedPolicy resolved) {
        return SpringArgsBuilder.build(resolved, trace, jsonReport, textReport);
    }

    static ConfigurableApplicationContext bootHeadlessContext(String[] springArgs) {
        val app = new SpringApplication(SaplNodeApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setAdditionalProfiles("cli");
        return app.run(springArgs);
    }

}
