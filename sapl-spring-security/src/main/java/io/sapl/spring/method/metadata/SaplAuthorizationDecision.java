package io.sapl.spring.method.metadata;

import org.springframework.security.authorization.AuthorizationDecision;

import lombok.Getter;

public class SaplAuthorizationDecision extends AuthorizationDecision {

	@Getter
	SaplAttribute attribute;

	public SaplAuthorizationDecision(boolean granted, SaplAttribute attribute) {
		super(granted);
		this.attribute = attribute;
	}

	@Override
	public String toString() {
		return "SaplAuthorizationDecision(granted=" + this.isGranted() + ", annotation=" + attribute + ")";
	}

}
