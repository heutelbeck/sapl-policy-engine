package io.sapl.springdatamongoreactive.sapl.queryTypes.filterEnforcement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.springdatamongoreactive.sapl.utils.ConstraintHandlerUtils;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatamongoreactive.sapl.handlers.DataManipulationHandler;
import io.sapl.springdatamongoreactive.sapl.handlers.LoggingConstraintHandlerProvider;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

public class ProceededDataFilterEnforcementPointTest {

    final TestUser aaron   = new TestUser(new ObjectId(), "Aaron", 20);
    final TestUser brian   = new TestUser(new ObjectId(), "Brian", 21);
    final TestUser cathrin = new TestUser(new ObjectId(), "Cathrin", 33);

    final Flux<TestUser> data = Flux.just(aaron, brian, cathrin);

    LoggingConstraintHandlerProvider loggingConstraintHandlerProviderMock;
    EmbeddedPolicyDecisionPoint      pdpMock;

    @BeforeEach
    public void beforeEach() {
        constraintHandlerUtilsMock           = mockStatic(ConstraintHandlerUtils.class);
        loggingConstraintHandlerProviderMock = mock(LoggingConstraintHandlerProvider.class);
        pdpMock                              = mock(EmbeddedPolicyDecisionPoint.class);
    }

    @AfterEach
    public void cleanUp() {
        constraintHandlerUtilsMock.close();
    }

    MockedStatic<ConstraintHandlerUtils> constraintHandlerUtilsMock;
    final MethodInvocationForTesting     mongoMethodInvocationTest = new MethodInvocationForTesting(
            "findAllByFirstname", new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);

    @Test
    public void when_actionWasFoundInPolicies_then_enforce() throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                .mockConstruction(DataManipulationHandler.class)) {
            // GIVEN
            var obligations     = objectMapper.readTree(
                    "[{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
            var authSub         = AuthorizationSubscription.of("", "permitTest", "");
            var enforcementData = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest, null,
                    TestUser.class, pdpMock, authSub);

            var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData);
            var dataManipulationHandler             = dataManipulationHandlerMockedConstruction.constructed().get(0);

            // WHEN
            when(pdpMock.decide(any(AuthorizationSubscription.class)))
                    .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
            when(dataManipulationHandler.manipulate(eq(obligations))).thenReturn((data) -> this.data);
            constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                    .thenReturn(JsonNodeFactory.instance.nullNode());
            constraintHandlerUtilsMock
                    .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                    .thenReturn(obligations);
            var testUserFlux = proceededDataFilterEnforcementPoint.enforce();

            // THEN
            StepVerifier.create(testUserFlux).expectNext(aaron).expectNext(brian).expectNext(cathrin).verifyComplete();

            verify(dataManipulationHandler, times(1)).manipulate(eq(obligations));
        }
    }

    @Test
    public void when_actionWasFoundInPoliciesButProceededDataCantBeConverted_then_throwRuntimeException()
            throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                .mockConstruction(DataManipulationHandler.class)) {
            // GIVEN
            var mongoMethodInvocationTest           = new MethodInvocationForTesting("findAllByFirstname",
                    new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), new Throwable());
            var obligations                         = objectMapper.readTree(
                    "[{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
            var authSub                             = AuthorizationSubscription.of("", "permitTest", "");
            var enforcementData                     = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                    null, TestUser.class, pdpMock, authSub);
            var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData);

            var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
            when(dataManipulationHandler.manipulate(eq(obligations))).thenReturn((data) -> this.data);
            constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                    .thenReturn(JsonNodeFactory.instance.nullNode());
            constraintHandlerUtilsMock
                    .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                    .thenReturn(obligations);
            when(pdpMock.decide(any(AuthorizationSubscription.class)))
                    .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));

            // WHEN
            var accessDeniedException = proceededDataFilterEnforcementPoint.enforce();

            // THEN
            StepVerifier.create(accessDeniedException).expectError(RuntimeException.class).verify();

            verify(dataManipulationHandler, times(0)).manipulate(eq(obligations));
        }
    }

    @Test
    public void when_actionWasFoundInPoliciesButDecisionIsDeny_then_throwAccessDeniedException() {
        // GIVEN
        var authSub                             = AuthorizationSubscription.of("", "denyTest", "");
        var enforcementData                     = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                null, TestUser.class, pdpMock, authSub);
        var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData);

        // WHEN
        when(pdpMock.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));
        var accessDeniedException = proceededDataFilterEnforcementPoint.enforce();

        // THEN
        StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();
    }

    @Test
    public void when_actionWasNotFoundInPolicies_then_throwAccessDeniedException() {
        // GIVEN
        var authSub                             = AuthorizationSubscription.of("", "noCorrectAction", "");
        var enforcementData                     = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                null, TestUser.class, pdpMock, authSub);
        var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData);

        // WHEN
        when(pdpMock.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));
        var accessDeniedException = proceededDataFilterEnforcementPoint.enforce();

        // THEN
        StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();
    }

}
