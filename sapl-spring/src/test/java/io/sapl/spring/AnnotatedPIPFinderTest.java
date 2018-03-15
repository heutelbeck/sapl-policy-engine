package io.sapl.spring;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;

import io.sapl.spring.otherpips.PIPX;
import io.sapl.spring.otherpips.PIPY;
import io.sapl.spring.pips.PIP1;
import io.sapl.spring.pips.PIP2;

public class AnnotatedPIPFinderTest {

	PIPProvider provider;

	@Test
	public void testWithSubpackages() throws ClassNotFoundException {
		provider = new AnnotationScanPipProvider(new String[] { "io.sapl.spring" });
		Collection<Class<? extends Object>> classes = provider.getPIPClasses();
		Collection<Class<? extends Object>> expected = Arrays.asList(Class.forName(TestPiP.class.getName()), PIP1.class,
				PIP2.class, PIPX.class, PIPY.class);
		assertTrue(classes.containsAll(expected) && expected.containsAll(classes));

	}

	@Test
	public void testPackage() {
		provider = new AnnotationScanPipProvider(new String[] { "io.sapl.spring.pips" });
		Collection<Class<? extends Object>> classes = provider.getPIPClasses();
		Collection<Class<? extends Object>> expected = Arrays.asList(PIP1.class, PIP2.class);
		assertTrue(classes.containsAll(expected) && expected.containsAll(classes));

	}

	@Test
	public void testTwoPackage() throws ClassNotFoundException {
		provider = new AnnotationScanPipProvider(new String[] { "io.sapl.spring.otherpips", "io.sapl.spring.pips" });
		Collection<Class<? extends Object>> classes = provider.getPIPClasses();
		Collection<Class<? extends Object>> expected = Arrays.asList(PIP1.class, PIP2.class, PIPX.class, PIPY.class);
		assertTrue(classes.containsAll(expected) && expected.containsAll(classes));

	}

}
