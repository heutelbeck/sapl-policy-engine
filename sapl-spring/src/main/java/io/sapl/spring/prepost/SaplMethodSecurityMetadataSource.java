package io.sapl.spring.prepost;

import java.lang.reflect.Method;
import java.util.Collection;

import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.method.AbstractMethodSecurityMetadataSource;

public class SaplMethodSecurityMetadataSource extends AbstractMethodSecurityMetadataSource {

	@Override
	public Collection<ConfigAttribute> getAllConfigAttributes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<ConfigAttribute> getAttributes(Method method, Class<?> targetClass) {
		// TODO Auto-generated method stub
		return null;
	}

}
