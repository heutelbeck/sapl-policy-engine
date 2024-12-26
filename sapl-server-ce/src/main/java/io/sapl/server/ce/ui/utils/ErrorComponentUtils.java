/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.ui.utils;

import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.theme.lumo.LumoUtility;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class ErrorComponentUtils {

    /**
     * returns a Div-component styled for errors with a specified error message.
     *
     * @param errorMessage the error message to show
     */
    public static Div getErrorDiv(@NonNull String errorMessage) {
        final var error = new Div(new Text(errorMessage));
        error.addClassNames(LumoUtility.TextColor.ERROR_CONTRAST, LumoUtility.Padding.SMALL,
                LumoUtility.Background.ERROR, LumoUtility.BorderRadius.LARGE);
        error.setWhiteSpace(HasText.WhiteSpace.PRE_LINE);
        return error;
    }
}
