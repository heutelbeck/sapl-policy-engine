package io.sapl.vaadin.base;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class VaadinAuthorizationSubscriptionBuilderTests {

	MethodSecurityExpressionHandler methodSecurityExpressionHandlerMock;
	private final ObjectMapper mapper = new ObjectMapper();
	VaadinAuthorizationSubscriptionBuilderService sut;
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@BeforeEach
	void setup() {
		methodSecurityExpressionHandlerMock = mock(MethodSecurityExpressionHandler.class);
		sut = new VaadinAuthorizationSubscriptionBuilderService(methodSecurityExpressionHandlerMock, mapper);
	}

	@Test
	void when_evaluateExpressionStringToJson_then_valueToTreeWithInputIsReturned() {
		// GIVEN
		var expressionParser = mock(ExpressionParser.class);
		when(methodSecurityExpressionHandlerMock.getExpressionParser()).thenReturn(expressionParser);
		var expressionMock = mock(Expression.class);
		when(expressionMock.getValue(any(EvaluationContext.class))).thenReturn("1");
		when(expressionParser.parseExpression(any())).thenReturn(expressionMock);

		// WHEN
		JsonNode ret = sut.evaluateExpressionStringToJson("{roles: getAuthorities().![getAuthority()]}", null);
		
		// THEN
		assertEquals(mapper.valueToTree("1"), ret);
	}

	@Test
	void when_evaluateExpressionStringToJsonWithThrownEvaluationException_then_IllegalArgumentExceptionIsGiven() {
		// GIVEN
		var expressionParser = mock(ExpressionParser.class);
		when(methodSecurityExpressionHandlerMock.getExpressionParser()).thenReturn(expressionParser);
		var expressionMock = mock(Expression.class);
		when(expressionMock.getValue(any(EvaluationContext.class))).thenThrow(EvaluationException.class);
		when(expressionParser.parseExpression(any())).thenReturn(expressionMock);
		when(expressionMock.getExpressionString()).thenReturn("roles: getAuthorities().![getAuthority()]}");

		// WHEN
		var exception = assertThrows(IllegalArgumentException.class, () -> sut.evaluateExpressionStringToJson("{roles: getAuthorities().![getAuthority()]}", null));
		// THEN
		assertEquals("Failed to evaluate expression 'roles: getAuthorities().![getAuthority()]}'",
				exception.getMessage());
	}

	@Test
	void when_retrieveSubjectCredentials_then_SubjectIsReturnedWithoutCredentials() {
		// GIVEN
		var authReq = new UsernamePasswordAuthenticationToken("user", "pass");

		// WHEN
		var ret = sut.retrieveSubject(authReq);

		// THEN
		assertEquals(mapper.valueToTree("user"), ret.get("principal"));
		assertNull(ret.get("credentials"));
	}

	@Test
	void when_retrieveSubjectWithPasswordAndCredentials_then_SubjectIsReturnedWithoutBoth() {
		// GIVEN
		var subject = mapper.createObjectNode();
		subject.set("password", mapper.convertValue("password", JsonNode.class));
		subject.set("user", mapper.convertValue("user", JsonNode.class));
		var authReq = new UsernamePasswordAuthenticationToken(subject, "pass");

		// WHEN
		var ret = sut.retrieveSubject(authReq);

		// THEN
		assertNull(ret.get("principal").get("password"));
		assertNull(ret.get("credentials"));
		var expected = mapper.createObjectNode();
		expected.set("user", mapper.convertValue("user", JsonNode.class));
		assertEquals(expected, ret.get("principal"));
	}

	@Test
	void when_retrieveSubjectWithPasswordAndCredentialsWithoutExpression_then_SubjectIsReturnedWithoutBoth() {
		// GIVEN
		var subject = mapper.createObjectNode();
		subject.set("password", mapper.convertValue("password", JsonNode.class));
		subject.set("user", mapper.convertValue("user", JsonNode.class));
		var authReq = new UsernamePasswordAuthenticationToken(subject, "pass");

		// WHEN
		var ret = sut.retrieveSubject(authReq, null);

		// THEN
		assertNull(ret.get("principal").get("password"));
		assertNull(ret.get("credentials"));
		var expected = mapper.createObjectNode();
		expected.set("user", mapper.convertValue("user", JsonNode.class));
		assertEquals(expected, ret.get("principal"));
	}

	@Test
	void when_retrieveSubjectWithPasswordAndCredentialsWithEmptyExpression_then_SubjectIsReturnedWithoutBoth() {
		// GIVEN
		var subject = mapper.createObjectNode();
		subject.set("password", mapper.convertValue("password", JsonNode.class));
		subject.set("user", mapper.convertValue("user", JsonNode.class));
		var authReq = new UsernamePasswordAuthenticationToken(subject, "pass");

		// WHEN
		var ret = sut.retrieveSubject(authReq, "");

		// THEN
		assertNull(ret.get("principal").get("password"));
		assertNull(ret.get("credentials"));
		var expected = mapper.createObjectNode();
		expected.set("user", mapper.convertValue("user", JsonNode.class));
		assertEquals(expected, ret.get("principal"));
	}

	@Test
	void when_retrieveSubjectWithExpression_then_valueToTreeWithInputIsReturned() {
		// GIVEN
		var subject = mapper.createObjectNode();
		subject.set("password", mapper.convertValue("password", JsonNode.class));
		subject.set("user", mapper.convertValue("user", JsonNode.class));
		var authReq = new UsernamePasswordAuthenticationToken(subject, "pass");

		var expressionParser = mock(ExpressionParser.class);
		when(methodSecurityExpressionHandlerMock.getExpressionParser()).thenReturn(expressionParser);
		var expressionMock = mock(Expression.class);
		when(expressionMock.getValue(any(EvaluationContext.class))).thenReturn("1");
		when(expressionParser.parseExpression(any())).thenReturn(expressionMock);

		// WHEN
		var ret = sut.retrieveSubject(authReq, "{roles: getAuthorities().![getAuthority()]}");
		
		// THEN
		assertEquals(mapper.valueToTree("1"), ret);
	}

	@Test
	void when_serializeTargetClassDescription() {
		// GIVEN
		// WHEN
		var ret = VaadinAuthorizationSubscriptionBuilderService.serializeTargetClassDescription(getClass());
		
		// THEN
		var subject = mapper.createObjectNode();
		subject.set("name", JSON.textNode("io.sapl.vaadin.base.VaadinAuthorizationSubscriptionBuilderTests"));
		subject.set("canonicalName", JSON.textNode("io.sapl.vaadin.base.VaadinAuthorizationSubscriptionBuilderTests"));
		subject.set("typeName", JSON.textNode("io.sapl.vaadin.base.VaadinAuthorizationSubscriptionBuilderTests"));
		subject.set("simpleName", JSON.textNode("VaadinAuthorizationSubscriptionBuilderTests"));
		assertEquals(subject, ret);
	}

}
