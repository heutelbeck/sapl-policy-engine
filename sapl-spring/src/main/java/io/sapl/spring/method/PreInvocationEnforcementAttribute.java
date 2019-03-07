package io.sapl.spring.method;

import org.springframework.security.access.ConfigAttribute;

/**
 * Marker interface for attributes which are created from @PreEnforce
 * annotations.
 * <p>
 * Consumed by a {@link PreInvocationEnforcementAdvice}.
 */
public interface PreInvocationEnforcementAttribute extends ConfigAttribute {

}