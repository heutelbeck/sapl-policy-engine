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

import java.io.IOException;
import java.util.Objects;

import org.springframework.web.util.UriComponentsBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;

/**
 * CLI command to count the amount of attributes for a given pdp id.
 */
@CommandLine.Command(name = "count", mixinStandardHelpOptions = true, description = "Counts the amount of attributes for a given pdp id")
public class CountAttributeCommand extends BaseAttributeCommand {
    @Mixin
    FileMixin file;

    @Override
    public Integer call() throws Exception {
        var uriBuilder = UriComponentsBuilder.fromUriString(resolvedURL() + "/api/attributes").queryParam("count",
                "true");
        var response   = Objects.requireNonNull(webClient.get().uri(uriBuilder.build().toUri()).headers(authHeaders())
                .retrieve().toEntity(String.class).block());

        print(Objects.requireNonNullElse(response.getBody(), ""));
        return response.getStatusCode().is2xxSuccessful() ? 0 : 1;
    }

    private void print(String content) throws IOException {
        file.getFileWriter().println(content);
    }
}
