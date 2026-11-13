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

import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponentsBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CLI command to publish or update a new attribute in an attribute repository.
 */
@Command(name = "publish", mixinStandardHelpOptions = true, description = "Publish an attribute into the attribute repository of a running SAPL node")
public class PublishAttributeCommand extends BaseAttributeCommand {

    @Option(names = "--entity", description = "The subject or resource the attribute belongs to (omit for global attributes)")
    String entity;

    @Option(names = "--name", required = true, description = "The attribute name e.g. role or user.role")
    String name;

    @Option(names = "--value", required = true, description = "The value of the attribute (JSON literal or plain string)")
    String value;

    @Option(names = "--arguments", description = "Comma-separated list of arguments", defaultValue = "", split = ",")
    List<String> arguments;

    @Option(names = "--ttl", description = "Time to live in seconds. Omit or use -1 for permanent attributes.", defaultValue = "-1")
    Long ttl;

    @Override
    public Integer call() {
        var uriBuilder = UriComponentsBuilder.fromUriString(resolvedURL() + attributePath(entity, name));
        arguments.stream().filter(s -> !s.isEmpty()).forEach(arg -> uriBuilder.queryParam("arg", arg));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", parseLiteral(value));
        body.put("ttl", ttl >= 0 ? ttl : null);

        var response = Objects.requireNonNull(webClient.put().uri(uriBuilder.build().toUri()).headers(authHeaders())
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve().toEntity(String.class).block());

        return response.getStatusCode().is2xxSuccessful() ? 0 : 1;
    }
}
