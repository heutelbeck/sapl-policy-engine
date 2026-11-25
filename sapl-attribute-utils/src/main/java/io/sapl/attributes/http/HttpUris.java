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
package io.sapl.attributes.http;

import lombok.val;
import okhttp3.HttpUrl;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 * HTTP and WebSocket URIs with browser-style international domain names.
 * Unicode hosts and their Punycode spelling select the same destination.
 * Only the host is canonicalized; the scheme, explicit port, user information,
 * raw path, query and fragment are retained.
 */
public final class HttpUris {
    private static final String ERROR_INVALID_AUTHORITY = "The URL authority is invalid.";

    private HttpUris() {
    }

    /**
     * Parses a URI and converts an international hostname to its ASCII spelling.
     * For example, {@code straße.example} becomes {@code xn--strae-oqa.example}.
     * Already valid ASCII hosts retain their original URI representation.
     *
     * @param source the URI text
     * @return the URI with an ASCII hostname
     * @throws IllegalArgumentException if the URI or hostname is invalid
     */
    public static URI create(String source) {
        val uri       = URI.create(source);
        val authority = uri.getRawAuthority();
        if (uri.getHost() != null || authority == null || uri.getScheme() == null
                || authority.chars().allMatch(character -> character <= 0x7f)) {
            return uri;
        }
        final URL components;
        try {
            // The JDK URL parser exposes Unicode host and userinfo without opening a connection.
            components = URI.create("https://" + authority).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(ERROR_INVALID_AUTHORITY, e);
        }
        val host = components.getHost();
        if (host.chars().allMatch(character -> character <= 0x7f)) {
            return uri;
        }
        val ascii    = new HttpUrl.Builder().scheme("https").host(host).build().host();
        val userinfo = components.getUserInfo();
        val start    = uri.getScheme().length() + 3 + (userinfo == null ? 0 : userinfo.length() + 1);
        return URI.create(source.substring(0, start) + ascii + source.substring(start + host.length()));
    }
}
