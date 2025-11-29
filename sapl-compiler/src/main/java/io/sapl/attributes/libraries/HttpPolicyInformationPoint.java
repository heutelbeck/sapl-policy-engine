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
package io.sapl.attributes.libraries;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@PolicyInformationPoint(name = "http", description = HttpPolicyInformationPoint.DESCRIPTION, pipDocumentation = HttpPolicyInformationPoint.DOCUMENTATION)
public class HttpPolicyInformationPoint {
    public static final String DESCRIPTION   = "This Policy Information Point to get and monitor HTTP based information.";
    public static final String DOCUMENTATION = """
            This Policy Information Point provides basic means to source attribute data by consuming
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

            Connection timeout is 10 seconds, read timeout is 30 seconds. Unresponsive endpoints will
            result in an error value.

            Example:
            ```json
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
            ```sapl
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
    @Attribute
    public Flux<Value> get(ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.GET, requestSettings);
    }

    @EnvironmentAttribute(docs = """
            ```<post(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP POST request and
            polls it according the the settings.

            Example:
            ```sapl
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
    @Attribute
    public Flux<Value> post(ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.POST, requestSettings);
    }

    @EnvironmentAttribute(docs = """
            ```<put(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP PUT request and
            polls it according the the settings.

            Example:
            ```sapl
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
    @Attribute
    public Flux<Value> put(ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.PUT, requestSettings);
    }

    @EnvironmentAttribute(docs = """
            ```<patch(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP PATCH request and
            polls it according the the settings.

            Example:
            ```sapl
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
    @Attribute
    public Flux<Value> patch(ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.PATCH, requestSettings);
    }

    @EnvironmentAttribute(docs = """
            ```<delete(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP DELETE
            request and polls it according the the settings.

            Example:
            ```sapl
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
    @Attribute
    public Flux<Value> delete(ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.DELETE, requestSettings);
    }

    @EnvironmentAttribute(docs = """
            ```<websocket(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and connects to a Websocket and emits events
            as sent by the server. Upon connection, the ```body``` of the settings is sent to the server.

            Example:
            ```sapl
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
    @Attribute
    public Flux<Value> websocket(ObjectValue requestSettings) {
        return webClient.consumeWebSocket(requestSettings);
    }

    @EnvironmentAttribute(docs = """
            ```(TEXT resourceUrl).<get(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl```.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP GET
            request and polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
            where
              "https://example.com/resources/123".<http.get({ })>.status == "HEALTHY";
            ```
            """)
    @Attribute
    public Flux<Value> get(TextValue resourceUrl, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.GET, withBaseUrl(resourceUrl, requestSettings));
    }

    @EnvironmentAttribute(docs = """
            ```(TEXT resourceUrl).<post(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl```.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP POST
            request and polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
            where
              "https://example.com/resources/123".<http.post({ body = "\\"test\\"" })>.status == "OK";
            ```
            """)
    @Attribute
    public Flux<Value> post(TextValue resourceUrl, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.POST, withBaseUrl(resourceUrl, requestSettings));
    }

    @EnvironmentAttribute(docs = """
            ```(TEXT resourceUrl).<put(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl```.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP PUT
            request and polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
            where
              "https://example.com/resources/123".<http.put({ body = "\\"test\\"" })>.status == "OK";
            ```
            """)
    @Attribute
    public Flux<Value> put(TextValue resourceUrl, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.PUT, withBaseUrl(resourceUrl, requestSettings));
    }

    @EnvironmentAttribute(docs = """
            ```(TEXT resourceUrl).<patch(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl```.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP PATCH
            request and polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
            where
              "https://example.com/resources/123".<http.patch({ body = "\\"test\\"" })>.status == "OK";
            ```
            """)
    @Attribute
    public Flux<Value> patch(TextValue resourceUrl, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.PATCH, withBaseUrl(resourceUrl, requestSettings));
    }

    @EnvironmentAttribute(docs = """
            ```(TEXT resourceUrl).<delete(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl```.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP DELETE
            request and polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
            where
              "https://example.com/resources/123".<http.delete({})> != undefined;
            ```
            """)
    @Attribute
    public Flux<Value> delete(TextValue resourceUrl, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.DELETE, withBaseUrl(resourceUrl, requestSettings));
    }

    @EnvironmentAttribute(docs = """
            ```(TEXT resourceUrl).<websocket(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl```.
            This attribute takes a ```requestSettings``` object as a parameter and connects to a Websocket and emits events
            as sent by the server. Upon connection, the ```body``` of the settings is sent to the server.

            Example:
            ```sapl
            policy "http example"
            permit
            where
              var request = { "body": "message" }
             "baseUrl": "https://example.com/status".<http.websocket(request)>.health == "GOOD";
            ```
            """)
    @Attribute
    public Flux<Value> websocket(TextValue resourceUrl, ObjectValue requestSettings) {
        return webClient.consumeWebSocket(withBaseUrl(resourceUrl, requestSettings));
    }

    private ObjectValue withBaseUrl(TextValue baseUrl, ObjectValue requestSettings) {
        // ObjectValue implements Map<String, Value>
        var builder = ObjectValue.builder();
        builder.putAll(requestSettings);
        builder.put(ReactiveWebClient.BASE_URL, baseUrl);
        return builder.build();
    }

}
