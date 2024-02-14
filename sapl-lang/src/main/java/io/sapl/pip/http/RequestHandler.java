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

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec;
import org.springframework.web.util.UriBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.experimental.UtilityClass;

@UtilityClass
class RequestHandler {

    public RequestHeadersSpec<?> setGetRequest(RequestHeadersUriSpec<?> in, String path,
            Map<String, String> urlParameters, Map<String, String> requestHeaders, MediaType acceptMediaType) {
        var params = check(urlParameters);
        var hdrs   = check(requestHeaders);

        return in.uri(u -> setUrlParams(u, params).path(path).build()).headers(h -> setHeaders(h, hdrs))
                .accept(acceptMediaType);
    }

    public RequestHeadersSpec<?> setRequest(RequestBodyUriSpec in, String path, Map<String, String> urlParameters,
            Map<String, String> requestHeaders, JsonNode body, MediaType acceptMediaType, MediaType contentType) {
        var params = check(urlParameters);
        var hdrs   = check(requestHeaders);
        return setBody(in.uri(u -> setUrlParams(u, params).path(path).build()).headers(h -> setHeaders(h, hdrs))
                .accept(acceptMediaType).contentType(contentType), body);
    }

    public static RequestHeadersSpec<?> setBody(RequestBodySpec in, JsonNode body) {
        return in.bodyValue(body);
    }

    public static HttpHeaders setHeaders(HttpHeaders h, Map<String, String> headers) {
        for (var header : headers.entrySet()) {
            h.set(header.getKey(), header.getValue());
        }
        return h;
    }

    public static UriBuilder setUrlParams(UriBuilder uri, Map<String, String> urlParams) {
        for (var param : urlParams.entrySet()) {
            uri.queryParam(param.getKey(), param.getValue());
        }
        return uri;
    }

    public static Map<String, String> check(Map<String, String> toCheck) {
        if (toCheck == null) {
            return new HashMap<>();
        }
        return toCheck;
    }

}
