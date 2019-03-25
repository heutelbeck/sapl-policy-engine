package io.sapl.spring.method.pre;

import io.sapl.spring.method.EnforcementAttribute;

/**
 * Marker interface for attributes which are created from @PreEnforce
 * annotations.
 * <p>
 * Consumed by a {@link PreInvocationEnforcementAdvice}.
 */
public interface PreInvocationEnforcementAttribute extends EnforcementAttribute {

}