package io.sapl.springdatamongoreactive.sapl;

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
        Operator result = Operator.getOperatorByKeyword("$lte");
        Assertions.assertEquals(result, Operator.LESS_THAN_EQUAL);
    }

    @Test
    void when_keywordExists_then_getSqlQueryBasedKeywords() {
        Assertions.assertEquals(operator.mongoBasedKeywords, List.of("$lte", "lte"));
    }

    @Test
    void when_keywordExists_then_getMethodNameBasedKeywords() {
        Assertions.assertEquals(operator.methodNameBasedKeywords, List.of("IsLessThanEqual", "LessThanEqual"));
    }

}
