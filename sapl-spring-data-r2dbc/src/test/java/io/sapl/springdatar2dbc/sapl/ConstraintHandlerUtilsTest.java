package io.sapl.springdatar2dbc.sapl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.springdatar2dbc.sapl.utils.ConstraintHandlerUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConstraintHandlerUtilsTest {

    final static ObjectMapper objectMapper = new ObjectMapper();
    static JsonNode           obligations;
    static JsonNode           mongoQueryManipulation;
    static JsonNode           wrongTypesObligations;
    static JsonNode           advice;

    final JsonNode nullNode = JsonNodeFactory.instance.nullNode();

    @BeforeAll
    public static void initBeforeAll() throws JsonProcessingException {
        obligations            = objectMapper.readTree(
                "[{\"type\":\"r2dbcQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]},{\"type\":\"filterJsonContent\",\"actions\":[{\"type\":\"blacken\",\"path\":\"$.firstname\",\"discloseLeft\":2}]},{\"type\":\"jsonContentFilterPredicate\",\"conditions\":[{\"type\":\"==\",\"path\":\"$.id\",\"value\":\"a1\"}]}]");
        mongoQueryManipulation = objectMapper
                .readTree("{\"type\":\"r2dbcQueryManipulation\",\"conditions\":[\"{'role':  {'$in': ['USER']}}\"]}");
        wrongTypesObligations  = objectMapper
                .readTree("[{\"type\":\"r2dbcQuery\",\"conditions\":\"'role':  {'$in': ['USER']}\"}]");
        advice                 = objectMapper
                .readTree("[{\"id\": \"log\",\"message\": \"You are using SAPL for protection of database.\"}]");
    }

    @Test
    void when_obligationContainsSpecificType_then_getConstraintHandlerByTypeIfResponsible() {
        // GIVEN

        // WHEN
        var actual = ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(obligations,
                "r2dbcQueryManipulation");

        // THEN
        Assertions.assertEquals(mongoQueryManipulation, actual);
    }

    @Test
    void when_obligationContainsNotSpecificType_then_returnNullNode() {
        // GIVEN

        // WHEN
        var actual = ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible(wrongTypesObligations,
                "r2dbcQueryManipulation");

        // THEN
        Assertions.assertEquals(nullNode, actual);
    }

    @Test
    void when_authorizationDecisionHasObligations_then_getObligations() {
        // GIVEN
        var obligationsAsArrayNode = (ArrayNode) obligations;
        var optionalObligations    = Optional.of(obligationsAsArrayNode);
        var authDec                = new AuthorizationDecision(Decision.PERMIT, null, optionalObligations, null);

        // WHEN
        var actual = ConstraintHandlerUtils.getObligations(authDec);

        // THEN
        Assertions.assertEquals(obligations, actual);
    }

    @Test
    void when_authorizationDecisionHasAdvices_then_getAdvices() {
        // GIVEN
        var adviceAsArrayNode = (ArrayNode) advice;

        var optionalAdvice = Optional.of(adviceAsArrayNode);
        var authDec        = new AuthorizationDecision(Decision.PERMIT, null, null, optionalAdvice);

        // WHEN
        var actual = ConstraintHandlerUtils.getAdvices(authDec);

        // THEN
        Assertions.assertEquals(advice, actual);
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            var constructor = ConstraintHandlerUtils.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }
}
