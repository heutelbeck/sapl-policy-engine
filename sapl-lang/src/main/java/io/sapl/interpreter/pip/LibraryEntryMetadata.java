/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.interpreter.pip;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

public interface LibraryEntryMetadata {

    String getLibraryName();

    String getFunctionName();

    String getCodeTemplate();

    String getDocumentationCodeTemplate();

    Method getFunction();

    boolean isVarArgsParameters();

    int getNumberOfParameters();

    default String fullyQualifiedName() {
        return getLibraryName() + '.' + getFunctionName();
    }

    default String getParameterName(int index) {
        return getFunction().getParameters()[index].getName();
    }

    default List<Annotation> getValidationAnnotationsOfParameter(int index) {
        var annotations           = getFunction().getParameters()[index].getAnnotations();
        var validationAnnotations = new ArrayList<Annotation>(annotations.length);
        for (var annotation : annotations)
            if (isValidationAnnotation(annotation))
                validationAnnotations.add(annotation);
        return validationAnnotations;
    }

    default boolean isValidationAnnotation(Annotation annotation) {
        for (var saplType : ValidationTypes.VALIDATION_ANNOTATION_TYPES)
            if (saplType.isAssignableFrom(annotation.getClass()))
                return true;
        return false;
    }

    default String describeParameterForDocumentation(int index) {
        return describeParameterForDocumentation(getParameterName(index), getValidationAnnotationsOfParameter(index));
    }

    default String describeParameterForDocumentation(String name, List<Annotation> types) {
        if (types.isEmpty())
            return name;
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        var numberOfTypes = types.size();
        for (var i = 0; i < numberOfTypes; i++) {
            sb.append(types.get(i).annotationType().getSimpleName());
            if (i < numberOfTypes - 1)
                sb.append('|');
        }
        sb.append(' ').append(name).append(')');
        return sb.toString();
    }

    default void appendParameterList(StringBuilder sb, int parameterOffset,
            IntFunction<String> parameterStringBuilder) {
        if (isVarArgsParameters())
            sb.append('(').append(parameterStringBuilder.apply(parameterOffset)).append("...)");
        else if (getNumberOfParameters() > 0) {
            sb.append('(');
            for (var i = 0; i < getNumberOfParameters(); i++) {
                sb.append(parameterStringBuilder.apply(parameterOffset++));
                if (i < getNumberOfParameters() - 1)
                    sb.append(", ");
            }
            sb.append(')');
        }
    }

}
