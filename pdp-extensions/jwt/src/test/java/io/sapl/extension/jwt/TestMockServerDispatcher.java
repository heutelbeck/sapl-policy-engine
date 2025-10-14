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
package io.sapl.extension.jwt;

import lombok.Setter;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

class TestMockServerDispatcher extends Dispatcher {

    private final String kidPath;

    private final Map<String, String> kidToPubKeyMap;

    @Setter
    private DispatchMode dispatchMode = DispatchMode.TRUE;

    public TestMockServerDispatcher(String kidPath, Map<String, String> kidToPubKeyMap) {
        this.kidPath        = kidPath;
        this.kidToPubKeyMap = new HashMap<>(kidToPubKeyMap);
    }

    @NotNull
    @Override
    public MockResponse dispatch(RecordedRequest request) {
        final var path = request.getPath();
        if (null == path || !path.startsWith(kidPath))
            return new MockResponse().setResponseCode(404);

        String requestedId = path.substring(kidPath.length());

        if (!kidToPubKeyMap.containsKey(requestedId))
            return new MockResponse().setResponseCode(404);

        return switch (this.dispatchMode) {
        case BOGUS   -> this.dispatchBogusKey();
        case INVALID -> this.dispatchInvalidKey(requestedId);
        case TRUE    -> this.dispatchTrueKey(requestedId);
        case WRONG   -> this.dispatchWrongKey();
        case BASIC   -> this.dispatchBasicKey(requestedId);
        default      -> new MockResponse().setResponseCode(404);
        };
    }

    private MockResponse dispatchTrueKey(String requestedId) {
        return new MockResponse().setBody(kidToPubKeyMap.get(requestedId));
    }

    private MockResponse dispatchWrongKey() {
        return new MockResponse()
                .setBody(Base64DataUtil.encodePublicKeyToBase64URLPrimary(Base64DataUtil.generateRSAKeyPair()));
    }

    private MockResponse dispatchInvalidKey(String requestedId) {
        return new MockResponse().setBody(Base64DataUtil.base64Invalid(kidToPubKeyMap.get(requestedId)));
    }

    private MockResponse dispatchBogusKey() {
        return new MockResponse().setBody(Base64DataUtil.base64Bogus());
    }

    private MockResponse dispatchBasicKey(String requestedId) {
        return new MockResponse().setBody(Base64DataUtil.base64Basic(kidToPubKeyMap.get(requestedId)));
    }

}
