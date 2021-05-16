package io.sapl.prp.index.canonical;

import static io.sapl.grammar.sapl.impl.util.ParserUtil.entitilement;
import static io.sapl.grammar.sapl.impl.util.ParserUtil.expression;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EquivalenceAndHashUtilTest {

	@Test
	void testSemanticHash() throws Exception {
		var imports = Map.of("mock", "io.sapl.mock");

		var expA = expression("io.sapl.mock()");
		var expB = expression("mock()");

		var hashA = EquivalenceAndHashUtil.semanticHash(expA, imports);
		var hashB = EquivalenceAndHashUtil.semanticHash(expB, imports);
		var hashC = EquivalenceAndHashUtil.semanticHash(expB, Collections.emptyMap());

		assertThat(hashA, is(hashB));
		assertThat(hashA, not(is(hashC)));

		var exp1 = expression("exp");
		var exp2 = expression("exp");
		var ent1 = entitilement("permit");
		var ent2 = entitilement("deny");

		var hash1 = EquivalenceAndHashUtil.semanticHash(exp1, Collections.emptyMap());
		var hash2 = EquivalenceAndHashUtil.semanticHash(exp2, Collections.emptyMap());
		var hash3 = EquivalenceAndHashUtil.semanticHash(ent1, Collections.emptyMap());
		var hash4 = EquivalenceAndHashUtil.semanticHash(ent2, Collections.emptyMap());

		assertThat(hash1, is(hash2));
		assertThat(hash1, not(is(hash3)));
		assertThat(hash1, not(is(hash4)));
		assertThat(hash3, not(is(hash4)));
	}

	@Test
	void testAreEquivalent() throws Exception {
		var imports = Map.of("mock", "io.sapl.mock");

		var expA = expression("io.sapl.mock()");
		var expB = expression("mock()");
		assertThat(EquivalenceAndHashUtil.areEquivalent(expA, imports, expB, imports), is(true));
		assertThat(EquivalenceAndHashUtil.areEquivalent(expB, imports, expB, imports), is(true));

		var exp1 = expression("exp1");
		var exp2 = expression("exp1");
		var exp3 = expression("exp2");
		var ent1 = entitilement("permit");
		var ent2 = entitilement("deny");

		assertThat(EquivalenceAndHashUtil.areEquivalent(exp1, Collections.emptyMap(), exp2, Collections.emptyMap()),
				is(true));
		assertThat(EquivalenceAndHashUtil.areEquivalent(exp1, Collections.emptyMap(), exp3, Collections.emptyMap()),
				is(false));
		assertThat(EquivalenceAndHashUtil.areEquivalent(ent1, Collections.emptyMap(), ent1, Collections.emptyMap()),
				is(true));
		assertThat(EquivalenceAndHashUtil.areEquivalent(ent1, Collections.emptyMap(), ent2, Collections.emptyMap()),
				is(false));
		assertThat(EquivalenceAndHashUtil.areEquivalent(exp1, Collections.emptyMap(), ent1, Collections.emptyMap()),
				is(false));

		assertThrows(NullPointerException.class,
				() -> EquivalenceAndHashUtil.areEquivalent(exp1, Collections.emptyMap(), null, Collections.emptyMap()));
		assertThrows(NullPointerException.class,
				() -> EquivalenceAndHashUtil.areEquivalent(null, Collections.emptyMap(), exp2, Collections.emptyMap()));
		assertThrows(NullPointerException.class,
				() -> EquivalenceAndHashUtil.areEquivalent(exp1, null, exp2, Collections.emptyMap()));
		assertThrows(NullPointerException.class,
				() -> EquivalenceAndHashUtil.areEquivalent(exp1, Collections.emptyMap(), exp2, null));

		assertThrows(NullPointerException.class, () -> new Literal((Bool) null));

		// Map<String, String> imports = Map.of("numbers", "test.numbers");
		//
		// var e4 = expression("numbers.MAX_VALUE");
		// var e5 = expression("test.numbers.MAX_VALUE");
		// assertThat(EquivalenceAndHashUtil.areEquivalent(e4, imports, e5, imports),
		// is(true));
	}

}
