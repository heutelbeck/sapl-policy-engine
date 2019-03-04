package io.sapl.spring.runas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.intercept.RunAsManagerImpl;
import org.springframework.security.access.intercept.RunAsUserToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import io.sapl.spring.PDPProperties;

public class PolicyEngineRunAsManager extends RunAsManagerImpl {

	private final PDPProperties pdpProperites;

	public PolicyEngineRunAsManager(PDPProperties pdpProperites) {
		super();
		this.pdpProperites = pdpProperites;
	}

	@Override
	public Authentication buildRunAs(Authentication authentication, Object object,
			Collection<ConfigAttribute> attributes) {
		if (!(object instanceof ReflectiveMethodInvocation)
				|| ((ReflectiveMethodInvocation) object).getMethod().getAnnotation(RunAsPolicyEngine.class) == null) {
			return super.buildRunAs(authentication, object, attributes);
		}

		if (pdpProperites.getPolicyEngineAuthority() == null || pdpProperites.getPolicyEngineAuthority().isEmpty()) {
			return null;
		}

		GrantedAuthority runAsAuthority = new SimpleGrantedAuthority(pdpProperites.getPolicyEngineAuthority());
		List<GrantedAuthority> newAuthorities = new ArrayList<GrantedAuthority>();
		newAuthorities.addAll(authentication.getAuthorities());
		newAuthorities.add(runAsAuthority);

		return new RunAsUserToken(getKey(), authentication.getPrincipal(), authentication.getCredentials(),
				newAuthorities, authentication.getClass());
	}
}
