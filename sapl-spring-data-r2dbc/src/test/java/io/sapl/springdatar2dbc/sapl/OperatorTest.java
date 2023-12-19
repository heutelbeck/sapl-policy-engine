package io.sapl.springdatar2dbc.sapl;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class OperatorTest {

    final Operator operator = Operator.LESS_THAN_EQUAL;

    @Test
    void when_keywordNotExist_then_throwNotImplementedException() {
        Assertions.assertThrows(NotImplementedException.class, () -> Operator.getOperatorByKeyword("notValid"));
    }

    @Test
    void when_keywordExist_then_returnOperation() {
        Operator result = Operator.getOperatorByKeyword(">=");
        Assertions.assertEquals(result, Operator.GREATER_THAN_EQUAL);
    }

    @Test
    void when_valuesOfOperatorIsNoArray_then_returnFalse() {
        Assertions.assertFalse(operator.isArray);
    }

    @Test
    void when_valuesOfOperatorIsArray_then_returnTrue() {
        Assertions.assertTrue(Operator.BETWEEN.isArray);
    }

    @Test
    void when_keywordExists_then_getSqlQueryBasedKeywords() {
        Assertions.assertEquals(operator.sqlQueryBasedKeywords, List.of("<="));
    }

}
