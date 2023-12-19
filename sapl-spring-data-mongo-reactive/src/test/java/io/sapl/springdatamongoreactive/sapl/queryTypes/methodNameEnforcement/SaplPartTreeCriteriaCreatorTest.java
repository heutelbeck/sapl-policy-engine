package io.sapl.springdatamongoreactive.sapl.queryTypes.methodNameEnforcement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.Operator;
import io.sapl.springdatamongoreactive.sapl.utils.SaplCondition;
import io.sapl.springdatamongoreactive.sapl.utils.SaplConditionOperation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoWriter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.query.ConvertingParameterAccessor;
import org.springframework.data.mongodb.repository.query.MongoParameterAccessor;
import org.springframework.data.repository.query.parser.Part;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.mockito.Mockito.*;

class SaplPartTreeCriteriaCreatorTest {

    ReactiveMongoTemplate reactiveMongoTemplateMock;

    static MockedStatic<SaplConditionOperation> saplConditionOperationMockedStatic;

    @BeforeAll
    public static void beforeAll() {
        saplConditionOperationMockedStatic = mockStatic(SaplConditionOperation.class);
    }

    @BeforeEach
    public void beforeEach() {
        reactiveMongoTemplateMock = mock(ReactiveMongoTemplate.class);
    }

    @Test
    void when_policyDecisionContainsQueryManipulationConditions_then_createManipulatedQuery() {

        try (MockedConstruction<MongoQueryCreatorFactory> mockedConstruction = Mockito
                .mockConstruction(MongoQueryCreatorFactory.class)) {

            // GIVEN
            var methodInvocation = new MethodInvocationForTesting("findAllByAgeBefore",
                    new ArrayList<>(List.of(int.class)), new ArrayList<>(List.of(40)), null);

            var criteria1         = Criteria.where("age").lt(40);
            var criteria1and2     = criteria1.and("id").is(10);
            var criteria1and2and3 = criteria1and2.and("firstname").is("Aaron");

            var mongoWriter = Mockito.mock(MongoWriter.class);
            var delegate    = Mockito.mock(MongoParameterAccessor.class);

            var conditions                     = JsonNodeFactory.instance.objectNode();
            var expectedMethodSaplConditions   = new ArrayList<>(
                    List.of(new SaplCondition("age", 40, Operator.BEFORE, "And")));
            var expectedJsonNodeSaplConditions = new ArrayList<>(
                    List.of(new SaplCondition("id", 10, Operator.SIMPLE_PROPERTY, "And"),
                            new SaplCondition("firstname", "Aaron", Operator.SIMPLE_PROPERTY, "And")));
            var expectedResult                 = new Query()
                    .addCriteria(Criteria.where("age").lt(40).and("id").is(10).and("firstname").is("Aaron"))
                    .with(Sort.by(List.of()));

            var saplPartTreeCriteriaCreator = new SaplPartTreeCriteriaCreator<>(reactiveMongoTemplateMock,
                    methodInvocation, TestUser.class);

            var mongoQueryCreatorFactoryMock = mockedConstruction.constructed().get(0);

            // WHEN
            when(mongoQueryCreatorFactoryMock.create(any(Part.class), any(Iterator.class))).thenReturn(criteria1);
            when(mongoQueryCreatorFactoryMock.and(any(Part.class), eq(criteria1), any(Iterator.class)))
                    .thenReturn(criteria1and2);
            when(mongoQueryCreatorFactoryMock.and(any(Part.class), eq(criteria1and2), any(Iterator.class)))
                    .thenReturn(criteria1and2and3);
            when(mongoQueryCreatorFactoryMock.getConvertingParameterAccessor())
                    .thenReturn(new ConvertingParameterAccessor(mongoWriter, delegate));
            when(mongoQueryCreatorFactoryMock.getConvertingParameterAccessor().getSort())
                    .thenReturn(Sort.by(List.of()));
            saplConditionOperationMockedStatic.when(() -> SaplConditionOperation
                    .methodToSaplConditions(any(Object[].class), any(Method.class), any(Class.class)))
                    .thenReturn(expectedMethodSaplConditions);
            saplConditionOperationMockedStatic
                    .when(() -> SaplConditionOperation.toModifiedMethodName(anyString(), any(List.class)))
                    .thenReturn("findAllByAgeBeforeAndIdAndFirstname");
            saplConditionOperationMockedStatic
                    .when(() -> SaplConditionOperation.jsonNodeToSaplConditions(any(JsonNode.class)))
                    .thenReturn(expectedJsonNodeSaplConditions);

            var actualResult = saplPartTreeCriteriaCreator.createManipulatedQuery(conditions);

            // THEN
            Assertions.assertEquals(actualResult.toString(), expectedResult.toString());

            verify(mongoQueryCreatorFactoryMock, times(1)).create(any(Part.class), any(Iterator.class));
            verify(mongoQueryCreatorFactoryMock, times(2)).and(any(Part.class), any(Criteria.class),
                    any(Iterator.class));
            saplConditionOperationMockedStatic.verify(() -> SaplConditionOperation
                    .methodToSaplConditions(any(Object[].class), any(Method.class), any(Class.class)), times(1));
            saplConditionOperationMockedStatic
                    .verify(() -> SaplConditionOperation.toModifiedMethodName(anyString(), any(List.class)), times(1));
            saplConditionOperationMockedStatic
                    .verify(() -> SaplConditionOperation.jsonNodeToSaplConditions(any(JsonNode.class)), times(1));
            saplConditionOperationMockedStatic.close();
        }
    }

}
