/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributes.pips.http;

import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@PolicyInformationPoint(name = "http", description = HttpPolicyInformationPoint.DESCRIPTION)
public class HttpPolicyInformationPoint {

    public static final String DESCRIPTION = """
            This Policy Information Point provided basic means to source attribute data by consuming
            HTTP-based APIs and Websockets.

            The Attributes are named according to the HTTP verb, i.e., get, put, delete, post, and patch.
            And are available as either environment attributes or attributes of a an URL which semantically
            identifies a resource used as the left-hand input parameter of the attribute finders.

            This PIP is more technical than domain driven and therefore the attributes are specified by
            defining HTTP requests by defining a ```requestSetings``` object, which may contain the following
            parameters:
            * ```baseUrl```: The starting URL to build the request path.
            * ```path```: Path components to be appended to the baseUrl.
            * ```urlParameters```: An object with key-value pairs representing the HTTP query parameters to
            be embedded in the request URL.
            * ```headers```: An object with key-value pairs representing the HTTP headers.
            * ```body```: The request body.
            * ```accept```: The accepted mime media type.
            * ```contentType```: The mime type of the request body.
            * ```pollingIntervalMs```: The number of milliseconds between polling the HTTP endpoint. Defaults to 1000ms.
            * ```repetitions```: Upper bound for number of repeated requests. Defaults to 0x7fffffffffffffffL.

            For the media type ```text/event-stream```, the attribute finder will treat the consumed
            endpoint to be sending server-sent events (SSEs) and will not poll the endpoint, but subscribe
            to the events emitted by the consumed API.

            If the accepted media type is ```application/json```, the PIP will attempt to parse it and map
            the response body to a SAPL value. Else, the response body is returned as a text value.

            Example:
            ```
            {
              "baseUrl": "https://example.com",
              "path": "/api/owners",
              "urlParameters": {
                                  "age": 5,
                                  "sort": "ascending"
                               },
              "headers": {
                           "Authorization": "Bearer <token>",
                           "If-Modified-Since": "Tue, 19 Jul 2016 12:22:11 UTC"
                         },
              "body": "<tag>abc</tag>",
              "accept": "application/json",
              "contentType": "application/xml",
              "pollingIntervalMs": 4500,
              "repetitions": 999
            }
            ```
            """;

    private final ReactiveWebClient webClient;

    @EnvironmentAttribute(docs = """
            ```<get(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP GET request and
            polls it according the the settings.

            Example:
            ```
            policy "http example"
            permit
            where
              var request = {
                                "baseUrl": "https://example.com",
                                "path": "/status"
                            }
              <http.get(request)>.status == "OK";
            ```
            """)
    public Flux<Val> get(@JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.GET, requestSettings);
    }

    @EnvironmentAttribute(docs = """
            ```<post(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP POST request and
            polls it according the the settings.

            Example:
            ```
            policy "http example"
            permit
            where
              var request = {
                                "baseUrl": "https://example.com",
                                "path": "/status",
                                "body": { "action": "turnOff", "resource": "heater" }
                            }
              <http.post(request)>.status == "off";
            ```
            """)
    public Flux<Val> post(@JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.POST, requestSettings);
    }

    @EnvironmentAttribute(docs = """
            ```<put(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP PUT request and
            polls it according the the settings.

            Example:
            ```
            policy "http example"
            permit
            where
              var request = {
                                "baseUrl": "https://example.com",
                                "path": "/status",
                                "body": { "action": "turnOff", "resource": "heater" }
                            }
              <http.put(request)>.status == "off";
            ```
            """)
    public Flux<Val> put(@JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.PUT, requestSettings);
    }

    @EnvironmentAttribute(docs = """
            ```<patch(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP PATCH request and
            polls it according the the settings.

            Example:
            ```
            policy "http example"
            permit
            where
              var request = {
                                "baseUrl": "https://example.com",
                                "path": "/status",
                                "body": { "action": "turnOff", "resource": "heater" }
                            }
              <http.patch(request)>.status == "off";
            ```
            """)
    public Flux<Val> patch(@JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.PATCH, requestSettings);
    }

    @EnvironmentAttribute(docs = """
            ```<delete(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP DELETE
            request and polls it according the the settings.

            Example:
            ```
            policy "http example"
            permit
            where
              var request = {
                                "baseUrl": "https://example.com",
                                "path": "/status"
                            }
              <http.delete(request)> != undefined;
            ```
            """)
    public Flux<Val> delete(@JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.DELETE, requestSettings);
    }

    @EnvironmentAttribute(docs = """
            ```<websocket(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and connects to a Websocket and emits events
            as sent by the server. Upon connection, the ```body``` of the settings is sent to the server.

            Example:
            ```
            policy "http example"
            permit
            where
              var request = {
                                "baseUrl": "https://example.com",
                                "path": "/status",
                                "body": "message"
                            }
              <http.websocket(request)>.health == "GOOD";
            ```
            """)
    public Flux<Val> websocket(@JsonObject Val requestSettings) {
        return webClient.consumeWebSocket(requestSettings);
    }

    @EnvironmentAttribute(docs = """
            ```(TEXT resourceUrl).<get(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl``.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP GET
            request and polls it according the the settings.

            Example:
            ```
            policy "http example"
            permit
            where
              "https://example.com/resources/123".<http.get({ })>.status == "HEALTHY";
            ```
            """)
    public Flux<Val> get(@Text Val resourceUrl, @JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.GET, withBaseUrl(resourceUrl, requestSettings));
    }

    @EnvironmentAttribute(docs = """
            ```(TEXT resourceUrl).<post(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl``.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP POST
            request and polls it according the the settings.

            Example:
            ```
            policy "http example"
            permit
            where
              "https://example.com/resources/123".<http.post({ body = "\\"test\\"" })>.status == "OK";
            ```
            """)
    public Flux<Val> post(@Text Val resourceUrl, @JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.POST, withBaseUrl(resourceUrl, requestSettings));
    }

    @EnvironmentAttribute(docs = """
            ```(TEXT resourceUrl).<put(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl``.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP PUT
            request and polls it according the the settings.

            Example:
            ```
            policy "http example"
            permit
            where
              "https://example.com/resources/123".<http.put({ body = "\\"test\\"" })>.status == "OK";
            ```
            """)
    public Flux<Val> put(@Text Val resourceUrl, @JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.PUT, withBaseUrl(resourceUrl, requestSettings));
    }

    @EnvironmentAttribute(docs = """
            ```(TEXT resourceUrl).<patch(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl``.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP PATCH
            request and polls it according the the settings.

            Example:
            ```
            policy "http example"
            permit
            where
              "https://example.com/resources/123".<http.patch({ body = "\\"test\\"" })>.status == "OK";
            ```
            """)
    public Flux<Val> patch(@Text Val resourceUrl, @JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.PATCH, withBaseUrl(resourceUrl, requestSettings));
    }

    @EnvironmentAttribute(docs = """
            ```(TEXT resourceUrl).<delete(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl``.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP DELETE
            request and polls it according the the settings.

            Example:
            ```
            policy "http example"
            permit
            where
              "https://example.com/resources/123".<http.delete({})> != undefined;
            ```
            """)
    public Flux<Val> delete(@Text Val resourceUrl, @JsonObject Val requestSettings) {
        return webClient.httpRequest(HttpMethod.DELETE, withBaseUrl(resourceUrl, requestSettings));
    }

    @EnvironmentAttribute(docs = """
            ```(TEXT resourceUrl).<websocket(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl``.
            This attribute takes a ```requestSettings``` object as a parameter and connects to a Websocket and emits events
            as sent by the server. Upon connection, the ```body``` of the settings is sent to the server.

            Example:
            ```
            policy "http example"
            permit
            where
              var request = { "body": "message" }
             "baseUrl": "https://example.com/status".<http.websocket(request)>.health == "GOOD";
            ```
            """)
    public Flux<Val> websocket(@Text Val resourceUrl, @JsonObject Val requestSettings) {
        return webClient.consumeWebSocket(withBaseUrl(resourceUrl, requestSettings));
    }

    private Val withBaseUrl(Val baseUrl, Val requestSettings) {
        // unchecked cast is OK, @JsonObject => requestSettings is non-null ObjectNode
        final var newSettings = (ObjectNode) requestSettings.get().deepCopy();
        newSettings.set(ReactiveWebClient.BASE_URL, baseUrl.get());
        return Val.of(newSettings);
    }

}
