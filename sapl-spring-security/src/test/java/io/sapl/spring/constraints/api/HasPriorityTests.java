package io.sapl.spring.constraints.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

public class HasPriorityTests {

	@Test
	void minimalPriorityIsZero() {
		var sut = spy(HasPriority.class);
		assertEquals(0, sut.getPriority());		
	}
	
	@Test
	void compares_priorityAscending() {
		var sut_a = spy(HasPriority.class);
		var sut_b = spy(HasPriority.class);
		when(sut_a.getPriority()).thenReturn(-100);
		assertThat(sut_b.compareTo(sut_a)).isLessThan(0);		
		assertThat(sut_a.compareTo(sut_b)).isGreaterThan(0);		
		assertThat(sut_a.compareTo(sut_a)).isEqualTo(0);	
	}
}
