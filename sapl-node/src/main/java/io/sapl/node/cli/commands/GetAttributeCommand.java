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

import java.util.Objects;

import org.springframework.web.util.UriComponentsBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;
import java.io.IOException;
import java.util.List;

/**
 * CLI command to get one specific attribute from an attribute repository for the given pdp id.
 */
@CommandLine.Command(name = "get", mixinStandardHelpOptions = true, description = "Gets an attribute from the attribute repository")
public class GetAttributeCommand extends BaseAttributeCommand {

    @Mixin
    FileMixin file;

    @CommandLine.Option(names = "--entity", description = "The subject or resource the attribute belongs to (omit for global attributes)")
    String entity;

    @CommandLine.Option(names = "--name", required = true, description = "The attribute name e.g. role or user.role")
    String name;

    @CommandLine.Option(names = "--arguments", description = "Comma-separated list of arguments", defaultValue = "", split = ",")
    List<String> arguments;

    @Override
    public Integer call() throws Exception {
        var uriBuilder = UriComponentsBuilder.fromUriString(resolvedURL() + attributePath(entity, name));
        arguments.stream().filter(s -> !s.isEmpty()).forEach(arg -> uriBuilder.queryParam("arg", arg));

        var response = Objects.requireNonNull(webClient.get().uri(uriBuilder.build().toUri()).headers(authHeaders())
                .retrieve().toEntity(String.class).block());

        print(Objects.requireNonNullElse(response.getBody(), ""));
        return response.getStatusCode().is2xxSuccessful() ? 0 : 1;
    }

    private void print(String content) throws IOException {
        file.getFileWriter().println(content);
    }
}
