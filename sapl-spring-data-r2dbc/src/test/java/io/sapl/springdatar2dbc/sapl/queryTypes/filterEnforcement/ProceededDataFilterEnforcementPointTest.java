package io.sapl.springdatar2dbc.sapl.queryTypes.filterEnforcement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.springdatar2dbc.database.MethodInvocationForTesting;
import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.database.Role;
import io.sapl.springdatar2dbc.sapl.utils.ConstraintHandlerUtils;
import io.sapl.springdatar2dbc.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatar2dbc.sapl.handlers.DataManipulationHandler;
import io.sapl.springdatar2dbc.sapl.handlers.LoggingConstraintHandlerProvider;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import org.junit.jupiter.api.*;
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

    final Person malinda = new Person(1, "Malinda", "Perrot", 53, Role.ADMIN, true);
    final Person emerson = new Person(2, "Emerson", "Rowat", 82, Role.USER, false);
    final Person yul     = new Person(3, "Yul", "Barukh", 79, Role.USER, true);

    final Flux<Person> data = Flux.just(malinda, emerson, yul);

    final ObjectMapper objectMapper = new ObjectMapper();

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

    private final MethodInvocationForTesting r2dbcMethodInvocationTest = new MethodInvocationForTesting(
            "findAllByFirstname", new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);

    @Test
    public void when_actionWasFoundInPolicies_then_enforce() throws JsonProcessingException {
        try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                .mockConstruction(DataManipulationHandler.class)) {
            // GIVEN
            var obligations     = objectMapper.readTree(
                    "[{\"type\":\"r2dbcQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
            var authSub         = AuthorizationSubscription.of("", "permitTest", "");
            var enforcementData = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest, null, Person.class,
                    pdpMock, authSub);

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
            StepVerifier.create(testUserFlux).expectNext(malinda).expectNext(emerson).expectNext(yul).verifyComplete();

            verify(dataManipulationHandler, times(1)).manipulate(eq(obligations));
        }
    }

    @Test
    public void when_actionWasFoundInPoliciesButProceededDataCantBeConverted_then_throwRuntimeException()
            throws JsonProcessingException {
        try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                .mockConstruction(DataManipulationHandler.class)) {
            // GIVEN
            MethodInvocationForTesting r2dbcMethodInvocationTest           = new MethodInvocationForTesting(
                    "findAllByFirstname", new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")),
                    new Throwable());
            var                        obligations                         = objectMapper.readTree(
                    "[{\"type\":\"r2dbcQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
            var                        authSub                             = AuthorizationSubscription.of("",
                    "permitTest", "");
            var                        enforcementData                     = new QueryManipulationEnforcementData<>(
                    r2dbcMethodInvocationTest, null, Person.class, pdpMock, authSub);
            var                        proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(
                    enforcementData);

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
        var enforcementData                     = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                null, Person.class, pdpMock, authSub);
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
        var enforcementData                     = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                null, Person.class, pdpMock, authSub);
        var proceededDataFilterEnforcementPoint = new ProceededDataFilterEnforcementPoint<>(enforcementData);

        // WHEN
        when(pdpMock.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));
        var accessDeniedException = proceededDataFilterEnforcementPoint.enforce();

        // THEN
        StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();
    }

}
