/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.filter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.ConstraintHandlerBundle;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import reactor.core.publisher.Flux;

@SpringBootConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SaplFilterPolicyEnforcementPointTests {

	private ObjectMapper mapper;

	private PolicyDecisionPoint pdp;

	private ConstraintEnforcementService constraintHandlers;

	private ConstraintHandlerBundle<?> bundle;

	@BeforeEach
	void setUpMocks() {
		mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		mapper.registerModule(module);
		pdp = mock(PolicyDecisionPoint.class);
		constraintHandlers = mock(ConstraintEnforcementService.class);
		bundle = mock(ConstraintHandlerBundle.class);
		doReturn(bundle).when(constraintHandlers).bundleFor(any(), any());
	}

	@Test
	@WithMockUser
	void whenPermit_thenNoException() throws IOException, ServletException {
		var sut = new SaplFilterPolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		doReturn(Flux.empty()).when(bundle).wrap(any());
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		sut.doFilter(request, response, filterChain);
		verify(filterChain, times(1)).doFilter(any(), any());
		verify(bundle, times(1)).wrap(any());
	}

	@Test
	@WithMockUser
	void whenPermitAndObligationFails_thenAccessDeniedException() throws IOException, ServletException {
		var sut = new SaplFilterPolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		doReturn(Flux.error(new AccessDeniedException("ERROR"))).when(bundle).wrap(any());

		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
		verify(bundle, times(1)).wrap(any());
	}

	@Test
	@WithMockUser
	void whenPermitAndResourceSet_thenAccessDeniedException() throws IOException, ServletException {
		var sut = new SaplFilterPolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(decisionFluxOnePermitWithResource());
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
		verify(bundle, times(0)).wrap(any());
	}

	public Flux<AuthorizationDecision> decisionFluxOnePermitWithResource() {
		var json = JsonNodeFactory.instance;
		var resource = json.numberNode(10000L);
		return Flux.just(AuthorizationDecision.PERMIT.withResource(resource));
	}

	@Test
	@WithMockUser
	void whenEmptyAnswert_thenAccessDeniedException() throws IOException, ServletException {
		var sut = new SaplFilterPolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.empty());
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
	}

	@Test
	@WithMockUser
	void whenDeny_thenAccessDeniedException() throws IOException, ServletException {
		var sut = new SaplFilterPolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.DENY));
		doReturn(Flux.empty()).when(bundle).wrap(any());
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
		verify(filterChain, times(0)).doFilter(any(), any());
		verify(bundle, times(1)).wrap(any());
	}

	@Test
	@WithMockUser
	void whenIndeterminate_thenAccessDeniedException() throws IOException, ServletException {
		var sut = new SaplFilterPolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
		doReturn(Flux.empty()).when(bundle).wrap(any());
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
		verify(filterChain, times(0)).doFilter(any(), any());
		verify(bundle, times(1)).wrap(any());
	}

	@Test
	@WithMockUser
	void whenNotApplicable_thenAccessDeniedException() throws IOException, ServletException {
		var sut = new SaplFilterPolicyEnforcementPoint(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
		doReturn(Flux.empty()).when(bundle).wrap(any());
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
		verify(filterChain, times(0)).doFilter(any(), any());
		verify(bundle, times(1)).wrap(any());
	}

}
