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
package io.sapl.test.verification;

import java.util.LinkedList;
import java.util.List;

import io.sapl.test.mocking.MockCall;
import lombok.AllArgsConstructor;
import lombok.Data;

public class MockRunInformation {

    private final String fullName;

    private final List<CallWithMetadata> timesCalled;

    public MockRunInformation(String fullName) {
        this.fullName    = fullName;
        this.timesCalled = new LinkedList<>();
    }

    public String getFullName() {
        return this.fullName;
    }

    public int getTimesCalled() {
        return timesCalled.size();
    }

    public List<CallWithMetadata> getCalls() {
        return this.timesCalled;
    }

    public void saveCall(MockCall call) {
        this.timesCalled.add(new CallWithMetadata(false, call));
    }

    @Data
    @AllArgsConstructor
    public static class CallWithMetadata {

        private boolean isUsed;

        private MockCall call;

    }

}
