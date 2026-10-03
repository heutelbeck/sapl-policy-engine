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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.bson.Document;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import reactor.core.publisher.Mono;

class MongoAttributeStoreTests {
    private static final String       COLLECTION = "attributes";
    private static final AttributeKey KEY        = new AttributeKey(null, "sapl.test.attribute", List.of());

    private ReactiveMongoTemplate mongo;
    private MongoAttributeStore   store;

    @BeforeEach
    void setUp() {
        mongo = mock(ReactiveMongoTemplate.class);
        when(mongo.collectionExists(COLLECTION)).thenReturn(Mono.just(true));
        when(mongo.executeCommand(any(Document.class))).thenReturn(Mono.just(new Document()));
        store = new MongoAttributeStore(mongo, COLLECTION);
    }

    @Test
    @DisplayName("Publish with a negative TTL throws an exception and never writes into the database")
    void whenPublishedWithNegativeTTLThenExceptionIsThrown() {
        clearInvocations(mongo);
        var ttl   = Duration.ofSeconds(-1);
        var value = Value.of("negative");

        assertThatThrownBy(() -> store.publish(KEY, value, ttl, "default")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("TTL must be a strictly positive Duration.");
        verifyNoInteractions(mongo);
    }

    @Test
    @DisplayName("When the attribute exists then get returns it's value")
    void whenAttributeExistsThenGetReturnValue() {
        var document = new Document("value", ValueJsonMarshaller.toJsonString(Value.of("aValue")));

        when(mongo.findOne(any(Query.class), eq(Document.class), eq(COLLECTION))).thenReturn(Mono.just(document));
        assertThat(store.get(KEY, "default")).isEqualTo(Value.of("aValue"));
    }

    @Test
    @DisplayName("When the attribute doesn't exist then get returns UNDEFINED")
    void whenAttributeNotExistThenGetReturnUndefined() {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq(COLLECTION))).thenReturn(Mono.empty());
        assertThat(store.get(KEY, "default")).isEqualTo(Value.UNDEFINED);
    }
}
