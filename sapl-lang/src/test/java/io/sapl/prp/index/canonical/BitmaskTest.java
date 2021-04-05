package io.sapl.prp.index.canonical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BitmaskTest {

    Bitmask bitmask;

    @BeforeEach
    void setUp() {
        bitmask = new Bitmask();
        bitmask.set(2, 4);
    }

    @Test
    void flipTest() {
        assertThat(bitmask.toString(), is("{2, 3}"));

        bitmask.flip(0, 4);
        assertThat(bitmask.toString(), is("{0, 1}"));
    }

    @Test
    void forEachSetBitTest() {
        var listMock = (List<Integer>) mock(List.class);

        bitmask.forEachSetBit(listMock::add);

        verify(listMock, times(2)).add(anyInt());
    }
}
