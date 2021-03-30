package io.sapl.spring.method;

import static com.spotify.hamcrest.jackson.IsJsonArray.jsonArray;
import static com.spotify.hamcrest.jackson.IsJsonNull.jsonNull;
import static com.spotify.hamcrest.jackson.IsJsonObject.jsonObject;
import static com.spotify.hamcrest.jackson.IsJsonText.jsonText;
import static com.spotify.hamcrest.optional.OptionalMatchers.emptyOptional;
import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.util.MethodInvocationUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintHandlerService;
import io.sapl.spring.method.pre.PolicyBasedPreInvocationEnforcementAttribute;
import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;

class AbstractPolicyBasedInvocationEnforcementAdviceTests {

	ObjectFactory<PolicyDecisionPoint> pdpFactory;
	ObjectFactory<ConstraintHandlerService> constraintHandlerFactory;
	ObjectFactory<ObjectMapper> objectMapperFactory;

	PolicyDecisionPoint pdp;
	ConstraintHandlerService constraintHandlers;
	Authentication authentication;
	AbstractPolicyBasedInvocationEnforcementAdvice sut;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void beforeEach() {
		pdp = mock(PolicyDecisionPoint.class);
		pdpFactory = (ObjectFactory<PolicyDecisionPoint>) mock(ObjectFactory.class);
		when(pdpFactory.getObject()).thenReturn(pdp);

		constraintHandlers = mock(ConstraintHandlerService.class);
		constraintHandlerFactory = (ObjectFactory<ConstraintHandlerService>) mock(ObjectFactory.class);
		when(constraintHandlerFactory.getObject()).thenReturn(constraintHandlers);

		var mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		mapper.registerModule(module);
		objectMapperFactory = (ObjectFactory<ObjectMapper>) mock(ObjectFactory.class);
		when(objectMapperFactory.getObject()).thenReturn(mapper);

		authentication = new UsernamePasswordAuthenticationToken("principal", "credentials");

	}

	static class TestAdviceEnforcementAdvice extends AbstractPolicyBasedInvocationEnforcementAdvice {
		public TestAdviceEnforcementAdvice(ObjectFactory<PolicyDecisionPoint> pdpFactory,
				ObjectFactory<ConstraintHandlerService> constraintHandlerFactory,
				ObjectFactory<ObjectMapper> objectMapperFactory) {
			super(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		}
	}

	@Test
	void whenSettingExpressionHandler_thenFieldIsSet() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		var expressionHandler = mock(MethodSecurityExpressionHandler.class);
		sut.setExpressionHandler(expressionHandler);
		assertThat(sut.expressionHandler, is(expressionHandler));
	}

	@Test
	void whenLazyLoading_thenFactoriesCalledOnlyOnce() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		sut.lazyLoadDependencies();
		verify(pdpFactory, times(1)).getObject();
		verify(constraintHandlerFactory, times(1)).getObject();
		verify(objectMapperFactory, times(1)).getObject();
	}

	@Test
	void whenNoSubjectSetInAttribute_thenRetrieveSubjectReturnsTheAuthentication() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		assertThat(sut.retrieveSubject(authentication, attribute, null), is(authentication));
	}

	@Test
	void whenSubjectSetInAttribute_thenRetrieveSubjectReturnsTheEvaluatedExpression() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute("'expected subject'", null, null, null);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
		assertThat((JsonNode) sut.retrieveSubject(authentication, attribute, evaluationContext),
				is(jsonText("expected subject")));
	}

	@Test
	void whenSerializingMethod_thenMethodIsDescribedInJson() {
		var method = MethodInvocationUtils.create(new TestClass(), "doSomething").getMethod();
		assertThat(AbstractPolicyBasedInvocationEnforcementAdvice.serializeMethod(method), is(jsonObject()
				.where("name", is(jsonText("doSomething"))).where("returnType", is(jsonText("java.lang.String")))
				.where("declaringClass", is(jsonText(
						"io.sapl.spring.method.AbstractPolicyBasedInvocationEnforcementAdviceTests$TestClass")))));
	}

	@Test
	void whenSerializingClass_thenClassIsDescribedInJson() {
		assertThat(AbstractPolicyBasedInvocationEnforcementAdvice.serializeTargetClassDescription(TestClass.class),
				is(jsonObject().where("name", is(jsonText(TestClass.class.getName())))
						.where("canonicalName", is(jsonText(TestClass.class.getCanonicalName())))
						.where("typeName", is(jsonText(TestClass.class.getTypeName())))
						.where("simpleName", is(jsonText(TestClass.class.getSimpleName())))));
	}

	@Test
	void whenExpressionThrowsEvaluationException_thenThrowIllegalArgumentException() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		Expression expression = mock(Expression.class);
		when(expression.getValue((EvaluationContext) any())).thenThrow(new EvaluationException("FORCED EXCEPTION"));
		assertThrows(IllegalArgumentException.class, () -> sut.evaluateToJson(expression, null));
	}

	@Test
	void whenHttpRequestInContext_thenRetrieveIt() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		try (MockedStatic<RequestContextHolder> theMock = mockStatic(RequestContextHolder.class)) {
			var request = new MockHttpServletRequest();
			var requestAttributes = mock(ServletRequestAttributes.class);
			when(requestAttributes.getRequest()).thenReturn(request);
			theMock.when(() -> RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
			assertThat(TestAdviceEnforcementAdvice.retrieveRequestObject(), is(optionalWithValue(equalTo(request))));
		}
	}

	@Test
	void whenHttpRequestInContextButNullAttribute_thenRetrieveOptionalEmpty() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		try (MockedStatic<RequestContextHolder> theMock = mockStatic(RequestContextHolder.class)) {
			theMock.when(() -> RequestContextHolder.getRequestAttributes()).thenReturn(null);
			assertThat(TestAdviceEnforcementAdvice.retrieveRequestObject(), is(emptyOptional()));
		}
	}

	@Test
	void whenNoEnvironmentSetInAttribute_thenRetrieveEnvironmentReturnsTheAuthentication() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		assertThat(sut.retrieveEnvironment(attribute, null), nullValue());
	}

	@Test
	void whenEnvironemntSetInAttribute_thenRetrieveEnvironmentReturnsTheEvaluatedExpression() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute(null, null, null, "'expected environment'");
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
		assertThat((JsonNode) sut.retrieveEnvironment(attribute, evaluationContext),
				is(jsonText("expected environment")));
	}

	@Test
	void whenNoActionSetInAttribute_thenRetrieveActionConstructsOneFromMethodInvokation() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
		assertThat((JsonNode) sut.retrieveAction(methodInvocation, attribute, evaluationContext), is(jsonObject().where(
				"java",
				is(jsonObject().where("name", is(jsonText("doSomething"))).where("declaringTypeName", is(jsonText(
						"io.sapl.spring.method.AbstractPolicyBasedInvocationEnforcementAdviceTests$TestClass")))))));
	}

	@Test
	void whenNoActionSetInAttributeAndIsInHttpRequest_thenRetrieveActionConstructsOneFromMethodInvokationWithHttpInfo() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);

		try (MockedStatic<RequestContextHolder> theMock = mockStatic(RequestContextHolder.class)) {
			var request = new MockHttpServletRequest();
			var requestAttributes = mock(ServletRequestAttributes.class);
			when(requestAttributes.getRequest()).thenReturn(request);
			theMock.when(() -> RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
			assertThat((JsonNode) sut.retrieveAction(methodInvocation, attribute, evaluationContext),
					is(jsonObject().where("http", is(jsonObject()))));
		}
	}

	@Test
	void whenActionIsSetInAttribute_thenRetrieveActionReturnsTheEvaluatedExpression() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute(null, "'expected action'", null, null);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
		assertThat((JsonNode) sut.retrieveAction(methodInvocation, attribute, evaluationContext),
				is(jsonText("expected action")));
	}

	@Test
	void whenNoResourceIsSetInAttribute_thenRetrieveResourceConstructsOne() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomethingWithArgument", "an argument");
		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
		assertThat((JsonNode) sut.retrieveResource(methodInvocation, attribute, evaluationContext),
				is(jsonObject().where("targetClass",
						is(jsonObject().where("name", is(jsonText(TestClass.class.getName())))
								.where("canonicalName", is(jsonText(TestClass.class.getCanonicalName())))
								.where("typeName", is(jsonText(TestClass.class.getTypeName())))
								.where("simpleName", is(jsonText(TestClass.class.getSimpleName())))))));
	}

	@Test
	void whenNoResourceIsSetInAttributeAndItIsInHttpRequest_thenRetrieveResourceConstructsOneWithHttpInfo() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute((String) null, null, null, null);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomethingWithArgument", "an argument");
		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
		try (MockedStatic<RequestContextHolder> theMock = mockStatic(RequestContextHolder.class)) {
			var request = new MockHttpServletRequest();
			var requestAttributes = mock(ServletRequestAttributes.class);
			when(requestAttributes.getRequest()).thenReturn(request);
			theMock.when(() -> RequestContextHolder.getRequestAttributes()).thenReturn(requestAttributes);
			assertThat((JsonNode) sut.retrieveResource(methodInvocation, attribute, evaluationContext),
					is(jsonObject().where("http", is(jsonObject()))));
		}
	}

	@Test
	void whenArgumentsInMethodInvocation_thenRetrieveActionAddsThem() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomethingWithArgument", "an argument");
		assertThat((JsonNode) sut.retrieveAction(methodInvocation),
				is(jsonObject().where("arguments", is(jsonArray(contains(jsonText("an argument")))))));
	}

	@Test
	void whenArgumentsInMethodInvocationThatDoNotMarschalToJson_thenRetrieveActionAddsThemAsNull() {
		var mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		module.addSerializer(TestClass.class, new FailingTestSerializer());
		mapper.registerModule(module);
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, () -> mapper);
		sut.lazyLoadDependencies();
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomethingWithOtherArgument",
				new TestClass());
		assertThat((JsonNode) sut.retrieveAction(methodInvocation),
				is(jsonObject().where("arguments", is(jsonArray(contains(jsonNull()))))));
	}

	@Test
	void whenResourceIsSetInAttribute_thenRetrieveResourceReturnsTheEvaluatedExpression() {
		var sut = new TestAdviceEnforcementAdvice(pdpFactory, constraintHandlerFactory, objectMapperFactory);
		sut.lazyLoadDependencies();
		var attribute = new PolicyBasedPreInvocationEnforcementAttribute(null, null, "'expected resource'", null);
		var methodInvocation = MethodInvocationUtils.create(new TestClass(), "doSomething");
		var evaluationContext = sut.expressionHandler.createEvaluationContext(authentication, methodInvocation);
		assertThat((JsonNode) sut.retrieveResource(methodInvocation, attribute, evaluationContext),
				is(jsonText("expected resource")));
	}

	@JsonComponent
	public class FailingTestSerializer extends JsonSerializer<TestClass> {
		@Override
		public void serialize(TestClass value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
			throw new IllegalArgumentException("FORCED FAILURE");
		}
	}

	static class TestClass {
		public String doSomething() {
			return "I did something!";
		}

		public String doSomethingWithArgument(String arg) {
			return "I did something with: " + arg;
		}

		public String doSomethingWithOtherArgument(TestClass arg) {
			return "I did something with: " + arg;
		}
	}
}
