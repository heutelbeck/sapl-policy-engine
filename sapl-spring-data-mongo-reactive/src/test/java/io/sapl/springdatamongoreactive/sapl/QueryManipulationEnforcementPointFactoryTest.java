package io.sapl.springdatamongoreactive.sapl;

import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.queryTypes.filterEnforcement.ProceededDataFilterEnforcementPoint;
import io.sapl.springdatamongoreactive.sapl.queryTypes.annotationEnforcement.MongoAnnotationQueryManipulationEnforcementPoint;
import io.sapl.springdatamongoreactive.sapl.queryTypes.methodNameEnforcement.MongoMethodNameQueryManipulationEnforcementPoint;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
class QueryManipulationEnforcementPointFactoryTest {

    @Autowired
    QueryManipulationEnforcementPointFactory queryManipulationEnforcementPointFactory;

    @Mock
    BeanFactory beanFactoryMock;

    @Mock
    EmbeddedPolicyDecisionPoint pdpMock;

    private static final MethodInvocationForTesting mongoMethodInvocationTest = new MethodInvocationForTesting(
            "findAllByFirstname", new ArrayList<>(List.of(String.class)), null, null);

    @Test
    void createMongoAnnotationQueryManipulationEnforcementPoint() {

        try (MockedConstruction<MongoAnnotationQueryManipulationEnforcementPoint> mongoAnnotationQueryManipulationEnforcementPointMockedConstruction = Mockito
                .mockConstruction(MongoAnnotationQueryManipulationEnforcementPoint.class)) {

            // GIVEN
            var authSub         = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
            var enforcementData = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest, beanFactoryMock,
                    TestUser.class, pdpMock, authSub);

            // WHEN
            var result = queryManipulationEnforcementPointFactory
                    .createMongoAnnotationQueryManipulationEnforcementPoint(enforcementData);

            // THEN
            Assertions.assertNotNull(
                    mongoAnnotationQueryManipulationEnforcementPointMockedConstruction.constructed().get(0));
            Assertions.assertEquals(result.getClass(), MongoAnnotationQueryManipulationEnforcementPoint.class);
        }
    }

    @Test
    void createMongoMethodNameQueryManipulationEnforcementPoint() {

        try (MockedConstruction<MongoMethodNameQueryManipulationEnforcementPoint> mongoMethodNameQueryManipulationEnforcementPointMockedConstruction = Mockito
                .mockConstruction(MongoMethodNameQueryManipulationEnforcementPoint.class)) {

            // GIVEN
            var authSub         = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
            var enforcementData = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest, beanFactoryMock,
                    TestUser.class, pdpMock, authSub);

            // WHEN
            var result = queryManipulationEnforcementPointFactory
                    .createMongoMethodNameQueryManipulationEnforcementPoint(enforcementData);

            // THEN
            Assertions.assertNotNull(
                    mongoMethodNameQueryManipulationEnforcementPointMockedConstruction.constructed().get(0));
            Assertions.assertEquals(result.getClass(), MongoMethodNameQueryManipulationEnforcementPoint.class);
        }
    }

    @Test
    void createProceededDataFilterEnforcementPoint() {

        try (MockedConstruction<ProceededDataFilterEnforcementPoint> proceededDataFilterEnforcementPointMockedConstruction = Mockito
                .mockConstruction(ProceededDataFilterEnforcementPoint.class)) {

            // GIVEN
            var authSub         = AuthorizationSubscription.of("subject", "permitTest", "resource", "environment");
            var enforcementData = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest, beanFactoryMock,
                    TestUser.class, pdpMock, authSub);

            // WHEN
            var result = queryManipulationEnforcementPointFactory
                    .createProceededDataFilterEnforcementPoint(enforcementData);

            // THEN
            Assertions.assertNotNull(proceededDataFilterEnforcementPointMockedConstruction.constructed().get(0));
            Assertions.assertEquals(result.getClass(), ProceededDataFilterEnforcementPoint.class);
        }
    }
}
