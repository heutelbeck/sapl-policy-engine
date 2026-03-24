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
package io.sapl.node.cli.options;

import picocli.CommandLine.Option;

public class NamedSubscriptionOptions {

    @Option(names = { "-s",
            "--subject" }, required = true, description = "Subject as a JSON value (string, number, object, or array)")
    public String subject;

    @Option(names = { "-a",
            "--action" }, required = true, description = "Action as a JSON value (string, number, object, or array)")
    public String action;

    @Option(names = { "-r",
            "--resource" }, required = true, description = "Resource as a JSON value (string, number, object, or array)")
    public String resource;

    @Option(names = { "-e",
            "--environment" }, description = "Environment as a JSON value (optional context for policy evaluation)")
    public String environment;

    @Option(names = "--secrets", description = "Secrets as a JSON object (available to policies via the secrets() function)")
    public String secrets;

}
