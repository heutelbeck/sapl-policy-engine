/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.method.blocking;

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

import io.sapl.spring.method.metadata.PreEnforceAttribute;

class PreEnforcePolicyEnforcementPointVoterTests {

	@Test
	void whenPresentedWithNonSupported_thenItSaysSo() {
		var advice = mock(PreEnforcePolicyEnforcementPoint.class);
		var sut = new PreEnforcePolicyEnforcementPointVoter(advice);
		assertThat(sut.supports(mock(ConfigAttribute.class))).isFalse();
		assertThat(sut.supports(String.class)).isFalse();
	}

	@Test
	void whenPresentedWithSupported_thenItSaysSo() {
		var advice = mock(PreEnforcePolicyEnforcementPoint.class);
		var sut = new PreEnforcePolicyEnforcementPointVoter(advice);
		assertThat(sut.supports(mock(PreEnforceAttribute.class))).isTrue();
		assertThat(sut.supports(MethodInvocation.class)).isTrue();
	}

	@Test
	void whenNoAdvice_thenVoteAbstain() {
		var sut = new PreEnforcePolicyEnforcementPointVoter(null);
		var vote = sut.vote(mock(Authentication.class), mock(MethodInvocation.class), new ArrayList<>());
		assertThat(vote).isEqualTo(AccessDecisionVoter.ACCESS_ABSTAIN);
	}

	@Test
	void whenAdviceBeforePermit_thenVoteAccessGranted() {
		var advice = mock(PreEnforcePolicyEnforcementPoint.class);
		when(advice.before(any(), any(), any())).thenReturn(true);
		var sut = new PreEnforcePolicyEnforcementPointVoter(advice);
		var attributes = new ArrayList<ConfigAttribute>();
		attributes.add(mock(PreEnforceAttribute.class));
		var vote = sut.vote(mock(Authentication.class), mock(MethodInvocation.class), attributes);
		assertThat(vote).isEqualTo(AccessDecisionVoter.ACCESS_GRANTED);
	}

	@Test
	void whenAdviceBeforeDeny_thenVoteAccessDenied() {
		var advice = mock(PreEnforcePolicyEnforcementPoint.class);
		when(advice.before(any(), any(), any())).thenReturn(false);
		var sut = new PreEnforcePolicyEnforcementPointVoter(advice);
		var attributes = new ArrayList<ConfigAttribute>();
		attributes.add(mock(ConfigAttribute.class));
		attributes.add(mock(PreEnforceAttribute.class));
		var vote = sut.vote(mock(Authentication.class), mock(MethodInvocation.class), attributes);
		assertThat(vote).isEqualTo(AccessDecisionVoter.ACCESS_DENIED);
	}

}
