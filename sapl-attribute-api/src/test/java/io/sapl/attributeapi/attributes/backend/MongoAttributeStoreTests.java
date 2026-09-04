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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.bson.Document;
import io.sapl.api.model.Value;
import reactor.core.publisher.Mono;

class MongoAttributeStoreTests {

    @Test
    @DisplayName("Publish with a negative TTL throws an exception and never writes into the database")
    void whenPublishedWithNegativeTTLExceptionIsThrown() {
        var mongo = mock(ReactiveMongoTemplate.class);
        when(mongo.collectionExists("attributes")).thenReturn(Mono.just(true));
        when(mongo.executeCommand(any(Document.class))).thenReturn(Mono.just(new Document()));

        var store = new MongoAttributeStore(mongo, "attributes");
        var key   = new AttributeKey(null, "sapl.test.negative.ttl", List.of());
        var value = Value.of("negative");
        var ttl   = Duration.ofSeconds(-1);

        assertThatThrownBy(() -> store.publish(key, value, ttl, "default")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("TTL must be a strictly positive Duration.");
    }
}
