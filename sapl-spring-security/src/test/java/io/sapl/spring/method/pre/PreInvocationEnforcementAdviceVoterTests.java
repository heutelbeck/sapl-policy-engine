package io.sapl.spring.method.pre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;

import io.sapl.spring.method.attributes.PreEnforceAttribute;
import io.sapl.spring.method.blocking.PreInvocationEnforcementAdvice;
import io.sapl.spring.method.blocking.PreInvocationEnforcementAdviceVoter;

class PreInvocationEnforcementAdviceVoterTests {

	@Test
	void whenPresentedWithNonSupported_thenItSaysSo() {
		var advice = mock(PreInvocationEnforcementAdvice.class);
		var sut = new PreInvocationEnforcementAdviceVoter(advice);
		assertThat(sut.supports(mock(ConfigAttribute.class))).isFalse();
		assertThat(sut.supports(String.class)).isFalse();
	}

	@Test
	void whenPresentedWithSupported_thenItSaysSo() {
		var advice = mock(PreInvocationEnforcementAdvice.class);
		var sut = new PreInvocationEnforcementAdviceVoter(advice);
		assertThat(sut.supports(mock(PreEnforceAttribute.class))).isTrue();
		assertThat(sut.supports(MethodInvocation.class)).isTrue();
	}

	@Test
	void whenNoAdvice_thenVoteAbstain() {
		var sut = new PreInvocationEnforcementAdviceVoter(null);
		var vote = sut.vote(mock(Authentication.class), mock(MethodInvocation.class), new ArrayList<>());
		assertThat(vote).isEqualTo(AccessDecisionVoter.ACCESS_ABSTAIN);
	}

	@Test
	void whenAdviceBeforePermit_thenVoteAccessGranted() {
		var advice = mock(PreInvocationEnforcementAdvice.class);
		when(advice.before(any(), any(), any())).thenReturn(true);
		var sut = new PreInvocationEnforcementAdviceVoter(advice);
		var attributes = new ArrayList<ConfigAttribute>();
		attributes.add(mock(PreEnforceAttribute.class));
		var vote = sut.vote(mock(Authentication.class), mock(MethodInvocation.class), attributes);
		assertThat(vote).isEqualTo(AccessDecisionVoter.ACCESS_GRANTED);
	}

	@Test
	void whenAdviceBeforeDeny_thenVoteAccessDenied() {
		var advice = mock(PreInvocationEnforcementAdvice.class);
		when(advice.before(any(), any(), any())).thenReturn(false);
		var sut = new PreInvocationEnforcementAdviceVoter(advice);
		var attributes = new ArrayList<ConfigAttribute>();
		attributes.add(mock(ConfigAttribute.class));
		attributes.add(mock(PreEnforceAttribute.class));
		var vote = sut.vote(mock(Authentication.class), mock(MethodInvocation.class), attributes);
		assertThat(vote).isEqualTo(AccessDecisionVoter.ACCESS_DENIED);
	}

}
