package io.sapl.spring.method.blocking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import io.sapl.spring.subscriptions.WebAuthorizationSubscriptionBuilderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestSetupUtil {

	public static ObjectMapper objectMapperWithSerializers() {
		var module = new SimpleModule();
		module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
		module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
		module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
		var mapper = new ObjectMapper();
		mapper.registerModule(module);
		return mapper;
	}

	@SuppressWarnings("unchecked")
	public static WebAuthorizationSubscriptionBuilderService subsctiptionBuilderService() {
		var mapper                        = objectMapperWithSerializers();
		var mockExpressionHandlerProvider = mock(ObjectProvider.class);
		when(mockExpressionHandlerProvider.getIfAvailable(any()))
				.thenReturn(new DefaultMethodSecurityExpressionHandler());
		var mockMapperProvider = mock(ObjectProvider.class);
		when(mockMapperProvider.getIfAvailable(any())).thenReturn(mapper);
		var mockDefaultsProvider = mock(ObjectProvider.class);
		var mockContext          = mock(ApplicationContext.class);
		return new WebAuthorizationSubscriptionBuilderService(mockExpressionHandlerProvider, mockMapperProvider,
				mockDefaultsProvider, mockContext);
	}
}
