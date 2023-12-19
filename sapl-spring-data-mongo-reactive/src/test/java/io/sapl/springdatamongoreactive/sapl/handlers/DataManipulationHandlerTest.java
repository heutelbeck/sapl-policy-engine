package io.sapl.springdatamongoreactive.sapl.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.springdatamongoreactive.sapl.utils.ConstraintHandlerUtils;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

class DataManipulationHandlerTest {

    DataManipulationHandler<TestUser>    dataManipulationHandler;
    MockedStatic<ConstraintHandlerUtils> constraintHandlerUtilsMock;

    final TestUser aaron   = new TestUser(new ObjectId(), "Aaron", 20);
    final TestUser brian   = new TestUser(new ObjectId(), "Brian", 21);
    final TestUser cathrin = new TestUser(new ObjectId(), "Cathrin", 23);

    final Flux<TestUser> data = Flux.just(aaron, brian, cathrin);

    static final ObjectMapper objectMapper = new ObjectMapper();
    static JsonNode           obligations;
    static JsonNode           jsonContentFilterPredicate;
    static JsonNode           filterJsonContent;

    @BeforeAll
    public static void initBeforeAll() throws JsonProcessingException {
        obligations                = objectMapper.readTree(
                "[{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        jsonContentFilterPredicate = objectMapper.readTree(
                "{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.firstname\",\"value\":\"Aaron\"}]}");
        filterJsonContent          = objectMapper.readTree(
                "{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2},{\"type\":\"delete\",\"path\":\"$.age\"}]}");
    }

    @BeforeEach
    public void initBeforeEach() {
        constraintHandlerUtilsMock = mockStatic(ConstraintHandlerUtils.class);
        dataManipulationHandler    = new DataManipulationHandler<>(TestUser.class);
    }

    @AfterEach
    public void cleanUp() {
        constraintHandlerUtilsMock.close();
    }

    @Test
    void when_NoFilteringOrTransformationIsDesired_then_manipulate() {
        // GIVEN

        // WHEN
        constraintHandlerUtilsMock.when(
                () -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), anyString()))
                .thenReturn(JsonNodeFactory.instance.nullNode());

        var result = dataManipulationHandler.manipulate(obligations).apply(data);

        // THEN
        StepVerifier.create(result).expectNextMatches(testUser -> assertTwoTestUsers(testUser, aaron))
                .expectNextMatches(testUser -> assertTwoTestUsers(testUser, brian))
                .expectNextMatches(testUser -> assertTwoTestUsers(testUser, cathrin)).expectComplete().verify();

        constraintHandlerUtilsMock.verify(
                () -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), anyString()),
                times(2));
    }

    @Test
    void when_FilterPredicateIsDesired_then_manipulate() {
        // GIVEN

        // WHEN
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("mongoQueryManipulation")))
                .thenReturn(JsonNodeFactory.instance.nullNode());
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("filterJsonContent")))
                .thenReturn(JsonNodeFactory.instance.nullNode());
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils
                .getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("jsonContentFilterPredicate")))
                .thenReturn(jsonContentFilterPredicate);

        var result = dataManipulationHandler.manipulate(obligations).apply(data);

        // THEN
        StepVerifier.create(result).expectNextMatches(testUser -> assertTwoTestUsers(testUser, aaron)).expectComplete()
                .verify();

        constraintHandlerUtilsMock.verify(
                () -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), anyString()),
                times(2));
    }

    @Test
    void when_FilterJsonContentIsDesired_then_manipulate() {
        // GIVEN

        // WHEN
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("mongoQueryManipulation"))).thenReturn(JsonNodeFactory.instance.nullNode());
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("filterJsonContent"))).thenReturn(filterJsonContent);
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("jsonContentFilterPredicate"))).thenReturn(JsonNodeFactory.instance.nullNode());

        var result = dataManipulationHandler.manipulate(obligations).apply(data);

        // THEN
        StepVerifier.create(result)
                .expectNextMatches(testUser -> {
                    Assertions.assertEquals(testUser.getId(), aaron.getId());
                    Assertions.assertEquals(testUser.getAge(), 0);
                    Assertions.assertEquals(testUser.getFirstname(), "Aa███");
                    return true;
                })
                .expectNextMatches(testUser -> {
                    Assertions.assertEquals(testUser.getId(), brian.getId());
                    Assertions.assertEquals(testUser.getAge(), 0);
                    Assertions.assertEquals(testUser.getFirstname(), "Br███");
                    return true;
                })
                .expectNextMatches(testUser -> {
                    Assertions.assertEquals(testUser.getId(), cathrin.getId());
                    Assertions.assertEquals(testUser.getAge(), 0);
                    Assertions.assertEquals(testUser.getFirstname(), "Ca█████");
                    return true;
                })
                .expectComplete()
                .verify();

        constraintHandlerUtilsMock.verify(() -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), anyString()), times(2));
    }

    @Test
    void when_FilterJsonContentAndFilterPredicateIsDesired_then_manipulate() {
        // GIVEN

        // WHEN
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("mongoQueryManipulation"))).thenReturn(JsonNodeFactory.instance.nullNode());
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("filterJsonContent"))).thenReturn(filterJsonContent);
        constraintHandlerUtilsMock.when(() -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), eq("jsonContentFilterPredicate"))).thenReturn(jsonContentFilterPredicate);

        var result = dataManipulationHandler.manipulate(obligations).apply(data);

        // THEN
        StepVerifier.create(result)
                .expectNextMatches(testUser -> {
                    Assertions.assertEquals(testUser.getId(), aaron.getId());
                    Assertions.assertEquals(testUser.getAge(), 0);
                    Assertions.assertEquals(testUser.getFirstname(), "Aa███");
                    return true;
                })
                .expectComplete()
                .verify();

        constraintHandlerUtilsMock.verify(() -> ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(any(JsonNode.class), anyString()), times(2));
    }

    /**
     * Necessary, because the ObjectMapper changes the object reference. The
     * ObjectMapper is needed if a DomainObject has an ObjectId as type.
     */
    private boolean assertTwoTestUsers(TestUser first, TestUser second) {
        Assertions.assertEquals(first.getId(), second.getId());
        Assertions.assertEquals(first.getFirstname(), second.getFirstname());
        Assertions.assertEquals(first.getAge(), second.getAge());

        return true;
    }
}
