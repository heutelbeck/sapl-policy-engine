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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import reactor.core.publisher.Flux;

@PolicyInformationPoint(name = "http", description = "Generic PIP for accessing and polling web resources.")
public class HttpPolicyInformationPoint {

    private static final String GET_DOCS    = "Sends an HTTP GET request to the uri provided in the value parameter and returns a flux of responses.";
    private static final String POST_DOCS   = "Sends an HTTP POST request to the url provided in the value parameter and returns a flux of responses.";
    private static final String PUT_DOCS    = "Sends an HTTP PUT request to the url provided in the value parameter and returns a flux of responses.";
    private static final String PATCH_DOCS  = "Sends an HTTP PATCH request to the url provided in the value parameter and returns a flux of responses.";
    private static final String DELETE_DOCS = "Sends an HTTP DELETE request to the url provided in the value parameter and returns a flux of responses.";
    private static final String SOCKET_DOCS = "Connects to a web socket";
    private ReactiveWebClient   webClient;

    public HttpPolicyInformationPoint(ObjectMapper mapper) {
        webClient = new ReactiveWebClient(mapper);
    }

    @EnvironmentAttribute(docs = GET_DOCS)
    public Flux<Val> get(Val requestSettings) {
        return webClient.httpRequest(HttpMethod.GET, requestSettings);
    }

    @EnvironmentAttribute(docs = POST_DOCS)
    public Flux<Val> post(Val requestSettings) {
        return webClient.httpRequest(HttpMethod.POST, requestSettings);
    }

    @EnvironmentAttribute(docs = PUT_DOCS)
    public Flux<Val> put(Val requestSettings) {
        return webClient.httpRequest(HttpMethod.PUT, requestSettings);
    }

    @EnvironmentAttribute(docs = PATCH_DOCS)
    public Flux<Val> patch(Val requestSettings) {
        return webClient.httpRequest(HttpMethod.PATCH, requestSettings);
    }

    @EnvironmentAttribute(docs = DELETE_DOCS)
    public Flux<Val> delete(Val requestSettings) {
        return webClient.httpRequest(HttpMethod.DELETE, requestSettings);
    }

    @EnvironmentAttribute(docs = SOCKET_DOCS)
    public Flux<Val> websocket(Val requestSettings) {
        return webClient.consumeWebSocket(requestSettings);
    }

}
