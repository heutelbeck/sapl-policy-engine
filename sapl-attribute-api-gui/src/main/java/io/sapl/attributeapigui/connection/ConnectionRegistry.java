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
package io.sapl.attributeapigui.connection;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.sapl.attributeapigui.client.AttributeApiClient;
import io.sapl.attributeapigui.config.AttributeApiConnectionProperties;

@VaadinSessionScope
@Component
public class ConnectionRegistry {
    private final Map<String, SavedConnection>    connections = new LinkedHashMap<>();
    private final Map<String, AttributeApiClient> clients     = new HashMap<>();
    private final ReentrantLock                   lock        = new ReentrantLock();
    private String                                activeId;

    public ConnectionRegistry(AttributeApiConnectionProperties properties) {
        var initialConnection = new SavedConnection(UUID.randomUUID().toString(), properties.getName(),
                ConnectionSettings.from(properties));
        connections.put(initialConnection.id(), initialConnection);
        activeId = initialConnection.id();
    }

    // Return only a copy of the connection to prevent that someone can overwrite it
    public List<SavedConnection> getSavedConnection() {
        lock.lock();
        try {
            return List.copyOf(connections.values());
        } finally {
            lock.unlock();
        }
    }

    public SavedConnection getActiveConnection() {
        lock.lock();
        try {
            return connections.get(activeId);
        } finally {
            lock.unlock();
        }
    }

    public void setActiveId(String id) {
        lock.lock();
        try {
            if (connections.containsKey(id))
                activeId = id;
        } finally {
            lock.unlock();
        }
    }

    public void addConnection(SavedConnection connection) {
        lock.lock();
        try {
            connections.put(connection.id(), connection);
        } finally {
            lock.unlock();
        }
    }

    public void removeConnection(String id) {
        lock.lock();
        try {
            if (connections.size() <= 1)
                return;

            connections.remove(id);
            clients.remove(id);

            // Set a new active connection if the removed connection is currently active
            if (id.equals(activeId)) {
                activeId = connections.keySet().iterator().next();
            }
        } finally {
            lock.unlock();
        }
    }

    public AttributeApiClient activeClient() {
        lock.lock();
        try {
            return clients.computeIfAbsent(activeId, key -> new AttributeApiClient(connections.get(key).settings()));
        } finally {
            lock.unlock();
        }
    }
}
