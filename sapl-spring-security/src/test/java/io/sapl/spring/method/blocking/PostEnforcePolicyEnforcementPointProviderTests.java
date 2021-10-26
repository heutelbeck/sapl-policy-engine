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

import io.sapl.spring.method.metadata.PostEnforceAttribute;

class PostEnforcePolicyEnforcementPointProviderTests {

	@Test
	void whenPostsentedWithNonSupported_thenItSaysSo() {
		var advice = mock(PostEnforcePolicyEnforcementPoint.class);
		var sut = new PostEnforcePolicyEnforcementPointProvider(advice);
		assertThat(sut.supports(mock(ConfigAttribute.class))).isFalse();
		assertThat(sut.supports(String.class)).isFalse();
	}

	@Test
	void whenPostsentedWithSupported_thenItSaysSo() {
		var advice = mock(PostEnforcePolicyEnforcementPoint.class);
		var sut = new PostEnforcePolicyEnforcementPointProvider(advice);
		assertThat(sut.supports(mock(PostEnforceAttribute.class))).isTrue();
		assertThat(sut.supports(MethodInvocation.class)).isTrue();
	}

	@Test
	void whenNoAdvice_thenVoteAbstain() {
		var sut = new PostEnforcePolicyEnforcementPointProvider(null);
		var returnObject = sut.decide(mock(Authentication.class), mock(MethodInvocation.class), new ArrayList<>(),
				"original return object");
		assertThat(returnObject).isEqualTo("original return object");
	}

	@Test
	void whenAdviceBeforePermit_thenReturnTheIndicatedObject() {
		var advice = mock(PostEnforcePolicyEnforcementPoint.class);
		when(advice.after(any(), any(), any(), any())).thenReturn("changed return object");
		var sut = new PostEnforcePolicyEnforcementPointProvider(advice);
		var attributes = new ArrayList<ConfigAttribute>();
		attributes.add(mock(PostEnforceAttribute.class));
		var vote = sut.decide(mock(Authentication.class), mock(MethodInvocation.class), attributes,
				"original return object");
		assertThat(vote).isEqualTo("changed return object");
	}

	@Test
	void whenAdviceBeforeDeny_thenVoteAccessDenied() {
		var advice = mock(PostEnforcePolicyEnforcementPoint.class);
		when(advice.after(any(), any(), any(), any())).thenThrow(new AccessDeniedException("FORCED DENY"));
		var sut = new PostEnforcePolicyEnforcementPointProvider(advice);
		var attributes = new ArrayList<ConfigAttribute>();
		attributes.add(mock(ConfigAttribute.class));
		attributes.add(mock(PostEnforceAttribute.class));
		assertThrows(AccessDeniedException.class, () -> sut.decide(mock(Authentication.class),
				mock(MethodInvocation.class), attributes, "original return object"));
	}

}
