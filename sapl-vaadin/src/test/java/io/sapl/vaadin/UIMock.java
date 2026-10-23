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
package io.sapl.vaadin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;

public class UIMock {
    public static UI getMockedUI() {
        UI mockedUI = mock(UI.class);
        doAnswer(invocationOnMock -> {
            var f = (Command) invocationOnMock.getArguments()[0];
            f.execute();
            return null;
        }).when(mockedUI).access(any(Command.class));
        return mockedUI;
    }
}
