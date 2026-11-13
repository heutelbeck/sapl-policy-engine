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
package io.sapl.node.cli.commands;

import org.springframework.web.util.UriComponentsBuilder;
import picocli.CommandLine;

import java.util.List;
import java.util.Objects;

/**
 * CLI command remove and attribute from the attribute repository for a given pdp id.
 */
@CommandLine.Command(name = "delete", mixinStandardHelpOptions = true, description = "Removes an attribute from the attribute repository of a running SAPL node")
public class DeleteAttributeCommand extends BaseAttributeCommand {

    @CommandLine.Option(names = "--entity", description = "The subject or resource the attribute belongs to (omit for global attributes)")
    String entity;

    @CommandLine.Option(names = "--name", required = true, description = "The attribute name e.g. role or user.role")
    String name;

    @CommandLine.Option(names = "--arguments", description = "Comma-separated list of arguments", defaultValue = "", split = ",")
    List<String> arguments;

    @Override
    public Integer call() {
        var uriBuilder = UriComponentsBuilder.fromUriString(resolvedURL() + attributePath(entity, name));
        arguments.stream().filter(s -> !s.isEmpty()).forEach(arg -> uriBuilder.queryParam("arg", arg));

        var response = Objects.requireNonNull(webClient.delete().uri(uriBuilder.build().toUri()).headers(authHeaders())
                .retrieve().toEntity(Void.class).block());

        return response.getStatusCode().is2xxSuccessful() ? 0 : 1;
    }
}
