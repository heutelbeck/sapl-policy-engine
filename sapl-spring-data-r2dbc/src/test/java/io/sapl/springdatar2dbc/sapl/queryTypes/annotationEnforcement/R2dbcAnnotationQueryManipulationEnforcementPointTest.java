package io.sapl.springdatar2dbc.sapl.queryTypes.annotationEnforcement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.springdatar2dbc.database.MethodInvocationForTesting;
import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.database.Role;
import io.sapl.springdatar2dbc.sapl.utils.ConstraintHandlerUtils;
import io.sapl.springdatar2dbc.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatar2dbc.sapl.QueryManipulationExecutor;
import io.sapl.springdatar2dbc.sapl.handlers.DataManipulationHandler;
import io.sapl.springdatar2dbc.sapl.handlers.LoggingConstraintHandlerProvider;
import io.sapl.springdatar2dbc.sapl.handlers.R2dbcQueryManipulationObligationProvider;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
class R2dbcAnnotationQueryManipulationEnforcementPointTest {

    final static ObjectMapper objectMapper = new ObjectMapper();
    static JsonNode           obligations;
    static JsonNode           r2dbcQueryManipulation;
    static JsonNode           condition;
    static JsonNode           conditionWithConjunction;
    static JsonNode           r2dbcQueryManipulationWithConjunction;

    final Person       malinda       = new Person(1, "Malinda", "Perrot", 53, Role.ADMIN, true);
    final Flux<Person> malindaAsFlux = Flux.just(malinda);

    @Mock
    EmbeddedPolicyDecisionPoint pdpMock;

    @MockBean
    LoggingConstraintHandlerProvider loggingConstraintHandlerProviderMock;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    R2dbcEntityTemplate r2dbcEntityTemplateMock;

    @Mock
    BeanFactory beanFactoryMock;

    @Mock
    Flux<Map<String, Object>> fluxMap;

    MockedStatic<ConstraintHandlerUtils>           constraintHandlerUtilsMock;
    MockedStatic<QueryAnnotationParameterResolver> queryAnnotationParameterResolverMockedStatic;
    MockedStatic<QueryManipulationExecutor>        queryExecutorMock;

    @BeforeAll
    public static void setUp() throws JsonProcessingException {
        obligations                           = objectMapper.readTree(
                "[{\"type\":\"r2dbcQueryManipulation\",\"condition\":\"firstname IN('Aaron', 'Cathrin')\"},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        r2dbcQueryManipulation                = objectMapper
                .readTree("{\"type\":\"r2dbcQueryManipulation\",\"condition\":\"firstname IN('Aaron', 'Cathrin')\"}");
        condition                             = r2dbcQueryManipulation.get("condition");
        r2dbcQueryManipulationWithConjunction = objectMapper.readTree(
                "{\"type\":\"r2dbcQueryManipulation\",\"condition\":\"AND firstname IN('Aaron', 'Cathrin')\"}");
        conditionWithConjunction              = r2dbcQueryManipulationWithConjunction.get("condition");
    }

    @BeforeEach
    public void initBeforeEach() {
        constraintHandlerUtilsMock                   = mockStatic(ConstraintHandlerUtils.class);
        queryAnnotationParameterResolverMockedStatic = mockStatic(QueryAnnotationParameterResolver.class);
        queryExecutorMock                            = mockStatic(QueryManipulationExecutor.class);
    }

    @AfterEach
    public void cleanUp() {
        constraintHandlerUtilsMock.close();
        queryAnnotationParameterResolverMockedStatic.close();
        queryExecutorMock.close();
    }

    @Test
    void when_thereAreConditionsInTheDecision_then_enforce(CapturedOutput output) {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                // GIVEN
                var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findAllByAgeAfter",
                        new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of(30)), null);
                var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData           = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                        beanFactoryMock, Person.class, pdpMock, authSub);
                var expectedLoggerMessage     = "Manipulated condition: [firstname IN('Aaron', 'Cathrin') AND age > 30]";

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                queryExecutorMock.when(
                        () -> QueryManipulationExecutor.execute(anyString(), any(BeanFactory.class), eq(Person.class)))
                        .thenReturn(fluxMap);
                queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)))
                        .thenReturn("SELECT * FROM testUser WHERE age > 30");
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);

                var r2bcAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                        enforcementData);

                var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                when(dataManipulationHandler.manipulate(any(JsonNode.class))).thenReturn(obligations -> malindaAsFlux);
                when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> malindaAsFlux);

                var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(r2dbcQueryManipulationObligationProviderMock.isResponsible(any(JsonNode.class)))
                        .thenReturn(Boolean.TRUE);
                when(r2dbcQueryManipulationObligationProviderMock.getObligation(any(JsonNode.class)))
                        .thenReturn(r2dbcQueryManipulation);
                when(r2dbcQueryManipulationObligationProviderMock.getCondition(any(JsonNode.class)))
                        .thenReturn(condition);

                var result = r2bcAnnotationQueryManipulationEnforcementPoint.enforce();

                // THEN
                StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                Assertions.assertTrue(output.getOut().contains(expectedLoggerMessage));
                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).getObligation(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).getCondition(eq(r2dbcQueryManipulation));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)),
                        times(1));
            }
        }
    }

    @Test
    void when_thereAreConditionsInTheDecisionButWithAnAndConjunction_then_enforce(CapturedOutput output) {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                // GIVEN
                var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest",
                        new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(30, '2')), null);
                var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData           = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                        beanFactoryMock, Person.class, pdpMock, authSub);
                var expectedLoggerMessage     = "Manipulated condition: [firstname IN('Aaron', 'Cathrin') AND age = 30 AND id = '2']";

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                queryExecutorMock.when(
                        () -> QueryManipulationExecutor.execute(anyString(), any(BeanFactory.class), eq(Person.class)))
                        .thenReturn(fluxMap);
                queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)))
                        .thenReturn("SELECT * FROM testUser WHERE age = 30 AND id = '2'");
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);

                var r2bcAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                        enforcementData);

                var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                when(dataManipulationHandler.manipulate(eq(obligations))).thenReturn(obligations -> Flux.just(malinda));
                when(dataManipulationHandler.toDomainObject()).thenCallRealMethod();

                var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(r2dbcQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                        .thenReturn(Boolean.TRUE);
                when(r2dbcQueryManipulationObligationProviderMock.getObligation(eq(obligations)))
                        .thenReturn(r2dbcQueryManipulationWithConjunction);
                when(r2dbcQueryManipulationObligationProviderMock
                        .getCondition(eq(r2dbcQueryManipulationWithConjunction))).thenReturn(conditionWithConjunction);

                var result = r2bcAnnotationQueryManipulationEnforcementPoint.enforce();

                // THEN
                StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                Assertions.assertTrue(output.getOut().contains(expectedLoggerMessage));
                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).getObligation(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, times(1))
                        .getCondition(eq(r2dbcQueryManipulationWithConjunction));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)),
                        times(1));
            }
        }
    }

    @Test
    void when_decisionIsNotPermit_then_throwAccessDeniedException() {
        // GIVEN
        var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest",
                new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(30, '2')), null);
        var authSub                   = AuthorizationSubscription.of("subject", "denyTest", "resource", "environment");
        var enforcementData           = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                beanFactoryMock, Person.class, pdpMock, authSub);

        // WHEN
        when(pdpMock.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));

        var mongoAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                enforcementData);
        var accessDeniedException                            = mongoAnnotationQueryManipulationEnforcementPoint
                .enforce();

        // THEN
        StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();

        constraintHandlerUtilsMock.verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)),
                times(1));
        constraintHandlerUtilsMock.verify(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)),
                times(0));
    }

    @Test
    void when_r2dbcQueryManipulationObligationIsResponsibleIsFalse_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandler = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                // GIVEN
                var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest",
                        new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(30, '2')), null);
                var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData           = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                        beanFactoryMock, Person.class, pdpMock, authSub);
                var expectedQuery             = "SELECT * FROM testUser WHERE age = 30 AND id = '2'";

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)))
                        .thenReturn(expectedQuery);
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);

                var r2bcAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                        enforcementData);

                when(dataManipulationHandler.constructed().get(0).manipulate(eq(obligations)))
                        .thenReturn(obligations -> malindaAsFlux);
                when(dataManipulationHandler.constructed().get(0).toDomainObject())
                        .thenReturn(obligations -> malindaAsFlux);

                var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(r2dbcQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                        .thenReturn(Boolean.FALSE);

                var result = r2bcAnnotationQueryManipulationEnforcementPoint.enforce();

                // THEN
                StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                verifyNoMoreInteractions(r2dbcEntityTemplateMock.getDatabaseClient());
                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, times(0)).getObligation(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, times(0)).getCondition(eq(r2dbcQueryManipulation));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)),
                        times(1));
            }
        }
    }

    @Test
    void when_r2dbcQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandler = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                // GIVEN
                var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findUserTest",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")),
                        Mono.just(malinda));
                var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData           = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                        beanFactoryMock, Person.class, pdpMock, authSub);
                var expectedQuery             = "SELECT * FROM testUser WHERE age = 30 AND id = '2'";

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)))
                        .thenReturn(expectedQuery);
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);

                var r2bcAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                        enforcementData);

                when(dataManipulationHandler.constructed().get(0).manipulate(eq(obligations)))
                        .thenReturn(obligations -> malindaAsFlux);
                when(dataManipulationHandler.constructed().get(0).toDomainObject())
                        .thenReturn(obligations -> malindaAsFlux);

                var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(r2dbcQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                        .thenReturn(Boolean.FALSE);

                var result = r2bcAnnotationQueryManipulationEnforcementPoint.enforce();

                // THEN
                StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, times(0)).getObligation(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, times(0)).getCondition(eq(r2dbcQueryManipulation));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)),
                        times(1));
            }
        }
    }

    @Test
    void when_mongoQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_throwThrowable() {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandler = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                // GIVEN
                var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findUserTest",
                        new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), new Throwable());
                var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData           = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                        beanFactoryMock, Person.class, pdpMock, authSub);
                var expectedQuery             = "SELECT * FROM testUser WHERE age = 30 AND id = '2'";

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)))
                        .thenReturn(expectedQuery);
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);

                var r2bcAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                        enforcementData);

                when(dataManipulationHandler.constructed().get(0).manipulate(eq(obligations)))
                        .thenReturn(obligations -> malindaAsFlux);
                when(dataManipulationHandler.constructed().get(0).toDomainObject())
                        .thenReturn(obligations -> malindaAsFlux);

                var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(r2dbcQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                        .thenReturn(Boolean.FALSE);

                var throwableException = r2bcAnnotationQueryManipulationEnforcementPoint.enforce();

                // THEN
                StepVerifier.create(throwableException).expectError(Throwable.class).verify();

                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, times(0)).getObligation(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, times(0)).getCondition(eq(r2dbcQueryManipulation));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)),
                        times(1));
            }
        }
    }

    @Test
    void when_thereAreConditionsInTheDecisionButNoWhereClauseInOriginalQuery_then_enforce(CapturedOutput output) {
        try (MockedConstruction<R2dbcQueryManipulationObligationProvider> r2dbcQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(R2dbcQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                // GIVEN
                var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest", new ArrayList<>(),
                        new ArrayList<>(), null);
                var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                        "environment");
                var enforcementData           = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                        beanFactoryMock, Person.class, pdpMock, authSub);
                var expectedLoggerMessage     = "Manipulated condition: [firstname IN('Aaron', 'Cathrin')]";

                // WHEN
                when(pdpMock.decide(any(AuthorizationSubscription.class)))
                        .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                queryExecutorMock.when(
                        () -> QueryManipulationExecutor.execute(anyString(), any(BeanFactory.class), eq(Person.class)))
                        .thenReturn(fluxMap);
                queryAnnotationParameterResolverMockedStatic.when(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)))
                        .thenReturn("SELECT * FROM testUser");
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                        .thenReturn(JsonNodeFactory.instance.nullNode());
                constraintHandlerUtilsMock
                        .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                        .thenReturn(obligations);

                var r2bcAnnotationQueryManipulationEnforcementPoint = new R2dbcAnnotationQueryManipulationEnforcementPoint<>(
                        enforcementData);

                var dataManipulationHandler = dataManipulationHandlerMockedConstruction.constructed().get(0);
                when(dataManipulationHandler.manipulate(any(JsonNode.class))).thenReturn(obligations -> malindaAsFlux);
                when(dataManipulationHandler.toDomainObject()).thenReturn(obligations -> malindaAsFlux);

                var r2dbcQueryManipulationObligationProviderMock = r2dbcQueryManipulationObligationProviderMockedConstruction
                        .constructed().get(0);
                when(r2dbcQueryManipulationObligationProviderMock.isResponsible(any(JsonNode.class)))
                        .thenReturn(Boolean.TRUE);
                when(r2dbcQueryManipulationObligationProviderMock.getObligation(any(JsonNode.class)))
                        .thenReturn(r2dbcQueryManipulation);
                when(r2dbcQueryManipulationObligationProviderMock.getCondition(any(JsonNode.class)))
                        .thenReturn(condition);

                var result = r2bcAnnotationQueryManipulationEnforcementPoint.enforce();

                // THEN
                StepVerifier.create(result).expectNext(malinda).expectComplete().verify();

                Assertions.assertTrue(output.getOut().contains(expectedLoggerMessage));
                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).getObligation(eq(obligations));
                verify(r2dbcQueryManipulationObligationProviderMock, times(1)).getCondition(eq(r2dbcQueryManipulation));
                constraintHandlerUtilsMock
                        .verify(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                constraintHandlerUtilsMock.verify(
                        () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                queryAnnotationParameterResolverMockedStatic.verify(() -> QueryAnnotationParameterResolver
                        .resolveBoundedMethodParametersAndAnnotationParameters(any(Method.class), any(Object[].class)),
                        times(1));
            }
        }
    }
}
