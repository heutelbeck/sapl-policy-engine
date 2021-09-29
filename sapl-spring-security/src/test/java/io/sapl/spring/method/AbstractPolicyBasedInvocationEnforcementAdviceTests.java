package io.sapl.spring.method;

class AbstractPolicyBasedInvocationEnforcementAdviceTests {

//	ObjectFactory<PolicyDecisionPoint> pdpFactory;
//	ObjectFactory<ReactiveConstraintEnforcementService> constraintHandlerFactory;
//	ObjectFactory<ObjectMapper> objectMapperFactory;
//	ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory;
//	
//	PolicyDecisionPoint pdp;
//	ReactiveConstraintEnforcementService constraintHandlers;
//	Authentication authentication;
//	AbstractPolicyBasedInvocationEnforcementAdvice sut;
//
//	@BeforeEach
//	@SuppressWarnings("unchecked")
//	void beforeEach() {
//		pdp = mock(PolicyDecisionPoint.class);
//		pdpFactory = (ObjectFactory<PolicyDecisionPoint>) mock(ObjectFactory.class);
//		when(pdpFactory.getObject()).thenReturn(pdp);
//
//		constraintHandlers = mock(ReactiveConstraintEnforcementService.class);
//		constraintHandlerFactory = (ObjectFactory<ReactiveConstraintEnforcementService>) mock(ObjectFactory.class);
//		when(constraintHandlerFactory.getObject()).thenReturn(constraintHandlers);
//
//		var mapper = new ObjectMapper();
//		SimpleModule module = new SimpleModule();
//		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
//		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
//		mapper.registerModule(module);
//		objectMapperFactory = (ObjectFactory<ObjectMapper>) mock(ObjectFactory.class);
//		when(objectMapperFactory.getObject()).thenReturn(mapper);
//
//		authentication = new UsernamePasswordAuthenticationToken("principal", "credentials");
//
//	}
//
//	static class TestAdviceEnforcementAdvice extends AbstractPolicyBasedInvocationEnforcementAdvice {
//		public TestAdviceEnforcementAdvice(ObjectFactory<PolicyDecisionPoint> pdpFactory,
//				ObjectFactory<ReactiveConstraintEnforcementService> constraintHandlerFactory,
//				ObjectFactory<ObjectMapper> objectMapperFactory,
//				ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory) {
//			super(pdpFactory, constraintHandlerFactory, objectMapperFactory, subscriptionBuilderFactory);
//		}
//	}
//
//	@Test
//	void whenSettingExpressionHandler_thenFieldIsSet() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		var expressionHandler = mock(MethodSecurityExpressionHandler.class);
//		sut.setExpressionHandler(expressionHandler);
//		assertThat(sut.expressionHandler, is(expressionHandler));
//	}
//
//	@Test
//	void whenLazyLoading_thenFactoriesCalledOnlyOnce() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		sut.lazyLoadDependencies();
//		verify(pdpFactory, times(1)).getObject();
//		verify(constraintHandlerFactory, times(1)).getObject();
//		verify(objectMapperFactory, times(1)).getObject();
//	}
//
//	@Test
//	void whenNoSubjectSetInAttribute_thenRetrieveSubjectReturnsTheAuthentication() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		var attribute = new PreEnforceAttribute((String) null, null, null, null, null);
//		assertThat(sut.retrieveSubject(authentication, attribute, null), is(authentication));
//	}
//
//	@Test
//	void whenSubjectSetInAttribute_thenRetrieveSubjectReturnsTheEvaluatedExpression() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		var attribute = new PreEnforceAttribute("'expected subject'", null, null, null, null);
//		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
//		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
//		assertThat((JsonNode) sut.retrieveSubject(authentication, attribute, evaluationContext),
//				is(jsonText("expected subject")));
//	}
//
//	@Test
//	void whenSerializingMethod_thenMethodIsDescribedInJson() {
//		var method = MethodInvocationUtils.create(new TestClass(), "doSomething").getMethod();
//		assertThat(AbstractPolicyBasedInvocationEnforcementAdvice.serializeMethod(method), is(jsonObject()
//				.where("name", is(jsonText("doSomething"))).where("returnType", is(jsonText("java.lang.String")))
//				.where("declaringClass", is(jsonText(
//						"io.sapl.spring.method.AbstractPolicyBasedInvocationEnforcementAdviceTests$TestClass")))));
//	}
//
//	@Test
//	void whenSerializingClass_thenClassIsDescribedInJson() {
//		assertThat(AbstractPolicyBasedInvocationEnforcementAdvice.serializeTargetClassDescription(TestClass.class),
//				is(jsonObject().where("name", is(jsonText(TestClass.class.getName())))
//						.where("canonicalName", is(jsonText(TestClass.class.getCanonicalName())))
//						.where("typeName", is(jsonText(TestClass.class.getTypeName())))
//						.where("simpleName", is(jsonText(TestClass.class.getSimpleName())))));
//	}
//
//	@Test
//	void whenExpressionThrowsEvaluationException_thenThrowIllegalArgumentException() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		Expression expression = mock(Expression.class);
//		when(expression.getValue((EvaluationContext) any())).thenThrow(new EvaluationException("FORCED EXCEPTION"));
//		assertThrows(IllegalArgumentException.class, () -> sut.evaluateToJson(expression, null));
//	}
//
//	@Test
//	void whenHttpRequestInContext_thenRetrieveIt() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		try (MockedStatic<RequestContextHolder> theMock = mockStatic(RequestContextHolder.class)) {
//			var request = new MockHttpServletRequest();
//			var requestAttributes = mock(ServletRequestAttributes.class);
//			when(requestAttributes.getRequest()).thenReturn(request);
//			theMock.when(() -> RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
//			assertThat(TestAdviceEnforcementAdvice.retrieveRequestObject(), is(optionalWithValue(equalTo(request))));
//		}
//	}
//
//	@Test
//	void whenHttpRequestInContextButNullAttribute_thenRetrieveOptionalEmpty() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		try (MockedStatic<RequestContextHolder> theMock = mockStatic(RequestContextHolder.class)) {
//			theMock.when(() -> RequestContextHolder.getRequestAttributes()).thenReturn(null);
//			assertThat(TestAdviceEnforcementAdvice.retrieveRequestObject(), is(emptyOptional()));
//		}
//	}
//
//	@Test
//	void whenNoEnvironmentSetInAttribute_thenRetrieveEnvironmentReturnsTheAuthentication() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		var attribute = new PreEnforceAttribute((String) null, null, null, null, null);
//		assertThat(sut.retrieveEnvironment(attribute, null), nullValue());
//	}
//
//	@Test
//	void whenEnvironemntSetInAttribute_thenRetrieveEnvironmentReturnsTheEvaluatedExpression() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		var attribute = new PreEnforceAttribute(null, null, null, "'expected environment'", null);
//		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
//		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
//		assertThat((JsonNode) sut.retrieveEnvironment(attribute, evaluationContext),
//				is(jsonText("expected environment")));
//	}
//
//	@Test
//	void whenNoActionSetInAttribute_thenRetrieveActionConstructsOneFromMethodInvokation() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		var attribute = new PreEnforceAttribute((String) null, null, null, null, null);
//		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
//		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
//		assertThat((JsonNode) sut.retrieveAction(methodInvocation, attribute, evaluationContext), is(jsonObject().where(
//				"java",
//				is(jsonObject().where("name", is(jsonText("doSomething"))).where("declaringTypeName", is(jsonText(
//						"io.sapl.spring.method.AbstractPolicyBasedInvocationEnforcementAdviceTests$TestClass")))))));
//	}
//
//	@Test
//	void whenNoActionSetInAttributeAndIsInHttpRequest_thenRetrieveActionConstructsOneFromMethodInvokationWithHttpInfo() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		var attribute = new PreEnforceAttribute((String) null, null, null, null, null);
//		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
//		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
//
//		try (MockedStatic<RequestContextHolder> theMock = mockStatic(RequestContextHolder.class)) {
//			var request = new MockHttpServletRequest();
//			var requestAttributes = mock(ServletRequestAttributes.class);
//			when(requestAttributes.getRequest()).thenReturn(request);
//			theMock.when(() -> RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
//			assertThat((JsonNode) sut.retrieveAction(methodInvocation, attribute, evaluationContext),
//					is(jsonObject().where("http", is(jsonObject()))));
//		}
//	}
//
//	@Test
//	void whenActionIsSetInAttribute_thenRetrieveActionReturnsTheEvaluatedExpression() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		var attribute = new PreEnforceAttribute(null, "'expected action'", null, null, null);
//		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
//		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
//		assertThat((JsonNode) sut.retrieveAction(methodInvocation, attribute, evaluationContext),
//				is(jsonText("expected action")));
//	}
//
//	@Test
//	void whenNoResourceIsSetInAttribute_thenRetrieveResourceConstructsOne() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		var attribute = new PreEnforceAttribute((String) null, null, null, null, null);
//		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomethingWithArgument", "an argument");
//		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
//		assertThat((JsonNode) sut.retrieveResource(methodInvocation, attribute, evaluationContext),
//				is(jsonObject().where("targetClass",
//						is(jsonObject().where("name", is(jsonText(TestClass.class.getName())))
//								.where("canonicalName", is(jsonText(TestClass.class.getCanonicalName())))
//								.where("typeName", is(jsonText(TestClass.class.getTypeName())))
//								.where("simpleName", is(jsonText(TestClass.class.getSimpleName())))))));
//	}
//
//	@Test
//	void whenNoResourceIsSetInAttributeAndItIsInHttpRequest_thenRetrieveResourceConstructsOneWithHttpInfo() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		var attribute = new PreEnforceAttribute((String) null, null, null, null, null);
//		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomethingWithArgument", "an argument");
//		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
//		try (MockedStatic<RequestContextHolder> theMock = mockStatic(RequestContextHolder.class)) {
//			var request = new MockHttpServletRequest();
//			var requestAttributes = mock(ServletRequestAttributes.class);
//			when(requestAttributes.getRequest()).thenReturn(request);
//			theMock.when(() -> RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
//			assertThat((JsonNode) sut.retrieveResource(methodInvocation, attribute, evaluationContext),
//					is(jsonObject().where("http", is(jsonObject()))));
//		}
//	}
//
//	@Test
//	void whenArgumentsInMethodInvocation_thenRetrieveActionAddsThem() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomethingWithArgument", "an argument");
//		assertThat((JsonNode) sut.retrieveAction(methodInvocation),
//				is(jsonObject().where("arguments", is(jsonArray(contains(jsonText("an argument")))))));
//	}
//
//	@Test
//	void whenArgumentsInMethodInvocationThatDoNotMarschalToJson_thenRetrieveActionAddsThemAsNull() {
//		var mapper = new ObjectMapper();
//		SimpleModule module = new SimpleModule();
//		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
//		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
//		module.addSerializer(TestClass.class, new FailingTestSerializer());
//		mapper.registerModule(module);
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, () -> mapper);
//		sut.lazyLoadDependencies();
//		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomethingWithOtherArgument",
//				new TestClass());
//		assertThat((JsonNode) sut.retrieveAction(methodInvocation),
//				is(jsonObject().where("arguments", is(jsonArray(contains(jsonNull()))))));
//	}
//
//	@Test
//	void whenResourceIsSetInAttribute_thenRetrieveResourceReturnsTheEvaluatedExpression() {
//		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
//		sut.lazyLoadDependencies();
//		var attribute = new PreEnforceAttribute(null, null, "'expected resource'", null, null);
//		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
//		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
//		assertThat((JsonNode) sut.retrieveResource(methodInvocation, attribute, evaluationContext),
//				is(jsonText("expected resource")));
//	}
//
//	@JsonComponent
//	public class FailingTestSerializer extends JsonSerializer<TestClass> {
//		@Override
//		public void serialize(TestClass value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
//			throw new IllegalArgumentException("FORCED FAILURE");
//		}
//	}
//
//	static class TestClass {
//		public String doSomething() {
//			return "I did something!";
//		}
//
//		public String doSomethingWithArgument(String arg) {
//			return "I did something with: " + arg;
//		}
//
//		public String doSomethingWithOtherArgument(TestClass arg) {
//			return "I did something with: " + arg;
//		}
//	}
}
