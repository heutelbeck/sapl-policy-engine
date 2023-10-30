package io.sapl.grammar.sapl.impl.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import reactor.util.function.Tuples;

public class RepackageUtilTests {

    @Test
    void testObjectCombiningOnErrors1() {
        var t1     = Tuples.of("key1", Val.of("value1"));
        var t2     = Tuples.of("key2", Val.error("error1"));
        var t3     = Tuples.of("key3", Val.of("value3"));
        var t4     = Tuples.of("key4", Val.of("value4"));
        var actual = RepackageUtil.recombineObject(new Object[] { t1, t2, t3, t4 });
        assertThat(actual).isEqualTo(Val.error("error1"));
    }

    @Test
    void testObjectCombiningOnErrors2() {
        var t1     = Tuples.of("key1", Val.of("value1"));
        var t2     = Tuples.of("key2", Val.error("error1"));
        var t3     = Tuples.of("key3", Val.error("error2"));
        var t4     = Tuples.of("key4", Val.of("value4"));
        var actual = RepackageUtil.recombineObject(new Object[] { t1, t2, t3, t4 });
        assertThat(actual).isEqualTo(Val.error("error1"));
    }
}
