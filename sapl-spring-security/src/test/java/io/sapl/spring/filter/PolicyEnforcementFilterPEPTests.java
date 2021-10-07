package io.sapl.spring.filter;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class PolicyEnforcementFilterPEPTests {

//	private ObjectMapper mapper;
//	private PolicyDecisionPoint pdp;
//	private ReactiveConstraintEnforcementService constraintHandlers;
//
//	@BeforeEach
//	void setUpMocks() {
//		mapper = new ObjectMapper();
//		SimpleModule module = new SimpleModule();
//		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
//		mapper.registerModule(module);
//		pdp = mock(PolicyDecisionPoint.class);
//		constraintHandlers = mock(ReactiveConstraintEnforcementService.class);
//	}
//
//	@Test
//	@WithMockUser
//	void whenPermit_thenNoException() throws IOException, ServletException {
//		var sut = new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
//		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
//		var request = new MockHttpServletRequest();
//		var response = new MockHttpServletResponse();
//		var filterChain = mock(FilterChain.class);
//		sut.doFilter(request, response, filterChain);
//		verify(filterChain, times(1)).doFilter(any(), any());
//		verify(constraintHandlers, times(1)).handleAdvice(any());
//		verify(constraintHandlers, times(1)).handleObligations(any());
//	}
//
//	@Test
//	@WithMockUser
//	void whenPermitAndObligationFails_thenAccessDeniedException() throws IOException, ServletException {
//		var sut = new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
//		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
//		doThrow(new AccessDeniedException("forced failure of obligation")).when(constraintHandlers)
//				.handleObligations(any());
//		var request = new MockHttpServletRequest();
//		var response = new MockHttpServletResponse();
//		var filterChain = mock(FilterChain.class);
//		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
//		verify(constraintHandlers, times(1)).handleAdvice(any());
//		verify(constraintHandlers, times(1)).handleObligations(any());
//	}
//
//	@Test
//	@WithMockUser
//	void whenEmptyAnswert_thenAccessDeniedException() throws IOException, ServletException {
//		var sut = new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
//		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.empty());
//		var request = new MockHttpServletRequest();
//		var response = new MockHttpServletResponse();
//		var filterChain = mock(FilterChain.class);
//		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
//	}
//
//	
//	@Test
//	@WithMockUser
//	void whenDeny_thenAccessDeniedException() throws IOException, ServletException {
//		var sut = new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
//		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.DENY));
//		var request = new MockHttpServletRequest();
//		var response = new MockHttpServletResponse();
//		var filterChain = mock(FilterChain.class);
//		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
//		verify(filterChain, times(0)).doFilter(any(), any());
//		verify(constraintHandlers, times(1)).handleAdvice(any());
//		verify(constraintHandlers, times(1)).handleObligations(any());
//	}
//
//	@Test
//	@WithMockUser
//	void whenIndeterminate_thenAccessDeniedException() throws IOException, ServletException {
//		var sut = new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
//		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
//		var request = new MockHttpServletRequest();
//		var response = new MockHttpServletResponse();
//		var filterChain = mock(FilterChain.class);
//		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
//		verify(filterChain, times(0)).doFilter(any(), any());
//		verify(constraintHandlers, times(1)).handleAdvice(any());
//		verify(constraintHandlers, times(1)).handleObligations(any());
//	}
//
//	@Test
//	@WithMockUser
//	void whenNotApplicable_thenAccessDeniedException() throws IOException, ServletException {
//		var sut = new PolicyEnforcementFilterPEP(pdp, constraintHandlers, mapper);
//		when(pdp.decide((AuthorizationSubscription) any())).thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
//		var request = new MockHttpServletRequest();
//		var response = new MockHttpServletResponse();
//		var filterChain = mock(FilterChain.class);
//		assertThrows(AccessDeniedException.class, () -> sut.doFilter(request, response, filterChain));
//		verify(filterChain, times(0)).doFilter(any(), any());
//		verify(constraintHandlers, times(1)).handleAdvice(any());
//		verify(constraintHandlers, times(1)).handleObligations(any());
//	}

}
