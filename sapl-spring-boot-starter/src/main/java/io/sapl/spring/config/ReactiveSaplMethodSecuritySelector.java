/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.config;

import lombok.NonNull;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds ReactiveSaplMethodSecurityConfiguration to the imports
 * if @EnableReactiveSaplMethodSecurity is present.
 */
public class ReactiveSaplMethodSecuritySelector extends AdviceModeImportSelector<EnableReactiveSaplMethodSecurity> {

    private static final String ERROR_ADVICE_MODE_NOT_SUPPORTED = "AdviceMode %s is not supported";

    @Override
    protected String[] selectImports(@NonNull AdviceMode adviceMode) {
        if (adviceMode == AdviceMode.PROXY) {
            return getProxyImports();
        }
        throw new IllegalStateException(ERROR_ADVICE_MODE_NOT_SUPPORTED.formatted(adviceMode));
    }

    /**
     * Return the imports to use if the {@link AdviceMode} is set to
     * {@link AdviceMode#PROXY}.
     * <p>
     * Take care of adding the necessary JSR-107 import if it is available.
     */
    private String[] getProxyImports() {
        List<String> result = new ArrayList<>();
        result.add(ReactiveSaplMethodSecurityConfiguration.class.getName());
        result.add(SaplTransactionManagementConfiguration.class.getName());
        return result.toArray(new String[0]);
    }

}
