package io.sapl.springdatar2dbc.sapl.queryTypes.methodNameEnforcement;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
class SqlCondition {
    Conjunction conjunction;
    String      condition;
}
