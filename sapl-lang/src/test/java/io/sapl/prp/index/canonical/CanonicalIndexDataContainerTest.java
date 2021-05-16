package io.sapl.prp.index.canonical;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;

import org.junit.jupiter.api.Test;

class CanonicalIndexDataContainerTest {

    @Test
    void testGetNumberOfFormulasWithConjunction() {
        var numberOfFormulasWithConjunction = new int[]{0, 1, 2, 3};

        var container = new CanonicalIndexDataContainer(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),  new int[0],numberOfFormulasWithConjunction);

        assertThat(container.getNumberOfFormulasWithConjunction(0), is(0));
        assertThat(container.getNumberOfFormulasWithConjunction(3), is(3));

        assertThrows(ArrayIndexOutOfBoundsException.class, () ->  container.getNumberOfFormulasWithConjunction(Integer.MIN_VALUE));
        assertThrows(ArrayIndexOutOfBoundsException.class, () ->  container.getNumberOfFormulasWithConjunction(Integer.MAX_VALUE));
    }
}
