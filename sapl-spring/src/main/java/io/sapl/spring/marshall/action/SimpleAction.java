package io.sapl.spring.marshall.action;

import io.sapl.spring.marshall.Action;
import lombok.Value;

/**
 * OldStyle
 * @deprecated
 */
@Value
@Deprecated
public class SimpleAction implements Action {

	String method;

}
