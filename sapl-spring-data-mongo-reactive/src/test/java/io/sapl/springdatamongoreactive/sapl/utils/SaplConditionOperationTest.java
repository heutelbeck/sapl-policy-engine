package io.sapl.springdatamongoreactive.sapl.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.springdatamongoreactive.sapl.Operator;
import io.sapl.springdatamongoreactive.sapl.utils.SaplCondition;
import io.sapl.springdatamongoreactive.sapl.utils.SaplConditionOperation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaplConditionOperationTest {

    static final ObjectMapper objectMapper = new ObjectMapper();
    static JsonNode           mongoQueryManipulation;
    static JsonNode           conditions;
    static JsonNode           notValidConditions;

    @BeforeAll
    public static void setUp() throws JsonProcessingException {
        mongoQueryManipulation = objectMapper.readTree(
                "{\"type\":\"mongoQueryManipulation\",\"conditions\":[\"{'age': {'gt': 30 }}\", \"{'firstname':  {'$in': ['Cathrin', 'Aaron']}}\"]}");
        conditions             = mongoQueryManipulation.get("conditions");
        notValidConditions     = objectMapper.readTree("[\"{'fieldNotValid': {'gt': 30 }}\"]");
    }

    @Test
    void when_saplConditionsCanBeGeneratedFromJsonNode_then_jsonNodeToSaplConditions() {
        // GIVEN
        ArrayList<SaplCondition> expected = new ArrayList<>();
        expected.add(new SaplCondition("age", 30, Operator.GREATER_THAN, "And"));
        expected.add(new SaplCondition("firstname", new ArrayList<>(List.of("Cathrin", "Aaron")), Operator.IN, "And"));

        // WHEN
        var actualSaplConditions = SaplConditionOperation.jsonNodeToSaplConditions(conditions);

        // THEN
        assertTwoSaplConditions(actualSaplConditions.get(0), expected.get(0));
        assertTwoSaplConditions(actualSaplConditions.get(1), expected.get(1));
    }

    @Test
    void when_jsonNodeIsEmpty_then_returnEmptySaplConditionsList() {
        // GIVEN
        ArrayList<SaplCondition> expected = new ArrayList<>();

        // WHEN
        var actualSaplConditions = SaplConditionOperation.jsonNodeToSaplConditions(JsonNodeFactory.instance.nullNode());

        // THEN
        assertEquals(actualSaplConditions, expected);
    }

    @Test
    void when_methodNameCanBeModified_then_toModifiedMethodName() {
        // GIVEN
        ArrayList<SaplCondition> saplConditions = new ArrayList<>();
        saplConditions.add(new SaplCondition("age", 30, Operator.GREATER_THAN, "And"));
        saplConditions
                .add(new SaplCondition("firstname", new ArrayList<>(List.of("Cathrin", "Aaron")), Operator.IN, "And"));
        String expectedMethodName = "findAllByIdAndAgeIsGreaterThanAndFirstnameIsIn";

        // WHEN
        var actualMethodName = SaplConditionOperation.toModifiedMethodName("findAllById", saplConditions);

        // THEN
        assertEquals(expectedMethodName, actualMethodName);
    }

    @Test
    void when_methodNameCanBeModifiedAndContainsKeyword_then_toModifiedMethodName() {
        // GIVEN
        ArrayList<SaplCondition> saplConditions = new ArrayList<>();
        saplConditions.add(new SaplCondition("age", 30, Operator.GREATER_THAN, "And"));
        saplConditions
                .add(new SaplCondition("firstname", new ArrayList<>(List.of("Cathrin", "Aaron")), Operator.IN, "And"));
        String expectedMethodName = "findAllByIdAndAgeIsGreaterThanAndFirstnameIsInOrderByAge";

        // WHEN
        var actualMethodName = SaplConditionOperation.toModifiedMethodName("findAllByIdOrderByAge", saplConditions);

        // THEN
        assertEquals(expectedMethodName, actualMethodName);
    }

    private void assertTwoSaplConditions(SaplCondition first, SaplCondition second) {
        assertEquals(first.field(), second.field());
        assertEquals(first.value(), second.value());
        assertEquals(first.operator(), second.operator());
        assertEquals(first.conjunction(), second.conjunction());
    }

}
