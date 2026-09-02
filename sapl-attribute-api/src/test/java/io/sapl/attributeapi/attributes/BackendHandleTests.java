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
package io.sapl.attributeapi.attributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.sapl.attributeapi.attributes.backend.AttributeBackendUnavailableException;
import io.sapl.attributeapi.attributes.backend.AttributeStore;

@ExtendWith(MockitoExtension.class)
class BackendHandleTests {
    @Mock
    private Supplier<AttributeStore> builder;

    @Mock
    private AttributeStore store;

    @Test
    @DisplayName("A store that is successfully built is cached and called exactly once")
    void whenBuilderSucceedsThenStoreIsCachedAndBuilderCalledOnce() {
        when(builder.get()).thenReturn(store);

        var handle = new BackendHandle(builder);

        var firstResolve  = handle.resolveOrThrow("test-backend");
        var secondResolve = handle.resolveOrThrow("test-backend");

        assertThat(firstResolve).isSameAs(store);
        assertThat(secondResolve).isSameAs(store);
    }

    @Test
    @DisplayName("A builder that failds throws an exception that the backend is unavailable")
    void whenBuilderFailedThenThrowsAttributeBackendUnavailableException() {
        when(builder.get()).thenThrow(new RuntimeException("connection refused"));

        var handle = new BackendHandle(builder);
        assertThatThrownBy(() -> handle.resolveOrThrow("test-backend"))
                .isInstanceOf(AttributeBackendUnavailableException.class);
    }

    @Test
    @DisplayName("A failed build does fail fast and does not retry to reconnect too long.")
    void whenBuilderFailsThenFailFastWithoutRebuilding() {
        when(builder.get()).thenThrow(new RuntimeException("connection refused"));

        var handle = new BackendHandle(builder);

        // First calls fails and sets a cooldown internally (lastFailure). The second call should be rejected fast.
        assertThatThrownBy(() -> handle.resolveOrThrow("test-backend"))
                .isInstanceOf(AttributeBackendUnavailableException.class);
        assertThatThrownBy(() -> handle.resolveOrThrow("test-backend"))
                .isInstanceOf(AttributeBackendUnavailableException.class);

        // builder.get() is called only once during cooldown period (should be 5s)
        verify(builder, times(1)).get();
    }

    @Test
    @DisplayName("After invalidation the next resolve fails fast during the cooldown instead of rebuilding")
    void whenBackendInvalidatedThenNextResolveFailsDuringCooldown() {
        when(builder.get()).thenReturn(store);

        var handle       = new BackendHandle(builder);
        var firstResolve = handle.resolveOrThrow("test-backend");

        // First resolve before invalidate
        assertThat(firstResolve).isSameAs(store);
        verify(builder, times(1)).get();

        handle.invalidate();

        // Still in cooldown period. Should only be called once
        assertThatThrownBy(() -> handle.resolveOrThrow("test-backend"))
                .isInstanceOf(AttributeBackendUnavailableException.class);
        verify(builder, times(1)).get();
    }

    @Test
    @DisplayName("When the pdp id can't be resolved then close no-op on the failed object")
    void whenPdpIdIsUnknownToResolverThenCloseDoesNoop() {
        var handle = new BackendHandle(builder);

        // Store has still no objects and closes nothing
        assertThatCode(handle::close).doesNotThrowAnyException();
        verify(store, never()).close();
        verifyNoInteractions(builder);
    }

    @Test
    @DisplayName("Closing a handle with a successfully resolved store delegate to the stores's close")
    void whenPdpIdResolvedThenCloseDelegatesToStore() {
        when(builder.get()).thenReturn(store);

        var handle  = new BackendHandle(builder);
        var resolve = handle.resolveOrThrow("test-backend");

        assertThat(resolve).isSameAs(store);
        verify(builder, times(1)).get();

        handle.close();
        verify(store, times(1)).close();
    }
}
