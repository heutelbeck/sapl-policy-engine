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
package io.sapl.spring.method.blocking;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.EnableSaplMethodSecurity;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.method.blocking.TransactionalMongoBlockingEnforcementIT.Application;
import io.sapl.spring.method.blocking.TransactionalMongoBlockingEnforcementIT.FailingMappingHandler;
import io.sapl.spring.method.blocking.TransactionalMongoBlockingEnforcementIT.MethodSecurityConfiguration;
import io.sapl.spring.method.blocking.TransactionalMongoBlockingEnforcementIT.MongoConfig;
import io.sapl.spring.method.blocking.TransactionalMongoBlockingEnforcementIT.TestService;
import io.sapl.spring.method.metadata.PreEnforce;
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
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(classes = { Application.class, MongoConfig.class, MethodSecurityConfiguration.class, TestService.class,
        FailingMappingHandler.class }, properties = { "spring.main.web-application-type=none",
                "io.sapl.pdp.embedded.enabled=false",
                "spring.autoconfigure.exclude="
                        + "de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration" })
class TransactionalMongoBlockingEnforcementIT {

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
    @EnableMongoRepositories(basePackageClasses = TransactionalMongoBlockingEnforcementIT.class, considerNestedRepositories = true)
    static class Application {
        public static void main(String... args) {
            SpringApplication.run(Application.class, args);
        }
    }

    @TestConfiguration
    @EnableSaplMethodSecurity
    static class MethodSecurityConfiguration {
    }

    @TestConfiguration
    static class MongoConfig {
        @Bean
        MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
            return new MongoTransactionManager(factory);
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

    interface TestDocumentRepository extends MongoRepository<TestDocument, String> {
    }

    @Service
    static class TestService {
        private final TestDocumentRepository repository;

        TestService(TestDocumentRepository repository) {
            this.repository = repository;
        }

        @Transactional
        @PreEnforce
        public TestDocument create(String name) {
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
    @DisplayName("when obligation handler fails after blocking MongoDB transactional method then document not in database")
    void whenObligationHandlerFailsAfterBlockingMongoTransactionalMethod_thenDocumentNotInDatabase() {
        var obligation = ObjectValue.builder().put("type", Value.of(FAIL_ON_RESULT_OBLIGATION)).build();
        var decision   = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
        when(pdp.decideOnceBlocking(any(AuthorizationSubscription.class))).thenReturn(decision);

        assertThatThrownBy(() -> testService.create("test")).isInstanceOf(AccessDeniedException.class);
        assertThat(repository.count()).isZero();
    }

}
