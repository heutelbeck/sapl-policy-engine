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

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import io.sapl.node.cli.options.RemoteConnectionOptions;
import io.sapl.node.cli.support.PdpSetup;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import reactor.netty.http.HttpProtocol;
import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base attribute command with common functions that all sub commmands are using
 * and only needs to be defined once.
 */
public abstract class BaseAttributeCommand implements Callable<Integer> {
    private static final String DEFAULT_URL = "http://localhost:8080";
    Function<String, String>    environment = System::getenv;

    @Spec
    CommandSpec spec;

    @Option(names = "--url", description = "API endpoint of a running SAPL attribute API (e.g. http://localhost:8080)")
    String url;

    @ArgGroup(exclusive = true)
    RemoteConnectionOptions.AuthOptions auth;

    final WebClient webClient = WebClient.builder().clientConnector(
            new ReactorClientHttpConnector(reactor.netty.http.client.HttpClient.create().protocol(HttpProtocol.HTTP11)))
            .build();

    /**
     * Converts the {@code --value} text into a JSON value. Booleans, null and
     * numbers are detected. Everything else will be a string.
     *
     * @param rawValue the value as given on the command line.
     * @return a {@link Boolean}, {@link Long}, {@link Double}, {@link String} or
     * {@code null}.
     */
    Serializable parseLiteral(String rawValue) {
        if ("true".equalsIgnoreCase(rawValue))
            return Boolean.TRUE;
        if ("false".equalsIgnoreCase(rawValue))
            return Boolean.FALSE;
        if ("null".equalsIgnoreCase(rawValue))
            return null;

        try {
            return Long.valueOf(rawValue);
        } catch (NumberFormatException ignored) {
            // try next, if its not a long
        }

        try {
            return Double.valueOf(rawValue);
        } catch (NumberFormatException ignored) {
            // try next (string), if its not a double
        }
        return rawValue;
    }

    /**
     * Builds the URL path of an attribute. The path is
     * {@code /api/attributes/<entity>/<name>} or {@code /api/attributes/<name>}
     * without an entity.
     *
     * @param entity the optional entity.
     * @param name the attribute name.
     * @return the URL path of the attribute.
     */
    String attributePath(String entity, String name) {
        return (entity != null && !entity.isBlank()) ? "/api/attributes/" + entity + "/" + name
                : "/api/attributes/" + name;
    }

    /**
     * Resolves the given URL and checks if it's empty
     *
     * @return The resolved URL
     */
    String resolvedURL() {
        if (url != null) {
            return url;
        }
        var env = environment.apply("SAPL_URL");
        return env != null ? env : DEFAULT_URL;
    }

    /**
     * Sets the right HTTP header for the chosen authentication. >HTTP Basic for the
     * --basic-auth option and Bearer for the --token option (JWT and OAuth2/JWT).
     *
     * @return A function that sets the Authorization header on a header for an
     * outgoing request. No-op if no credentials were configured.
     */
    Consumer<HttpHeaders> authHeaders() {
        if (auth != null && auth.basicAuth != null) {
            var parsed = PdpSetup.parseBasicAuth(auth.basicAuth);
            return headers -> {
                headers.setBasicAuth(parsed.username(), parsed.password());
                headers.set("X-SAPL-Client", "cli");
            };
        }

        if (auth != null && auth.token != null) {
            return headers -> headers.setBearerAuth(auth.token);
        }

        var envBasicAuth = environment.apply("SAPL_BASIC_AUTH");
        if (envBasicAuth != null) {
            var parsed = PdpSetup.parseBasicAuth(envBasicAuth);
            return headers -> headers.setBasicAuth(parsed.username(), parsed.password());
        }

        var envToken = environment.apply("SAPL_BEARER_TOKEN");
        if (envToken != null) {
            return headers -> headers.setBearerAuth(envToken);
        }

        return headers -> {};
    }
}
