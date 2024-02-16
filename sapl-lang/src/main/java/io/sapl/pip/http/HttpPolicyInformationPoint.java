/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pip.http;

import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@PolicyInformationPoint(name = "http", description = "Generic PIP for accessing and polling web resources.")
public class HttpPolicyInformationPoint {

    private static final String ENV_GET_DOCS    = "Sends an HTTP GET request to the URL provided in the baseUrl of the requestSettings object and returns a flux of responses.";
    private static final String ENV_POST_DOCS   = "Sends an HTTP POST request to the URL provided in the baseUrl of the requestSettings object and returns a flux of responses.";
    private static final String ENV_PUT_DOCS    = "Sends an HTTP PUT request to the URL provided in the baseUrl of the requestSettings object and returns a flux of responses.";
    private static final String ENV_PATCH_DOCS  = "Sends an HTTP PATCH request to the URL provided in the baseUrl of the requestSettings object and returns a flux of responses.";
    private static final String ENV_DELETE_DOCS = "Sends an HTTP DELETE request to the URL provided in the baseUrl of the requestSettings object and returns a flux of responses.";
    private static final String ENV_SOCKET_DOCS = "Connects to a web socket using the URL provided in the baseUrl of the requestSettings object and returns a flux of responses.";
    private static final String GET_DOCS        = "Sends an HTTP GET request to the URL provided in the baseUrl as a left hand argument to the attribute and returns a flux of responses.";
    private static final String POST_DOCS       = "Sends an HTTP POST request to the URL provided in the baseUrl as a left hand argument to the attribute and returns a flux of responses.";
    private static final String PUT_DOCS        = "Sends an HTTP PUT request to the URL provided in the baseUrl as a left hand argument to the attribute and returns a flux of responses.";
    private static final String PATCH_DOCS      = "Sends an HTTP PATCH request to the URL provided in the baseUrl as a left hand argument to the attribute and returns a flux of responses.";
    private static final String DELETE_DOCS     = "Sends an HTTP DELETE request to the URL provided in the baseUrl as a left hand argument to the attribute and returns a flux of responses.";
    private static final String SOCKET_DOCS     = "Connects to a web socket using the URL provided in the baseUrl as a left hand argument to the attribute and returns a flux of responses.";

    private final ReactiveWebClient webClient;

    @EnvironmentAttribute(docs = ENV_GET_DOCS)
    public Flux<Val> get(@JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.GET, requestSettings);
    }

    @EnvironmentAttribute(docs = ENV_POST_DOCS)
    public Flux<Val> post(@JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.POST, requestSettings);
    }

    @EnvironmentAttribute(docs = ENV_PUT_DOCS)
    public Flux<Val> put(@JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.PUT, requestSettings);
    }

    @EnvironmentAttribute(docs = ENV_PATCH_DOCS)
    public Flux<Val> patch(@JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.PATCH, requestSettings);
    }

    @EnvironmentAttribute(docs = ENV_DELETE_DOCS)
    public Flux<Val> delete(@JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.DELETE, requestSettings);
    }

    @EnvironmentAttribute(docs = ENV_SOCKET_DOCS)
    public Flux<Val> websocket(@JsonObject Val requestSettings) {
        return webClient.consumeWebSocket(requestSettings);
    }

    @Attribute(docs = GET_DOCS)
    public Flux<Val> get(@Text Val url, @JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.GET, withBaseUrl(url, requestSettings));
    }

    @Attribute(docs = POST_DOCS)
    public Flux<Val> post(@Text Val url, @JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.POST, withBaseUrl(url, requestSettings));
    }

    @Attribute(docs = PUT_DOCS)
    public Flux<Val> put(@Text Val url, @JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.PUT, withBaseUrl(url, requestSettings));
    }

    @Attribute(docs = PATCH_DOCS)
    public Flux<Val> patch(@Text Val url, @JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.PATCH, withBaseUrl(url, requestSettings));
    }

    @Attribute(docs = DELETE_DOCS)
    public Flux<Val> delete(@Text Val url, @JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.DELETE, withBaseUrl(url, requestSettings));
    }

    @Attribute(docs = SOCKET_DOCS)
    public Flux<Val> websocket(@Text Val url, @JsonObject Val requestSettings) {
        return webClient.consumeWebSocket(withBaseUrl(url, requestSettings));
    }

    private Val withBaseUrl(Val baseUrl, Val requestSettings) {
        // unchecked cast is OK, @JsonObject => requestSettings is non-null ObjectNode
        var newSettings = (ObjectNode) requestSettings.get().deepCopy();
        newSettings.set(ReactiveWebClient.BASE_URL, baseUrl.get());
        return Val.of(newSettings);
    }

}
