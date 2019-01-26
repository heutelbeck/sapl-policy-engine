package io.sapl.spring;

import java.io.Serializable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.sapl.pep.BlockingSAPLAuthorizer;
import io.sapl.pep.SAPLAuthorizer;

@Component
public class SAPLPermissionEvaluator implements PermissionEvaluator {

	private BlockingSAPLAuthorizer sapl;

	@Autowired
	public SAPLPermissionEvaluator(SAPLAuthorizer saplAuthorizer) {
		this.sapl = new BlockingSAPLAuthorizer(saplAuthorizer);
	}

	@Override
	public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
		return sapl.authorize(authentication, permission, targetDomainObject);
	}

	@Override
	public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType,
			Object permissionText) {
		return false;
	}

}
