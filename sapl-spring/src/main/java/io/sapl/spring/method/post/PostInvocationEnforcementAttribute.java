package io.sapl.spring.method.post;

import io.sapl.spring.method.EnforcementAttribute;
import io.sapl.spring.method.pre.PreInvocationEnforcementAdvice;

/**
 * Marker interface for attributes which are created from @PreEnforce annotations.
 * <p>
 * Consumed by a {@link PreInvocationEnforcementAdvice}.
 */
public interface PostInvocationEnforcementAttribute extends EnforcementAttribute {

}