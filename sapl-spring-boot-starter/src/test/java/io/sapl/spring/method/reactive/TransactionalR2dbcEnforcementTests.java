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

import io.r2dbc.spi.ConnectionFactory;
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
import io.sapl.spring.method.reactive.TransactionalR2dbcEnforcementTests.Application;
import io.sapl.spring.method.reactive.TransactionalR2dbcEnforcementTests.FailingMappingHandler;
import io.sapl.spring.method.reactive.TransactionalR2dbcEnforcementTests.R2dbcConfig;
import io.sapl.spring.method.reactive.TransactionalR2dbcEnforcementTests.ReactiveSecurityConfig;
import io.sapl.spring.method.reactive.TransactionalR2dbcEnforcementTests.TestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.UnaryOperator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { Application.class, R2dbcConfig.class, ReactiveSecurityConfig.class, TestService.class,
        FailingMappingHandler.class }, properties = { "spring.main.web-application-type=none",
                "io.sapl.pdp.embedded.enabled=false", "spring.r2dbc.url=r2dbc:h2:mem:///testdb",
                "spring.autoconfigure.exclude="
                        + "de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration" })
class TransactionalR2dbcEnforcementTests {

    private static final String FAIL_ON_RESULT_OBLIGATION = "fail-on-result";

    @MockitoBean
    PolicyDecisionPoint pdp;

    @Autowired
    TestEntityRepository repository;

    @Autowired
    TestService testService;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableR2dbcRepositories(basePackageClasses = TransactionalR2dbcEnforcementTests.class, considerNestedRepositories = true)
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
    static class R2dbcConfig {
        @Bean
        ConnectionFactoryInitializer initializer(ConnectionFactory connectionFactory) {
            var initializer = new ConnectionFactoryInitializer();
            initializer.setConnectionFactory(connectionFactory);
            initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ByteArrayResource(
                    "CREATE TABLE IF NOT EXISTS \"test_entity\" (\"id\" BIGINT AUTO_INCREMENT PRIMARY KEY, \"name\" VARCHAR(255))"
                            .getBytes())));
            return initializer;
        }
    }

    @Table("test_entity")
    static class TestEntity {
        @Id
        Long id;

        String name;

        TestEntity() {
        }

        TestEntity(String name) {
            this.name = name;
        }
    }

    interface TestEntityRepository extends ReactiveCrudRepository<TestEntity, Long> {
    }

    @Service
    static class TestService {
        private final TestEntityRepository repository;

        TestService(TestEntityRepository repository) {
            this.repository = repository;
        }

        @Transactional
        @PreEnforce
        public Mono<TestEntity> create(String name) {
            return repository.save(new TestEntity(name));
        }
    }

    @Component
    static class FailingMappingHandler implements MappingConstraintHandlerProvider<TestEntity> {

        @Override
        public boolean isResponsible(Value constraint) {
            return constraint instanceof ObjectValue obj && FAIL_ON_RESULT_OBLIGATION.equals(textValue(obj, "type"));
        }

        @Override
        public Class<TestEntity> getSupportedType() {
            return TestEntity.class;
        }

        @Override
        public UnaryOperator<TestEntity> getHandler(Value constraint) {
            return entity -> {
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
    @DisplayName("when obligation handler fails on reactive R2DBC transactional method then entity not in database")
    void whenObligationHandlerFailsOnReactiveTransactionalMethod_thenEntityNotInDatabase() {
        var obligation = ObjectValue.builder().put("type", Value.of(FAIL_ON_RESULT_OBLIGATION)).build();
        var decision   = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));

        StepVerifier.create(testService.create("test")).expectError(AccessDeniedException.class).verify();
        StepVerifier.create(repository.count()).expectNext(0L).verifyComplete();
    }

}
