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
package io.sapl.test.verification;

import org.assertj.core.api.Assertions;
import org.hamcrest.Matcher;

/**
 * Verify that this mock was called n times.
 *
 */
public class TimesCalledVerification implements MockingVerification {

    private static final String ERROR_TIMES_VERIFICATION = "Error verifying the expected number of calls to the mock \"%s\" - Expected: \"%s\" - got: \"%s\"";

    final Matcher<Integer> matcher;

    public TimesCalledVerification(Matcher<Integer> matcher) {
        this.matcher = matcher;
    }

    @Override
    public void verify(MockRunInformation mockRunInformation) {
        this.verify(mockRunInformation, null);
    }

    @Override
    public void verify(MockRunInformation mockRunInformation, String verificationFailedMessage) {

        String message;
        if (verificationFailedMessage != null && !verificationFailedMessage.isEmpty()) {
            message = verificationFailedMessage;
        } else {
            message = String.format(ERROR_TIMES_VERIFICATION, mockRunInformation.getFullName(), this.matcher.toString(),
                    mockRunInformation.getTimesCalled());
        }

        Assertions.assertThat(this.matcher.matches(mockRunInformation.getTimesCalled())).as(message).isTrue();
    }

}
