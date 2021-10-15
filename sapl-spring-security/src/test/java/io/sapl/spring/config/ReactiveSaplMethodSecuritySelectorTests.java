package io.sapl.spring.config;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AdviceMode;

public class ReactiveSaplMethodSecuritySelectorTests {

	@Test
	void when_AdviceModeNotProxy_throwIllegalState() {
		var sut = new ReactiveSaplMethodSecuritySelector();
		assertThrows(IllegalStateException.class, () -> sut.selectImports(AdviceMode.ASPECTJ));
	}

	@Test
	void when_AdviceModeProxy_thenRegistrarAndSaplConfigIncludedInSelectImports() {
		var sut = new ReactiveSaplMethodSecuritySelector();
		var actual = sut.selectImports(AdviceMode.PROXY);
		assertThat(actual, is(arrayContainingInAnyOrder("org.springframework.context.annotation.AutoProxyRegistrar",
				"io.sapl.spring.config.ReactiveSaplMethodSecurityConfiguration")));
	}
}
