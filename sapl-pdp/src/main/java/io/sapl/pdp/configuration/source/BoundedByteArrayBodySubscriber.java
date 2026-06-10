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
package io.sapl.pdp.configuration.source;

import lombok.val;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * {@link BodySubscriber} that buffers the full response body up to a fixed
 * byte cap. When the cap is exceeded the subscription is cancelled and the
 * resulting future completes exceptionally with an {@link IOException}, so the
 * caller can treat oversized responses identically to network errors and
 * trigger the standard retry/backoff path. Used to bound memory consumption
 * when fetching remote bundles from servers that may return arbitrarily large
 * payloads.
 */
final class BoundedByteArrayBodySubscriber implements BodySubscriber<byte[]> {

    private static final String ERROR_BODY_TOO_LARGE = "Response body exceeds maximum allowed size of %d bytes.";

    private final int                       maxBytes;
    private final ByteArrayOutputStream     buffer = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private volatile Flow.Subscription      subscription;

    BoundedByteArrayBodySubscriber(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> chunks) {
        for (val chunk : chunks) {
            val remaining = chunk.remaining();
            if (buffer.size() + remaining > maxBytes) {
                subscription.cancel();
                result.completeExceptionally(new IOException(ERROR_BODY_TOO_LARGE.formatted(maxBytes)));
                return;
            }
            val dst = new byte[remaining];
            chunk.get(dst);
            buffer.writeBytes(dst);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        result.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        result.complete(buffer.toByteArray());
    }
}
