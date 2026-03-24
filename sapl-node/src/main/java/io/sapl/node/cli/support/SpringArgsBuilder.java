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
package io.sapl.node.cli.support;

import java.util.ArrayList;

import io.sapl.node.cli.support.PolicySourceResolver.ResolvedPolicy;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class SpringArgsBuilder {

    public static String[] build(ResolvedPolicy resolved, boolean trace, boolean jsonReport, boolean textReport) {
        val args = new ArrayList<String>();
        args.add("--io.sapl.pdp.embedded.pdp-config-type=" + resolved.configType());
        args.add("--io.sapl.pdp.embedded.config-path=" + resolved.path());
        args.add("--io.sapl.pdp.embedded.policies-path=" + resolved.path());

        if (resolved.publicKeyPath() != null) {
            args.add("--io.sapl.pdp.embedded.bundle-security.public-key-path=" + resolved.publicKeyPath());
        }
        if (resolved.allowUnsigned()) {
            args.add("--io.sapl.pdp.embedded.bundle-security.allow-unsigned=true");
        }
        if (trace) {
            args.add("--io.sapl.pdp.embedded.print-trace=true");
        }
        if (jsonReport) {
            args.add("--io.sapl.pdp.embedded.print-json-report=true");
        }
        if (textReport) {
            args.add("--io.sapl.pdp.embedded.print-text-report=true");
        }
        if (trace || jsonReport || textReport) {
            args.add("--logging.level.[io.sapl.pdp.interceptors]=INFO");
        }
        return args.toArray(String[]::new);
    }

}
