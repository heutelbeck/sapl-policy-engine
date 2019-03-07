package io.sapl.spring.method;

import org.springframework.aop.framework.AopInfrastructureBean;

public interface PolicyEnforcementAttributeFactory extends AopInfrastructureBean {
	PreInvocationEnforcementAttribute createPreInvocationAttribute(String subjectAttribute, String actionAttribute,
			String resourceAttribute, String environmentAttribute);

	PostInvocationEnforcementAttribute createPostInvocationAttribute(String subjectAttribute, String actionAttribute,
			String resourceAttribute, String environmentAttribute);
}
