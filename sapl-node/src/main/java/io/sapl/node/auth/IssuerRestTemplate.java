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
package io.sapl.node.auth;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import lombok.val;

/**
 * The client with which the node reads the discovery document and the keys of
 * the OAuth2 issuer. It refuses a discovery document whose address of the keys
 * is not https, or not http where an http issuer is allowed, before any key is
 * fetched from it: keys fetched in cleartext could be replaced on the network
 * path. Spring Security reads the discovery document through
 * {@link #exchange(RequestEntity, ParameterizedTypeReference)} as a map, and
 * passes on the refusal unchanged.
 */
final class IssuerRestTemplate extends RestTemplate {

    private static final String ERROR_KEY_ADDRESS_NOT_ALLOWED = "The discovery document names no https address of the keys, nor an http one where http is allowed.";

    private static final String KEY_ADDRESS  = "jwks_uri";
    private static final String SCHEME_HTTP  = "http";
    private static final String SCHEME_HTTPS = "https";

    // The defaults of the issuer client of Spring Security.
    private static final int DEFAULT_TIMEOUT_MILLIS = 30_000;

    private final boolean allowInsecureIssuer;

    /**
     * @param allowInsecureIssuer true if the keys may be fetched over http
     */
    IssuerRestTemplate(boolean allowInsecureIssuer) {
        this.allowInsecureIssuer = allowInsecureIssuer;
        val requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory
                .setConnectTimeout(Integer.getInteger("sun.net.client.defaultConnectTimeout", DEFAULT_TIMEOUT_MILLIS));
        requestFactory.setReadTimeout(Integer.getInteger("sun.net.client.defaultReadTimeout", DEFAULT_TIMEOUT_MILLIS));
        setRequestFactory(requestFactory);
    }

    @Override
    public <T> ResponseEntity<T> exchange(RequestEntity<?> request, ParameterizedTypeReference<T> responseType) {
        val response = super.exchange(request, responseType);
        if (response.getBody() instanceof Map<?, ?> discoveryDocument
                && !isAllowedKeyAddress(discoveryDocument.get(KEY_ADDRESS))) {
            throw new IllegalArgumentException(ERROR_KEY_ADDRESS_NOT_ALLOWED);
        }
        return response;
    }

    private boolean isAllowedKeyAddress(@Nullable Object keyAddress) {
        if (!(keyAddress instanceof String address)) {
            return false;
        }
        try {
            val uri    = new URI(address);
            val scheme = uri.getScheme();
            return uri.getHost() != null && (SCHEME_HTTPS.equalsIgnoreCase(scheme)
                    || allowInsecureIssuer && SCHEME_HTTP.equalsIgnoreCase(scheme));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
