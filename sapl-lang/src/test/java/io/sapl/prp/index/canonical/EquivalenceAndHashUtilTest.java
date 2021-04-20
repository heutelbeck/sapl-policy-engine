package io.sapl.prp.index.canonical;

import io.sapl.grammar.sapl.impl.StringLiteralImplCustom;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class EquivalenceAndHashUtilTest {

    @Test
    void testSemanticHash() {
        var stringLiteral = new StringLiteralImplCustom();
        stringLiteral.setString("string");
        var hash1 = EquivalenceAndHashUtil.semanticHash(stringLiteral, Collections.emptyMap());


        var stringLiteral2 = new StringLiteralImplCustom();
        stringLiteral2.setString("string");
        var hash2 = EquivalenceAndHashUtil.semanticHash(stringLiteral2, Collections.emptyMap());

        assertThat(hash1, is(hash2));
    }

    @Test
    @Disabled
    void testSemanticHashWithImports() {
        Map<String, String> imports =  new HashMap<>();
        imports.put("xyz", "a.string" );

        var stringLiteral = new StringLiteralImplCustom();
        stringLiteral.setString("xyz");
        var hash1 = EquivalenceAndHashUtil.semanticHash(stringLiteral, imports);


        var stringLiteral2 = new StringLiteralImplCustom();
        stringLiteral2.setString("a.string");
        var hash2 = EquivalenceAndHashUtil.semanticHash(stringLiteral2, Collections.emptyMap());

        assertThat(hash1, is(hash2));
    }

    @Test
    void testAreEquivalent() {
        var stringLiteral = new StringLiteralImplCustom();
        stringLiteral.setString("string");

        var stringLiteral2 = new StringLiteralImplCustom();
        stringLiteral2.setString("string");

        assertThat(EquivalenceAndHashUtil.areEquivalent(stringLiteral,  Collections.emptyMap(), stringLiteral2, Collections.emptyMap()), is(true));
    }

}
