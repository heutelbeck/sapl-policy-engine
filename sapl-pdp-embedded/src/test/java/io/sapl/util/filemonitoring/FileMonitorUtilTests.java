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
package io.sapl.util.filemonitoring;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.nio.file.Paths;

import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Timeout(3)
class FileMonitorUtilTests {

    @Test
    void resolve_home_folder_in_valid_path() {
        var homePath = String.format("%shome%sjohndoe", File.separator, File.separator);
        System.setProperty("user.home", homePath);

        var path = FileMonitorUtil.resolveHomeFolderIfPresent("~" + File.separator);

        assertThat(path.toAbsolutePath().toString(), containsString(homePath));
    }

    @Test
    void resolve_home_folder_in_path_without_home() {
        var folder = File.separator + "opt" + File.separator;
        var path   = FileMonitorUtil.resolveHomeFolderIfPresent(folder);

        assertThat(path.toString(), is(File.separator + "opt"));
    }

    @Test
    void return_no_event_for_non_existent_directory() {
        Flux<FileEvent> monitorFlux = FileMonitorUtil.monitorDirectory(Paths.get("src/test/resources/not_existing_dir"),
                f -> true);

        StepVerifier.create(monitorFlux).expectNextCount(0L).thenCancel().verify();
        monitorFlux.take(1L).subscribe();
    }

    @Test
    void return_no_event_when_nothing_changes() {
        Flux<FileEvent> monitorFlux = FileMonitorUtil.monitorDirectory(Paths.get("src/test/resources/policies"),
                f -> true);
        StepVerifier.create(monitorFlux).expectNextCount(0L).thenCancel().verify();
    }

    @Test
    void throw_exception_in_monitor_start() throws Exception {
        try (MockedConstruction<FileAlterationMonitor> mocked = Mockito.mockConstruction(FileAlterationMonitor.class,
                (mock, context) -> doThrow(new Exception()).when(mock).start())) {

            Flux<FileEvent> monitorFlux = FileMonitorUtil.monitorDirectory(Paths.get("~/"), f -> true);
            monitorFlux.take(1L).subscribe();
            assertThat(mocked.constructed().size(), is(1));
            verify(mocked.constructed().get(0), times(1)).start();
        }
    }

}
