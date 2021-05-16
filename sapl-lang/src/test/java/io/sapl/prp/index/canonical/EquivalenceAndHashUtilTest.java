package io.sapl.prp.index.canonical;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static io.sapl.grammar.sapl.impl.util.ParserUtil.expression;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class EquivalenceAndHashUtilTest {

    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    @Test
    void testSemanticHash() throws Exception{
        var e1  = expression("exp1");
        var e2  = expression("exp1");
        var e3  = expression("exp2");

        var hash1 = EquivalenceAndHashUtil.semanticHash(e1, Collections.emptyMap());
        var hash2 = EquivalenceAndHashUtil.semanticHash(e2, Collections.emptyMap());
        var hash3 = EquivalenceAndHashUtil.semanticHash(e3, Collections.emptyMap());

        assertThat(hash1, is(hash2));
        assertThat(hash1, not(is(hash3)));
    }

    @Test
    @Disabled
    void testSemanticHashWithImports()throws Exception {
        Map<String, String> imports =  Map.of("numbers", "test.numbers");

        var e1  = expression("numbers.MAX_VALUE");
        var e2  = expression("test.numbers.MAX_VALUE");

        var hash1 = EquivalenceAndHashUtil.semanticHash(e1, imports);
        var hash2 = EquivalenceAndHashUtil.semanticHash(e2, imports);

        var policy = INTERPRETER.parse("policy \"p\" permit");
        var expected = AuthorizationDecision.PERMIT;

        assertThat(hash1, is(hash2));
    }

    @Test
    void testAreEquivalent()throws  Exception{
        var e1  = expression("exp1");
        var e2  = expression("exp1");
        var e3  = expression("exp2");

        assertThat(EquivalenceAndHashUtil.areEquivalent(e1,  Collections.emptyMap(), e2, Collections.emptyMap()), is(true));
        assertThat(EquivalenceAndHashUtil.areEquivalent(e1,  Collections.emptyMap(), e3, Collections.emptyMap()), is(false));

//        Map<String, String> imports =  Map.of("numbers", "test.numbers");
//
//        var e4  = expression("numbers.MAX_VALUE");
//        var e5  = expression("test.numbers.MAX_VALUE");
//        assertThat(EquivalenceAndHashUtil.areEquivalent(e4,  imports, e5, imports), is(true));
    }

}
