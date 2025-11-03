package io.sapl.compiler;

import io.sapl.api.v2.Value;
import lombok.RequiredArgsConstructor;
import org.eclipse.xtext.xbase.controlflow.EvaluationContext;
import reactor.core.publisher.Flux;

public interface CompiledExpression {

    interface PureCompiledExpression extends CompiledExpression {
        public Value evaluate(EvaluationContext context);
    }

    interface AsyncCompiledExpression extends CompiledExpression {
        public Flux<Value> evaluate(EvaluationContext context);
    }

    @RequiredArgsConstructor
    class Constant implements PureCompiledExpression {
        private final Value value;
        @Override
        public Value evaluate(EvaluationContext context) {
            return value;
        }
    }

    class Operator implements PureCompiledExpression {

        @Override
        public Value evaluate(EvaluationContext context) {
            return null;
        }
    }

}
