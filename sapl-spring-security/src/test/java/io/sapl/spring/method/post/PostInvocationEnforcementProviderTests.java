package io.sapl.spring.method.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

import io.sapl.spring.method.attributes.PostEnforceAttribute;
import io.sapl.spring.method.blocking.PostInvocationEnforcementAdvice;
import io.sapl.spring.method.blocking.PostInvocationEnforcementProvider;

class PostInvocationEnforcementProviderTests {

	@Test
	void whenPostsentedWithNonSupported_thenItSaysSo() {
		var advice = mock(PostInvocationEnforcementAdvice.class);
		var sut = new PostInvocationEnforcementProvider(advice);
		assertThat(sut.supports(mock(ConfigAttribute.class))).isFalse();
		assertThat(sut.supports(String.class)).isFalse();
	}

	@Test
	void whenPostsentedWithSupported_thenItSaysSo() {
		var advice = mock(PostInvocationEnforcementAdvice.class);
		var sut = new PostInvocationEnforcementProvider(advice);
		assertThat(sut.supports(mock(PostEnforceAttribute.class))).isTrue();
		assertThat(sut.supports(MethodInvocation.class)).isTrue();
	}

	@Test
	void whenNoAdvice_thenVoteAbstain() {
		var sut = new PostInvocationEnforcementProvider(null);
		var returnObject = sut.decide(mock(Authentication.class), mock(MethodInvocation.class), new ArrayList<>(),
				"original return object");
		assertThat(returnObject).isEqualTo("original return object");
	}

	@Test
	void whenAdviceBeforePermit_thenReturnTheIndicatedObject() {
		var advice = mock(PostInvocationEnforcementAdvice.class);
		when(advice.after(any(), any(), any(), any())).thenReturn("changed return object");
		var sut = new PostInvocationEnforcementProvider(advice);
		var attributes = new ArrayList<ConfigAttribute>();
		attributes.add(mock(PostEnforceAttribute.class));
		var vote = sut.decide(mock(Authentication.class), mock(MethodInvocation.class), attributes,
				"original return object");
		assertThat(vote).isEqualTo("changed return object");
	}

	@Test
	void whenAdviceBeforeDeny_thenVoteAccessDenied() {
		var advice = mock(PostInvocationEnforcementAdvice.class);
		when(advice.after(any(), any(), any(), any())).thenThrow(new AccessDeniedException("FORCED DENY"));
		var sut = new PostInvocationEnforcementProvider(advice);
		var attributes = new ArrayList<ConfigAttribute>();
		attributes.add(mock(ConfigAttribute.class));
		attributes.add(mock(PostEnforceAttribute.class));
		assertThrows(AccessDeniedException.class, () -> sut.decide(mock(Authentication.class),
				mock(MethodInvocation.class), attributes, "original return object"));
	}

}
