/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.filesystem;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import io.sapl.util.filemonitoring.FileMonitorUtil;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class FileSystemPrpUpdateEventSourceTests {

    @Test
    void testProcessFileEvent() {
        var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/empty", new DefaultSAPLInterpreter());

        var file1 = Paths.get("/file1.sapl");
        var file2 = Paths.get("/file2.sapl");

        try (MockedConstruction<ImmutableFileIndex> mocked = Mockito.mockConstruction(ImmutableFileIndex.class,
                (mock, context) -> {
                    when(mock.afterFileEvent(any())).thenReturn(mock);
                    when(mock.getUpdateEvent()).thenReturn(new PrpUpdateEvent(Collections.emptyList()));
                })) {

            try (MockedStatic<FileMonitorUtil> mock = mockStatic(FileMonitorUtil.class)) {
                var eventFlux = Flux.just(new FileCreatedEvent(file1), new FileDeletedEvent(file2));
                mock.when(() -> FileMonitorUtil.monitorDirectory(any(), any())).thenReturn(eventFlux);

                var updates = source.getUpdates();
                StepVerifier.create(updates).expectNextCount(2L).thenCancel().verify();

                mock.verify(() -> FileMonitorUtil.monitorDirectory(any(), any()), times(1));
            }
        }

        source.dispose();
    }

}
