/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import lombok.Setter;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

class TestMockServerDispatcher extends Dispatcher {

    private final String kidPath;

    private final Map<String, String> kidToPubKeyMap;

    @Setter
    private DispatchMode dispatchMode = DispatchMode.True;

    public TestMockServerDispatcher(String kidPath, Map<String, String> kidToPubKeyMap) {
        this.kidPath        = kidPath;
        this.kidToPubKeyMap = new HashMap<>(kidToPubKeyMap);
    }

    @NotNull
    @Override
    public MockResponse dispatch(RecordedRequest request) {
        var path = request.getPath();
        if (path == null || !path.startsWith(kidPath))
            return new MockResponse().setResponseCode(404);

        String requestedId = path.substring(kidPath.length());

        if (!kidToPubKeyMap.containsKey(requestedId))
            return new MockResponse().setResponseCode(404);

        switch (this.dispatchMode) {
        case Bogus:
            return this.dispatchBogusKey();
        case Invalid:
            return this.dispatchInvalidKey(requestedId);
        case True:
            return this.dispatchTrueKey(requestedId);
        case Wrong:
            return this.dispatchWrongKey();
        case Basic:
            return this.dispatchBasicKey(requestedId);
        default:
            return new MockResponse().setResponseCode(404);
        }
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
