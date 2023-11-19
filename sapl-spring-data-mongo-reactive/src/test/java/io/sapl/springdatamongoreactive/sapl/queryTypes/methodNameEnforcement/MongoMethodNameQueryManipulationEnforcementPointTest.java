package io.sapl.springdatamongoreactive.sapl.queryTypes.methodNameEnforcement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.springdatamongoreactive.sapl.utils.ConstraintHandlerUtils;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.MongoDbRepositoryTest;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.QueryManipulationEnforcementData;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.springdatamongoreactive.sapl.handlers.DataManipulationHandler;
import io.sapl.springdatamongoreactive.sapl.handlers.LoggingConstraintHandlerProvider;
import io.sapl.springdatamongoreactive.sapl.handlers.MongoQueryManipulationObligationProvider;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@SpringBootTest
class MongoMethodNameQueryManipulationEnforcementPointTest {

    static final ObjectMapper objectMapper = new ObjectMapper();
    static JsonNode           obligations;
    static JsonNode           mongoQueryManipulation;
    static JsonNode           conditions;

    final TestUser aaron   = new TestUser(new ObjectId(), "Aaron", 20);
    final TestUser brian   = new TestUser(new ObjectId(), "Brian", 21);
    final TestUser cathrin = new TestUser(new ObjectId(), "Cathrin", 33);

    final Flux<TestUser> data = Flux.just(aaron, brian, cathrin);

    @Autowired
    MongoDbRepositoryTest mongoDbRepositoryTest;

    @MockBean
    LoggingConstraintHandlerProvider loggingConstraintHandlerProviderMock;

    @Mock
    ReactiveMongoTemplate reactiveMongoTemplateMock;

    @Mock
    BeanFactory beanFactoryMock;

    @Mock
    EmbeddedPolicyDecisionPoint pdpMock;

    MockedStatic<ConstraintHandlerUtils> constraintHandlerUtilsMock;

    @BeforeAll
    public static void setUp() throws JsonProcessingException {
        obligations            = objectMapper.readTree(
                "[{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        mongoQueryManipulation = objectMapper
                .readTree("{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'age':  {'gt': 30 }}\"]}");
        conditions             = mongoQueryManipulation.get("conditions");
    }

    @BeforeEach
    public void beforeEach() {
        constraintHandlerUtilsMock = mockStatic(ConstraintHandlerUtils.class);
    }

    @AfterEach
    public void cleanUp() {
        constraintHandlerUtilsMock.close();
    }

    @Test
    void when_thereAreConditionsInTheDecision_then_enforce() {
        try (MockedConstruction<MongoQueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(MongoQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<SaplPartTreeCriteriaCreator> saplPartTreeCriteriaCreatorMockedConstruction = Mockito
                        .mockConstruction(SaplPartTreeCriteriaCreator.class)) {

                    // GIVEN
                    var expectedQuery             = new BasicQuery("{'firstname': 'Cathrin', 'age':  {'gt': 30 }}");
                    var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstname",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData           = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                            beanFactoryMock, TestUser.class, pdpMock, authSub);

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    when(beanFactoryMock.getBean(eq(ReactiveMongoTemplate.class)))
                            .thenReturn(reactiveMongoTemplateMock);
                    when(reactiveMongoTemplateMock.find(any(BasicQuery.class), any())).thenReturn(Flux.just(cathrin));
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligations);

                    var mongoMethodNameQueryManipulationEnforcementPoint = new MongoMethodNameQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var mongoQueryManipulationObligationProviderMock = mongoQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    var dataManipulationHandlerMock                  = dataManipulationHandlerMockedConstruction
                            .constructed().get(0);
                    var saplPartTreeCriteriaCreatorMock              = saplPartTreeCriteriaCreatorMockedConstruction
                            .constructed().get(0);

                    when(mongoQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                            .thenReturn(Boolean.TRUE);
                    when(mongoQueryManipulationObligationProviderMock.getObligation(eq(obligations)))
                            .thenReturn(mongoQueryManipulation);
                    when(mongoQueryManipulationObligationProviderMock.getConditions(eq(mongoQueryManipulation)))
                            .thenReturn(conditions);
                    when(dataManipulationHandlerMock.manipulate(eq(obligations)))
                            .thenReturn(obligations -> Flux.just(cathrin));
                    when(saplPartTreeCriteriaCreatorMock.createManipulatedQuery(eq(conditions)))
                            .thenReturn(expectedQuery);

                    // THEN
                    var result = mongoMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

                    verify(reactiveMongoTemplateMock, times(1)).find(eq(expectedQuery), eq(TestUser.class));
                    verify(saplPartTreeCriteriaCreatorMock, times(1)).createManipulatedQuery(eq(conditions));
                    verify(dataManipulationHandlerMock, times(1)).manipulate(eq(obligations));
                    verify(mongoQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                    verify(mongoQueryManipulationObligationProviderMock, times(1)).getObligation(eq(obligations));
                    verify(mongoQueryManipulationObligationProviderMock, times(1))
                            .getConditions(eq(mongoQueryManipulation));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                }
            }
        }
    }

    @Test
    void when_decisionIsNotPermit_then_throwAccessDeniedException() {
        try (MockedConstruction<MongoQueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(MongoQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<SaplPartTreeCriteriaCreator> saplPartTreeCriteriaCreatorMockedConstruction = Mockito
                        .mockConstruction(SaplPartTreeCriteriaCreator.class)) {

                    // GIVEN
                    var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstname",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                    var authSub                   = AuthorizationSubscription.of("subject", "denyTest", "resource",
                            "environment");
                    var enforcementData           = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                            beanFactoryMock, TestUser.class, pdpMock, authSub);

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.DENY)));
                    when(beanFactoryMock.getBean(eq(ReactiveMongoTemplate.class)))
                            .thenReturn(reactiveMongoTemplateMock);

                    var mongoMethodNameQueryManipulationEnforcementPoint = new MongoMethodNameQueryManipulationEnforcementPoint<>(
                            enforcementData);
                    var accessDeniedException                            = mongoMethodNameQueryManipulationEnforcementPoint
                            .enforce();

                    // THEN
                    StepVerifier.create(accessDeniedException).expectError(AccessDeniedException.class).verify();

                    Assertions.assertNotNull(saplPartTreeCriteriaCreatorMockedConstruction.constructed().get(0));
                    Assertions.assertNotNull(dataManipulationHandlerMockedConstruction.constructed().get(0));
                    Assertions.assertNotNull(
                            mongoQueryManipulationObligationProviderMockedConstruction.constructed().get(0));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(0));
                }
            }
        }
    }

    @Test
    void when_mongoQueryManipulationObligationIsResponsibleIsFalse_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<MongoQueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(MongoQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<SaplPartTreeCriteriaCreator> saplPartTreeCriteriaCreatorMockedConstruction = Mockito
                        .mockConstruction(SaplPartTreeCriteriaCreator.class)) {

                    // GIVEN
                    var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstname",
                            new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), null);
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData           = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                            beanFactoryMock, TestUser.class, pdpMock, authSub);

                    // WHEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    when(beanFactoryMock.getBean(eq(ReactiveMongoTemplate.class)))
                            .thenReturn(reactiveMongoTemplateMock);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligations);

                    var mongoMethodNameQueryManipulationEnforcementPoint = new MongoMethodNameQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var mongoQueryManipulationObligationProviderMock = mongoQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    var dataManipulationHandlerMock                  = dataManipulationHandlerMockedConstruction
                            .constructed().get(0);
                    var saplPartTreeCriteriaCreatorMock              = saplPartTreeCriteriaCreatorMockedConstruction
                            .constructed().get(0);

                    when(mongoQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                            .thenReturn(Boolean.FALSE);
                    when(dataManipulationHandlerMock.manipulate(eq(obligations))).thenReturn(obligations -> data);

                    // THEN
                    var result = mongoMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(result).expectNext(aaron).expectNext(brian).expectNext(cathrin).expectComplete()
                            .verify();

                    verify(reactiveMongoTemplateMock, never()).find(any(Query.class), eq(TestUser.class));
                    verify(saplPartTreeCriteriaCreatorMock, never()).createManipulatedQuery(eq(conditions));
                    verify(dataManipulationHandlerMock, times(1)).manipulate(eq(obligations));
                    verify(mongoQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                    verify(mongoQueryManipulationObligationProviderMock, never()).getObligation(eq(obligations));
                    verify(mongoQueryManipulationObligationProviderMock, never())
                            .getConditions(eq(mongoQueryManipulation));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                }
            }
        }
    }

    @Test
    void when_mongoQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_proceedWithoutQueryManipulation() {
        try (MockedConstruction<MongoQueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(MongoQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<SaplPartTreeCriteriaCreator> saplPartTreeCriteriaCreatorMockedConstruction = Mockito
                        .mockConstruction(SaplPartTreeCriteriaCreator.class)) {

                    // GIVEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    var mongoMethodInvocationTest = new MethodInvocationForTesting("findByAge",
                            new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of(30)), Mono.just(cathrin));
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData           = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                            beanFactoryMock, TestUser.class, pdpMock, authSub);

                    // WHEN
                    when(beanFactoryMock.getBean(eq(ReactiveMongoTemplate.class)))
                            .thenReturn(reactiveMongoTemplateMock);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligations);

                    var mongoMethodNameQueryManipulationEnforcementPoint = new MongoMethodNameQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var mongoQueryManipulationObligationProviderMock = mongoQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    var dataManipulationHandlerMock                  = dataManipulationHandlerMockedConstruction
                            .constructed().get(0);
                    var saplPartTreeCriteriaCreatorMock              = saplPartTreeCriteriaCreatorMockedConstruction
                            .constructed().get(0);

                    when(mongoQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                            .thenReturn(Boolean.FALSE);
                    when(dataManipulationHandlerMock.manipulate(eq(obligations)))
                            .thenReturn(obligations -> Flux.just(cathrin));

                    // THEN
                    var result = mongoMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(result).expectNext(cathrin).expectComplete().verify();

                    verify(reactiveMongoTemplateMock, never()).find(any(Query.class), eq(TestUser.class));
                    verify(saplPartTreeCriteriaCreatorMock, never()).createManipulatedQuery(eq(conditions));
                    verify(dataManipulationHandlerMock, times(1)).manipulate(eq(obligations));
                    verify(mongoQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                    verify(mongoQueryManipulationObligationProviderMock, never()).getObligation(eq(obligations));
                    verify(mongoQueryManipulationObligationProviderMock, never())
                            .getConditions(eq(mongoQueryManipulation));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                }
            }
        }
    }

    @Test
    void when_mongoQueryManipulationObligationIsResponsibleIsFalseAndReturnTypeIsMono_then_throwThrowable() {
        try (MockedConstruction<MongoQueryManipulationObligationProvider> mongoQueryManipulationObligationProviderMockedConstruction = Mockito
                .mockConstruction(MongoQueryManipulationObligationProvider.class)) {
            try (MockedConstruction<DataManipulationHandler> dataManipulationHandlerMockedConstruction = Mockito
                    .mockConstruction(DataManipulationHandler.class)) {
                try (MockedConstruction<SaplPartTreeCriteriaCreator> saplPartTreeCriteriaCreatorMockedConstruction = Mockito
                        .mockConstruction(SaplPartTreeCriteriaCreator.class)) {

                    // GIVEN
                    when(pdpMock.decide(any(AuthorizationSubscription.class)))
                            .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT)));
                    var mongoMethodInvocationTest = new MethodInvocationForTesting("findByAge",
                            new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of(30)), new Throwable());
                    var authSub                   = AuthorizationSubscription.of("subject", "permitTest", "resource",
                            "environment");
                    var enforcementData           = new QueryManipulationEnforcementData<>(mongoMethodInvocationTest,
                            beanFactoryMock, TestUser.class, pdpMock, authSub);

                    // WHEN
                    when(beanFactoryMock.getBean(eq(ReactiveMongoTemplate.class)))
                            .thenReturn(reactiveMongoTemplateMock);
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)))
                            .thenReturn(JsonNodeFactory.instance.nullNode());
                    constraintHandlerUtilsMock
                            .when(() -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)))
                            .thenReturn(obligations);

                    var mongoMethodNameQueryManipulationEnforcementPoint = new MongoMethodNameQueryManipulationEnforcementPoint<>(
                            enforcementData);

                    var mongoQueryManipulationObligationProviderMock = mongoQueryManipulationObligationProviderMockedConstruction
                            .constructed().get(0);
                    var dataManipulationHandlerMock                  = dataManipulationHandlerMockedConstruction
                            .constructed().get(0);
                    var saplPartTreeCriteriaCreatorMock              = saplPartTreeCriteriaCreatorMockedConstruction
                            .constructed().get(0);

                    when(mongoQueryManipulationObligationProviderMock.isResponsible(eq(obligations)))
                            .thenReturn(Boolean.FALSE);

                    // THEN
                    var throwableException = mongoMethodNameQueryManipulationEnforcementPoint.enforce();

                    StepVerifier.create(throwableException).expectError(Throwable.class).verify();

                    verify(reactiveMongoTemplateMock, never()).find(any(Query.class), eq(TestUser.class));
                    verify(saplPartTreeCriteriaCreatorMock, never()).createManipulatedQuery(eq(conditions));
                    verify(dataManipulationHandlerMock, never()).manipulate(eq(obligations));
                    verify(mongoQueryManipulationObligationProviderMock, times(1)).isResponsible(eq(obligations));
                    verify(mongoQueryManipulationObligationProviderMock, never()).getObligation(eq(obligations));
                    verify(mongoQueryManipulationObligationProviderMock, never())
                            .getConditions(eq(mongoQueryManipulation));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getAdvices(any(AuthorizationDecision.class)), times(1));
                    constraintHandlerUtilsMock.verify(
                            () -> ConstraintHandlerUtils.getObligations(any(AuthorizationDecision.class)), times(1));
                }
            }
        }
    }
}
