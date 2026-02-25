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
package io.sapl.spring.method.reactive;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.EnableReactiveSaplMethodSecurity;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.method.metadata.PreEnforce;
import io.sapl.spring.method.reactive.TransactionalMongoReactiveEnforcementIT.Application;
import io.sapl.spring.method.reactive.TransactionalMongoReactiveEnforcementIT.FailingMappingHandler;
import io.sapl.spring.method.reactive.TransactionalMongoReactiveEnforcementIT.MongoConfig;
import io.sapl.spring.method.reactive.TransactionalMongoReactiveEnforcementIT.ReactiveSecurityConfig;
import io.sapl.spring.method.reactive.TransactionalMongoReactiveEnforcementIT.TestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.UnaryOperator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(classes = { Application.class, MongoConfig.class, ReactiveSecurityConfig.class, TestService.class,
        FailingMappingHandler.class }, properties = { "spring.main.web-application-type=none",
                "io.sapl.pdp.embedded.enabled=false",
                "spring.autoconfigure.exclude="
                        + "de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration" })
class TransactionalMongoReactiveEnforcementIT {

    private static final String FAIL_ON_RESULT_OBLIGATION = "fail-on-result";

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0").withReplicaSet();

    @MockitoBean
    PolicyDecisionPoint pdp;

    @Autowired
    TestDocumentRepository repository;

    @Autowired
    TestService testService;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveMongoRepositories(basePackageClasses = TransactionalMongoReactiveEnforcementIT.class, considerNestedRepositories = true)
    static class Application {
        public static void main(String... args) {
            SpringApplication.run(Application.class, args);
        }
    }

    @TestConfiguration
    @EnableReactiveSaplMethodSecurity
    static class ReactiveSecurityConfig {
    }

    @TestConfiguration
    static class MongoConfig {
        @Bean
        ReactiveMongoTransactionManager transactionManager(ReactiveMongoDatabaseFactory factory) {
            return new ReactiveMongoTransactionManager(factory);
        }
    }

    @Document("test_documents")
    static class TestDocument {
        @Id
        String id;

        String name;

        TestDocument() {
        }

        TestDocument(String name) {
            this.name = name;
        }
    }

    interface TestDocumentRepository extends ReactiveCrudRepository<TestDocument, String> {
    }

    @Service
    static class TestService {
        private final TestDocumentRepository repository;

        TestService(TestDocumentRepository repository) {
            this.repository = repository;
        }

        @Transactional
        @PreEnforce
        public Mono<TestDocument> create(String name) {
            return repository.save(new TestDocument(name));
        }
    }

    @Component
    static class FailingMappingHandler implements MappingConstraintHandlerProvider<TestDocument> {

        @Override
        public boolean isResponsible(Value constraint) {
            return constraint instanceof ObjectValue obj && FAIL_ON_RESULT_OBLIGATION.equals(textValue(obj, "type"));
        }

        @Override
        public Class<TestDocument> getSupportedType() {
            return TestDocument.class;
        }

        @Override
        public UnaryOperator<TestDocument> getHandler(Value constraint) {
            return doc -> {
                throw new RuntimeException("Obligation handler failure on result");
            };
        }

        private static String textValue(ObjectValue obj, String key) {
            var value = obj.get(key);
            if (value instanceof TextValue(String text)) {
                return text;
            }
            return null;
        }
    }

    @Test
    @WithMockUser
    @DisplayName("when obligation handler fails on reactive MongoDB transactional method then document not in database")
    void whenObligationHandlerFailsOnReactiveMongoTransactionalMethod_thenDocumentNotInDatabase() {
        var obligation = ObjectValue.builder().put("type", Value.of(FAIL_ON_RESULT_OBLIGATION)).build();
        var decision   = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));

        StepVerifier.create(testService.create("test")).expectError(AccessDeniedException.class).verify();
        StepVerifier.create(repository.count()).expectNext(0L).verifyComplete();
    }

}
