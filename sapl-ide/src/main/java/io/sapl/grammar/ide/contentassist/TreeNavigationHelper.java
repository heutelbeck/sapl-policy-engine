/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.grammar.ide.contentassist;

import org.eclipse.emf.ecore.EObject;

/**
 * Helper class that contains methods to navigate the ecore model.
 */
public final class TreeNavigationHelper {

    private TreeNavigationHelper() {

    }

    /**
     * Moves up the model tree and returns the closest parent that matches the given
     * class type.
     *
     * @param <T>       Class type of the searched-for parent.
     * @param object    The current model from which the search starts.
     * @param classType Class type of the searched-for parent.
     * @return Returns the first parent for the given class type, or null if no
     *         match was found.
     */
    public static <T> T goToFirstParent(EObject object, Class<T> classType) {
        if (object == null)
            throw new IllegalArgumentException("object is null.");

        if (classType == null)
            throw new IllegalArgumentException("classType is null.");

        while (object != null) {
            if (classType.isInstance(object))
                return classType.cast(object);

            object = object.eContainer();
        }
        return null;
    }

    /**
     * Moves up the model tree and returns the highest parent that matches the given
     * class type.
     *
     * @param <T>       Class type of the searched-for parent.
     * @param object    The current model from which the search starts.
     * @param classType Class type of the searched-for parent.
     * @return Returns the first parent for the given class type, or null if no
     *         match was found.
     */
    public static <T> T goToLastParent(EObject object, Class<T> classType) {
        if (object == null)
            throw new IllegalArgumentException("object is null.");

        if (classType == null)
            throw new IllegalArgumentException("classType is null.");

        EObject parent = null;

        while (object != null) {
            if (classType.isInstance(object))
                parent = object;

            object = object.eContainer();
        }

        if (parent != null)
            return classType.cast(parent);
        return null;
    }

}
