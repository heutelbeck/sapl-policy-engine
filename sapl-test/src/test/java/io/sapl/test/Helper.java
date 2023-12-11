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
package io.sapl.test;

import static org.mockito.Mockito.mock;

import java.util.List;
import org.eclipse.emf.common.util.EList;
import org.mockito.AdditionalAnswers;

public class Helper {
    public static <T> EList<T> mockEList(List<T> delegate) {
        return mock(EList.class, AdditionalAnswers.delegatesTo(delegate));
    }
}
