package io.sapl.broker.impl.bunker;

public interface Attribute {

    String name();

    int numberOfArguments();

    boolean hasVariableNumberOfArguments();

    String libraryName();

    String documentation();

    String codeTemplate();

}
