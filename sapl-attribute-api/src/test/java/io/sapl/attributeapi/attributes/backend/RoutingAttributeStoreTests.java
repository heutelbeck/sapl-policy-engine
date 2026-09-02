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
package io.sapl.attributeapi.attributes.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import io.sapl.api.model.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import io.sapl.attributeapi.attributes.BackendHandle;

@ExtendWith(MockitoExtension.class)
class RoutingAttributeStoreTests {

    @Mock
    private BackendHandle handle;

    @Mock
    private AttributeStore store;

    @Mock
    private BackendHandle otherHandle;

    private RoutingAttributeStore routingStore;
    private AttributeKey          key;

    @BeforeEach
    void setUp() {
        routingStore = new RoutingAttributeStore(Map.of("backend-1", handle), Map.of("tenant-01", "backend-1"));

        // Just a key with an attribute name. No entity or arguments.
        key = new AttributeKey(null, "test.attribute", List.of());
    }

    @Test
    @DisplayName("An unknown pdp id throws an exception without touching any backend")
    void whenPdpIdIsUnknownThenGetThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> routingStore.get(key, "unknown-pdp")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown-pdp");

        verifyNoInteractions(handle);
    }

    @Test
    @DisplayName("A known pdp id delegates the get() call to the resolved backend")
    void whenPdpIdIsKnownThenGetDelegatesToResolvedBackendStore() {
        when(handle.resolveOrThrow("backend-1")).thenReturn(store);
        when(store.get(key, "tenant-01")).thenReturn(Value.of("test"));

        var result = routingStore.get(key, "tenant-01");
        assertThat(result).isEqualTo(Value.of("test"));
        verify(store).get(key, "tenant-01");
    }

    @Test
    @DisplayName("A known pdp id  delegates the publish() call to the resolved backend")
    void whenPdpIdIsKnownThenPublishDelegatesToResolvedBackendStore() {
        when(handle.resolveOrThrow("backend-1")).thenReturn(store);
        when(store.publish(key, Value.of("test1"), "tenant-01")).thenReturn(true);

        var created = routingStore.publish(key, Value.of("test1"), "tenant-01");

        assertThat(created).isTrue();
        verify(store).publish(key, Value.of("test1"), "tenant-01");
    }

    @Test
    @DisplayName("A known pdp id  delegates the publish() call with TTL to the resolved backend")
    void whenPdpIdIsKnownThenPublishWithTtlDelegatesToResolvedBackendStore() {
        when(handle.resolveOrThrow("backend-1")).thenReturn(store);
        when(store.publish(key, Value.of("test2"), Duration.ofHours(1), "tenant-01")).thenReturn(true);

        var created = routingStore.publish(key, Value.of("test2"), Duration.ofHours(1), "tenant-01");

        assertThat(created).isTrue();
        verify(store).publish(key, Value.of("test2"), Duration.ofHours(1), "tenant-01");
    }

    @Test
    @DisplayName("A known pdp id delegates the remove() call to the resolved backend")
    void whenPdpIdIsKnownThenRemoveDelegatesToResolvedBackendStore() {
        when(handle.resolveOrThrow("backend-1")).thenReturn(store);
        when(store.remove(key, "tenant-01")).thenReturn(true);

        var removed = routingStore.remove(key, "tenant-01");

        assertThat(removed).isTrue();
        verify(store).remove(key, "tenant-01");
    }

    @Test
    @DisplayName("A known pdp id delegates the count() call to the resolved backend")
    void whenPdpIdIsKnownThenCountDelegatesToResolvedBackendStore() {
        when(handle.resolveOrThrow("backend-1")).thenReturn(store);
        when(store.count("tenant-01")).thenReturn(5L);

        var result = routingStore.count("tenant-01");

        assertThat(result).isEqualTo(5L);
        verify(store).count("tenant-01");
    }

    @Test
    @DisplayName("A known pdp id delegates getAll() call to the resolved backend")
    void whenPdpIdIsKnownThenGetAllDelegatesToResolvedBackendStore() {
        when(handle.resolveOrThrow("backend-1")).thenReturn(store);
        var entry = new AttributeEntry(key, Value.of("test"));
        when(store.getAll("tenant-01", 10, 0)).thenReturn(List.of(entry));

        var result = routingStore.getAll("tenant-01", 10, 0);

        assertThat(result).containsExactly(entry);
        verify(store).getAll("tenant-01", 10, 0);
    }

    @Test
    @DisplayName("The AttributeBackendUnavailableException message never contains the original exception's details")
    void whenBackendUnavailableExceptionIsThrownThenMessageDoesNotLeakOriginalExceptionDetails() {
        when(handle.resolveOrThrow("backend-1")).thenReturn(store);
        when(store.get(key, "tenant-01"))
                .thenThrow(new DataAccessResourceFailureException("connection refused to db-host-internal:5432"));

        assertThatThrownBy(() -> routingStore.get(key, "tenant-01"))
                .isInstanceOf(AttributeBackendUnavailableException.class)
                .hasMessage("The service is currently unavailable").hasMessageNotContaining("db-host-internal");

        verify(handle).invalidate();
    }

    @Test
    @DisplayName("A close() call closes every BackendHandle in the map")
    void whenCloseIsCalledThenEveryBackendHandleIsClosed() {
        var multiBackendStore = new RoutingAttributeStore(Map.of("backend-1", handle, "backend-2", otherHandle),
                Map.of("tenant-01", "backend-1"));

        multiBackendStore.close();

        verify(handle).close();
        verify(otherHandle).close();
    }
}
