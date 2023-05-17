package io.sapl.spring.method.blocking;

import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;

import lombok.Getter;

public enum SaplAuthorizationInterceptorsOrder {
	PRE_ENFORCE(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder() - 50),
	POST_ENFORCE(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder() - 40);

	@Getter
	private final int order;

	SaplAuthorizationInterceptorsOrder(int order) {
		this.order = order;
	}
}
