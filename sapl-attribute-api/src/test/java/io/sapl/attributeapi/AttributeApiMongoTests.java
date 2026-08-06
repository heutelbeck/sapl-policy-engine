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
package io.sapl.attributeapi;

import com.mongodb.reactivestreams.client.MongoClients;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.attributes.backend.MongoAttributeStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@SpringBootTest(classes = AttributeApiApplication.class, properties = { "io.sapl.attribute-api.enabled=true",
        "io.sapl.attribute-api.allow-no-auth=true", "io.sapl.attribute-api.allow-basic-auth=false",
        "io.sapl.attribute-api.allow-api-key-auth=false", "io.sapl.attribute-api.allow-oauth2-auth=false",
        "io.sapl.attributes.storage=none" })
@Testcontainers
@Import(AttributeApiMongoTests.Config.class)
class AttributeApiMongoTests extends AbstractAttributeApiTests {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

    @Override
    protected void cleanRepository() {
        var client   = MongoClients.create(mongo.getConnectionString());
        var template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "sapl"));
        template.dropCollection("attributes").block();
    }

    @TestConfiguration
    static class Config {
        @Bean
        AttributeStore attributeStore() {
            var client   = MongoClients.create(mongo.getConnectionString());
            var template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "sapl"));
            return new MongoAttributeStore(template, "attributes");
        }
    }
}
