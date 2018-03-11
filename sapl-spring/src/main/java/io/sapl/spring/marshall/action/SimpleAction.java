package io.sapl.spring.marshall.action;

import io.sapl.spring.marshall.Action;
import lombok.Value;

/**
 * @deprecated OldStyle
 */
@Value
@Deprecated
public class SimpleAction implements Action {

	String method;

}
