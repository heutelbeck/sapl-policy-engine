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
import io.sapl.spring.method.blocking.TransactionalEnforcementTests.Application;
import io.sapl.spring.method.blocking.TransactionalEnforcementTests.FailingMappingHandler;
import io.sapl.spring.method.blocking.TransactionalEnforcementTests.MethodSecurityConfiguration;
import io.sapl.spring.method.blocking.TransactionalEnforcementTests.TestService;
import io.sapl.spring.method.metadata.PreEnforce;
import jakarta.persistence.Entity;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { Application.class, MethodSecurityConfiguration.class, TestService.class,
        FailingMappingHandler.class }, properties = { "spring.main.web-application-type=servlet",
                "io.sapl.pdp.embedded.enabled=false", "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.autoconfigure.exclude=de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration" })
class TransactionalEnforcementTests {

    private static final String FAIL_ON_RESULT_OBLIGATION = "fail-on-result";

    @MockitoBean
    PolicyDecisionPoint pdp;

    @Autowired
    TestEntityRepository repository;

    @Autowired
    TestService testService;

    @SpringBootApplication(exclude = R2dbcAutoConfiguration.class)
    @EntityScan(basePackageClasses = TransactionalEnforcementTests.class)
    @EnableJpaRepositories(basePackageClasses = TransactionalEnforcementTests.class, considerNestedRepositories = true)
    static class Application {
        public static void main(String... args) {
            SpringApplication.run(Application.class, args);
        }
    }

    @TestConfiguration
    @EnableSaplMethodSecurity
    static class MethodSecurityConfiguration {
    }

    @Entity
    @Table(name = "test_entity")
    static class TestEntity {
        @Id
        @GeneratedValue
        Long id;

        String name;

        TestEntity() {
        }

        TestEntity(String name) {
            this.name = name;
        }
    }

    interface TestEntityRepository extends JpaRepository<TestEntity, Long> {
    }

    @Service
    static class TestService {
        private final TestEntityRepository repository;

        TestService(TestEntityRepository repository) {
            this.repository = repository;
        }

        @Transactional
        @PreEnforce
        public TestEntity create(String name) {
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
    @DisplayName("when obligation handler fails after transactional method succeeds then entity not in database")
    void whenObligationHandlerFailsAfterTransactionalMethodSucceeds_thenEntityNotInDatabase() {
        var obligation = ObjectValue.builder().put("type", Value.of(FAIL_ON_RESULT_OBLIGATION)).build();
        var decision   = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligation), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
        when(pdp.decideOnceBlocking(any(AuthorizationSubscription.class))).thenReturn(decision);

        assertThatThrownBy(() -> testService.create("test")).isInstanceOf(AccessDeniedException.class);
        assertThat(repository.count()).isZero();
    }

}
