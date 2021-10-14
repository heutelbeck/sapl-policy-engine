package io.sapl.spring.filter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import reactor.core.publisher.Flux;

@SpringBootConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class PolicyEnforcementFilterPEPTests {

	private ObjectMapper mapper;
	private PolicyDecisionPoint pdp;
	private ReactiveConstraintEnforcementService constraintHandlers;

	@BeforeEach
	void setUpMocks() {
		mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		mapper.registerModule(module);
		pdp = mock(PolicyDecisionPoint.class);
		constraintHandlers = mock(ReactiveConstraintEnforcementService.class);
	}

	@Test
	@WithMockUser
	void whenPermit_thenNoException() throws IOException, ServletException {
		var sut = new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(constraintHandlers.handleForBlockingMethodInvocationOrAccessDenied(any())).thenReturn(true);
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		sut.doFilter(request, response, filterChain);
		verify(filterChain, times(1)).doFilter(any(), any());
		verify(constraintHandlers, times(1)).handleForBlockingMethodInvocationOrAccessDenied(any());
	}

	@Test
	@WithMockUser
	void whenPermitAndObligationFails_thenAccessDeniedException() throws IOException, ServletException {
		var sut = new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(constraintHandlers.handleForBlockingMethodInvocationOrAccessDenied(any())).thenReturn(false);

		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
		verify(constraintHandlers, times(1)).handleForBlockingMethodInvocationOrAccessDenied(any());
	}

	@Test
	@WithMockUser
	void whenEmptyAnswert_thenAccessDeniedException() throws IOException, ServletException {
		var sut = new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.empty());
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
	}

	@Test
	@WithMockUser
	void whenDeny_thenAccessDeniedException() throws IOException, ServletException {
		var sut = new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.DENY));
		when(constraintHandlers.handleForBlockingMethodInvocationOrAccessDenied(any())).thenReturn(true);
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
		verify(filterChain, times(0)).doFilter(any(), any());
		verify(constraintHandlers, times(1)).handleForBlockingMethodInvocationOrAccessDenied(any());
	}

	@Test
	@WithMockUser
	void whenIndeterminate_thenAccessDeniedException() throws IOException, ServletException {
		var sut = new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
		when(constraintHandlers.handleForBlockingMethodInvocationOrAccessDenied(any())).thenReturn(true);
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
		verify(filterChain, times(0)).doFilter(any(), any());
		verify(constraintHandlers, times(1)).handleForBlockingMethodInvocationOrAccessDenied(any());
	}

	@Test
	@WithMockUser
	void whenNotApplicable_thenAccessDeniedException() throws IOException, ServletException {
		var sut = new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
		when(constraintHandlers.handleForBlockingMethodInvocationOrAccessDenied(any())).thenReturn(true);
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();
		var filterChain = mock(FilterChain.class);
		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
		verify(filterChain, times(0)).doFilter(any(), any());
		verify(constraintHandlers, times(1)).handleForBlockingMethodInvocationOrAccessDenied(any());
	}

}
