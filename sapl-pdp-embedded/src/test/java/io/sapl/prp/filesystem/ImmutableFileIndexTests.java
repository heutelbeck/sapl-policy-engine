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

import static com.spotify.hamcrest.pojo.IsPojo.pojo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.spotify.hamcrest.pojo.IsPojo;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.util.filemonitoring.FileChangedEvent;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;

class ImmutableFileIndexTests {

    private static final SAPLInterpreter INTERPERETER  = new DefaultSAPLInterpreter();
    private static final String          POLICY_1      = "policy \"policy1\" permit";
    private static final String          POLICY_1_NAME = "policy1";
    private static final SAPL            SAPL_1        = INTERPERETER.parse(POLICY_1);
    private static final String          POLICY_2      = "policy \"policy2\" permit";
    private static final String          POLICY_2_NAME = "policy2";
    private static final SAPL            SAPL_2        = INTERPERETER.parse(POLICY_2);
    private static final String          PATH          = "/";

    @Test
    @Disabled("Was very specifically mocking old file traversal code")
    void when_initializingWithEmptyDirectory_then_updatesAreEmpty() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            var mockPaths           = List.of();
            var mockDirectoryStream = mock(DirectoryStream.class);
            when(mockDirectoryStream.iterator()).thenReturn(mockPaths.iterator());
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenReturn(mockDirectoryStream);
            var sut           = new ImmutableFileIndex(PATH, INTERPERETER);
            var actualUpdates = sut.getUpdateEvent();
            assertThat(actualUpdates.getUpdates(), is(emptyArray()));
        }
    }

    @Test
    void when_initializingWithDirectoryThatCannotBeOpened_then_updatesContainOnlyInconsistent() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenThrow(new IOException());
            var sut           = new ImmutableFileIndex(PATH, INTERPERETER);
            var actualUpdates = sut.getUpdateEvent();
            assertThat(actualUpdates.getUpdates(), is(arrayWithSize(1)));
            assertThat(actualUpdates.getUpdates(), arrayContainingInAnyOrder(
                    pojo(Update.class).withProperty("type", is(PrpUpdateEvent.Type.INCONSISTENT))));
        }
    }

    @Test
    void when_initializingWithDirectoryThatContainsUnreadableFile_then_updatesContainOnlyInconsistent() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            var mockPath = mock(Path.class);
            when(mockPath.toAbsolutePath()).thenReturn(mockPath);
            when(mockPath.toString()).thenReturn("mockedPath");
            var mockPaths           = List.of(mockPath);
            var mockDirectoryStream = mock(DirectoryStream.class);
            when(mockDirectoryStream.iterator()).thenReturn(mockPaths.iterator());
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenReturn(mockDirectoryStream);
            mockedFiles.when(() -> Files.readString(any())).thenThrow(new IOException());
            var sut           = new ImmutableFileIndex(PATH, INTERPERETER);
            var actualUpdates = sut.getUpdateEvent();
            assertThat(actualUpdates.getUpdates(), is(arrayWithSize(1)));
            assertThat(actualUpdates.getUpdates(),
                    arrayContainingInAnyOrder(isUpdateType(PrpUpdateEvent.Type.INCONSISTENT)));
        }
    }

    @Test
    @Disabled("Was very specifically mocking old file traversal code")
    void when_initializingWithDirectoryThatContainsValidSaplFile_then_updatesContainDocument() {
        var policy1 = POLICY_1;
        var sapl1   = INTERPERETER.parse(policy1);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            var mockPath = mock(Path.class);
            when(mockPath.toAbsolutePath()).thenReturn(mockPath);
            when(mockPath.toString()).thenReturn("mockedPath");
            var mockPaths           = List.of(mockPath);
            var mockDirectoryStream = mock(DirectoryStream.class);
            when(mockDirectoryStream.iterator()).thenReturn(mockPaths.iterator());
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenReturn(mockDirectoryStream);

            mockedFiles.when(() -> Files.readString(eq(mockPath))).thenReturn(policy1);
            var mockInterpreter = mock(SAPLInterpreter.class);
            when(mockInterpreter.parse(policy1)).thenReturn(sapl1);

            var sut           = new ImmutableFileIndex(PATH, mockInterpreter);
            var actualUpdates = sut.getUpdateEvent();

            assertThat(actualUpdates.getUpdates(), is(arrayWithSize(1)));
            // @formatter:off
			assertThat(actualUpdates.getUpdates(),
					arrayContainingInAnyOrder(
							isUpdateWithName(PrpUpdateEvent.Type.PUBLISH, POLICY_1_NAME)
					));
			// @formatter:on
        }
    }

    @Test
    void when_initializingWithEmptyDirectory_and_update_then_updatePublish() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            // Setup initial Index
            var mockPaths           = List.of();
            var mockDirectoryStream = mock(DirectoryStream.class);
            when(mockDirectoryStream.iterator()).thenReturn(mockPaths.iterator());
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenReturn(mockDirectoryStream);

            // 1st event load valid policy
            var mockInterpreter = mock(SAPLInterpreter.class);
            var mockFile        = mockPolicyFile(POLICY_1, SAPL_1, POLICY_1_NAME, mockedFiles, mockInterpreter);
            var event           = mock(FileCreatedEvent.class);
            when(event.file()).thenReturn(mockFile);

            // act
            var sut = new ImmutableFileIndex(PATH, mockInterpreter);
            sut = sut.afterFileEvent(event);
            var actualUpdates = sut.getUpdateEvent();

            // validate
            // @formatter:off
			assertThat(actualUpdates.getUpdates(),
					arrayContainingInAnyOrder(
							isUpdateWithName(PrpUpdateEvent.Type.PUBLISH, POLICY_1_NAME)
					));
			// @formatter:on
        }
    }

    @Test
    void when_initializingWithEmptyDirectory_and_updateAddAndRemove_then_updateWithdraw() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            // Setup initial Index
            var mockPaths           = List.of();
            var mockDirectoryStream = mock(DirectoryStream.class);
            when(mockDirectoryStream.iterator()).thenReturn(mockPaths.iterator());
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenReturn(mockDirectoryStream);

            // 1st event load valid policy
            var mockInterpreter = mock(SAPLInterpreter.class);
            var mockFile        = mockPolicyFile(POLICY_1, SAPL_1, POLICY_1_NAME, mockedFiles, mockInterpreter);

            var sut = new ImmutableFileIndex(PATH, mockInterpreter);

            var event1 = mock(FileCreatedEvent.class);
            when(event1.file()).thenReturn(mockFile);
            sut = sut.afterFileEvent(event1);

            // remove document again

            var event2 = mock(FileDeletedEvent.class);
            when(event2.file()).thenReturn(mockFile);
            sut = sut.afterFileEvent(event2);

            var actualUpdates = sut.getUpdateEvent();

            // validate
            // @formatter:off
			assertThat(actualUpdates.getUpdates(),
					arrayContainingInAnyOrder(
							isUpdateWithName(PrpUpdateEvent.Type.WITHDRAW, POLICY_1_NAME)
					));
			// @formatter:on
        }
    }

    @Test
    void when_initializingWithEmptyDirectory_and_updateAddAndChange_then_update() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            // Setup initial Index
            var mockPaths           = List.of();
            var mockDirectoryStream = mock(DirectoryStream.class);
            when(mockDirectoryStream.iterator()).thenReturn(mockPaths.iterator());
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenReturn(mockDirectoryStream);

            // 1st event load valid policy
            var mockInterpreter = mock(SAPLInterpreter.class);
            var mockFile        = mockPolicyFile(POLICY_1, SAPL_1, POLICY_1_NAME, mockedFiles, mockInterpreter);

            var sut = new ImmutableFileIndex(PATH, mockInterpreter);

            var event1 = mock(FileCreatedEvent.class);
            when(event1.file()).thenReturn(mockFile);
            sut = sut.afterFileEvent(event1);

            // change document again

            var event2 = mock(FileChangedEvent.class);
            when(event2.file()).thenReturn(mockFile);
            sut = sut.afterFileEvent(event2);

            var actualUpdates = sut.getUpdateEvent();

            // validate
            // @formatter:off
			assertThat(actualUpdates.getUpdates(),
					arrayContaining(
							isUpdateWithName(PrpUpdateEvent.Type.WITHDRAW, POLICY_1_NAME),
							isUpdateWithName(PrpUpdateEvent.Type.PUBLISH, POLICY_1_NAME)
					));
			// @formatter:on
        }
    }

    @Test
    void when_initializingWithEmptyDirectory_and_updateAddAndAddNameCollision_then_Inconsistent() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            // Setup initial Index
            var mockPaths           = List.of();
            var mockDirectoryStream = mock(DirectoryStream.class);
            when(mockDirectoryStream.iterator()).thenReturn(mockPaths.iterator());
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenReturn(mockDirectoryStream);

            // 1st event load valid policy
            var mockInterpreter = mock(SAPLInterpreter.class);
            var mockFile        = mockPolicyFile(POLICY_1, SAPL_1, POLICY_1_NAME, mockedFiles, mockInterpreter);

            var sut = new ImmutableFileIndex(PATH, mockInterpreter);

            var event1 = mock(FileCreatedEvent.class);
            when(event1.file()).thenReturn(mockFile);
            sut = sut.afterFileEvent(event1);

            // create policy with name collision
            var mockFile2 = mockPolicyFile(POLICY_1, SAPL_1, "alternatePath", mockedFiles, mockInterpreter);
            var event2    = mock(FileCreatedEvent.class);
            when(event2.file()).thenReturn(mockFile2);
            sut = sut.afterFileEvent(event2);

            var actualUpdates = sut.getUpdateEvent();

            // validate
            // @formatter:off
			assertThat(actualUpdates.getUpdates(),
					arrayContaining(
							isUpdateType(PrpUpdateEvent.Type.INCONSISTENT)
					));
			// @formatter:on
        }
    }

    @Test
    void when_initializingWithEmptyDirectory_and_updateAddAndAddNameCollisionAddAnotherUnrelatedPolicy_then_PublishNewOne() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            // Setup initial Index
            var mockPaths           = List.of();
            var mockDirectoryStream = mock(DirectoryStream.class);
            when(mockDirectoryStream.iterator()).thenReturn(mockPaths.iterator());
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenReturn(mockDirectoryStream);

            // 1st event load valid policy
            var mockInterpreter = mock(SAPLInterpreter.class);
            var mockFile        = mockPolicyFile(POLICY_1, SAPL_1, POLICY_1_NAME, mockedFiles, mockInterpreter);

            var sut = new ImmutableFileIndex(PATH, mockInterpreter);

            var event1 = mock(FileCreatedEvent.class);
            when(event1.file()).thenReturn(mockFile);
            sut = sut.afterFileEvent(event1);

            // create policy with name collision
            var mockFile2 = mockPolicyFile(POLICY_1, SAPL_1, "alternatePath", mockedFiles, mockInterpreter);
            var event2    = mock(FileCreatedEvent.class);
            when(event2.file()).thenReturn(mockFile2);
            sut = sut.afterFileEvent(event2);

            // add unrelated policy
            var mockFile3 = mockPolicyFile(POLICY_2, SAPL_2, POLICY_2_NAME, mockedFiles, mockInterpreter);
            var event3    = mock(FileCreatedEvent.class);
            when(event3.file()).thenReturn(mockFile3);
            sut = sut.afterFileEvent(event3);

            var actualUpdates = sut.getUpdateEvent();

            // validate
            // @formatter:off
			assertThat(actualUpdates.getUpdates(),
					arrayContaining(
							isUpdateWithName(PrpUpdateEvent.Type.PUBLISH, POLICY_2_NAME)
					));
			// @formatter:on
        }
    }

    @Test
    void when_initializingWithEmptyDirectory_and_updateAddAndAddNameCollisionAndRemoveInitial_then_Consistent() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            // Setup initial Index
            var mockPaths           = List.of();
            var mockDirectoryStream = mock(DirectoryStream.class);
            when(mockDirectoryStream.iterator()).thenReturn(mockPaths.iterator());
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenReturn(mockDirectoryStream);

            // 1st event load valid policy
            var mockInterpreter = mock(SAPLInterpreter.class);
            var mockFile        = mockPolicyFile(POLICY_1, SAPL_1, POLICY_1_NAME, mockedFiles, mockInterpreter);

            var sut = new ImmutableFileIndex(PATH, mockInterpreter);

            var event1 = mock(FileCreatedEvent.class);
            when(event1.file()).thenReturn(mockFile);
            sut = sut.afterFileEvent(event1);

            // create policy with name collision
            var mockFile2 = mockPolicyFile(POLICY_1, SAPL_1, "alternatePath", mockedFiles, mockInterpreter);
            var event2    = mock(FileCreatedEvent.class);
            when(event2.file()).thenReturn(mockFile2);
            sut = sut.afterFileEvent(event2);

            // remove initially added document
            var event3 = mock(FileDeletedEvent.class);
            when(event3.file()).thenReturn(mockFile);
            sut = sut.afterFileEvent(event3);

            var actualUpdates = sut.getUpdateEvent();

            // validate
            // @formatter:off
			assertThat(actualUpdates.getUpdates(),
					arrayContaining(
							isUpdateWithName(PrpUpdateEvent.Type.WITHDRAW, POLICY_1_NAME),
							isUpdateWithName(PrpUpdateEvent.Type.PUBLISH, POLICY_1_NAME),
							isUpdateType(PrpUpdateEvent.Type.CONSISTENT)
					));
			// @formatter:on
        }
    }

    @Test
    void when_initializingWithEmptyDirectory_and_updateAddAndAddNameCollisionAndRemoveNewColliding_then_Consistent() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            // Setup initial Index
            var mockPaths           = List.of();
            var mockDirectoryStream = mock(DirectoryStream.class);
            when(mockDirectoryStream.iterator()).thenReturn(mockPaths.iterator());
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenReturn(mockDirectoryStream);

            // 1st event load valid policy
            var mockInterpreter = mock(SAPLInterpreter.class);
            var mockFile        = mockPolicyFile(POLICY_1, SAPL_1, POLICY_1_NAME, mockedFiles, mockInterpreter);

            var sut = new ImmutableFileIndex(PATH, mockInterpreter);

            var event1 = mock(FileCreatedEvent.class);
            when(event1.file()).thenReturn(mockFile);
            sut = sut.afterFileEvent(event1);

            // create policy with name collision
            var mockFile2 = mockPolicyFile(POLICY_1, SAPL_1, "alternatePath", mockedFiles, mockInterpreter);
            var event2    = mock(FileCreatedEvent.class);
            when(event2.file()).thenReturn(mockFile2);
            sut = sut.afterFileEvent(event2);

            // remove initially added document
            var event3 = mock(FileDeletedEvent.class);
            when(event3.file()).thenReturn(mockFile2);
            sut = sut.afterFileEvent(event3);

            var actualUpdates = sut.getUpdateEvent();

            // validate
            // @formatter:off
			assertThat(actualUpdates.getUpdates(),
					arrayContaining(
							isUpdateType(PrpUpdateEvent.Type.CONSISTENT)
					));
			// @formatter:on
        }
    }

    @Test
    void when_initializingWithEmptyDirectory_and_addInvalidAndRemoveInvalid_then_FirstInconsistentThenConsistent() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            // Setup initial Index
            var mockPaths           = List.of();
            var mockDirectoryStream = mock(DirectoryStream.class);
            when(mockDirectoryStream.iterator()).thenReturn(mockPaths.iterator());
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenReturn(mockDirectoryStream);

            // 1st event load invalid policy
            var mockInterpreter = mock(SAPLInterpreter.class);
            var mockFile        = mockInvalidPolicyFile(POLICY_1, POLICY_1_NAME, mockedFiles, mockInterpreter);

            var sut = new ImmutableFileIndex(PATH, mockInterpreter);

            var event1 = mock(FileCreatedEvent.class);
            when(event1.file()).thenReturn(mockFile);
            sut = sut.afterFileEvent(event1);

            // @formatter:off
			assertThat(sut.getUpdateEvent().getUpdates(),
					arrayContaining(
							isUpdateType(PrpUpdateEvent.Type.INCONSISTENT)
					));
			// @formatter:on

            // remove initially added document
            var event3 = mock(FileDeletedEvent.class);
            when(event3.file()).thenReturn(mockFile);
            sut = sut.afterFileEvent(event3);

            var actualUpdates = sut.getUpdateEvent();

            // validate
            // @formatter:off
			assertThat(actualUpdates.getUpdates(),
					arrayContaining(
							isUpdateType(PrpUpdateEvent.Type.CONSISTENT)
					));
			// @formatter:on
        }
    }

    @Test
    void when_initializingWithEmptyDirectory_and_tryUnloadingNonExisting_then_nothing() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            // Setup initial Index
            var mockPaths           = List.of();
            var mockDirectoryStream = mock(DirectoryStream.class);
            when(mockDirectoryStream.iterator()).thenReturn(mockPaths.iterator());
            mockedFiles.when(() -> Files.newDirectoryStream(any(Path.class), any(String.class)))
                    .thenReturn(mockDirectoryStream);

            // 1st event load valid policy
            var mockInterpreter = mock(SAPLInterpreter.class);
            var mockFile        = mockPolicyFile(POLICY_1, SAPL_1, POLICY_1_NAME, mockedFiles, mockInterpreter);

            var sut = new ImmutableFileIndex(PATH, mockInterpreter);

            // remove non existent document from index
            var event3 = mock(FileDeletedEvent.class);
            when(event3.file()).thenReturn(mockFile);
            sut = sut.afterFileEvent(event3);

            var actualUpdates = sut.getUpdateEvent();

            // validate
            assertThat(actualUpdates.getUpdates(), emptyArray());
        }
    }

    private File mockPolicyFile(String document, SAPL sapl, String path, MockedStatic<Files> mockedFiles,
            SAPLInterpreter mockInterpreter) {
        var mockPath = mock(Path.class);
        when(mockPath.toAbsolutePath()).thenReturn(mockPath);
        when(mockPath.toString()).thenReturn(path);
        var mockFile = mock(File.class);
        when(mockFile.toPath()).thenReturn(mockPath);
        mockedFiles.when(() -> Files.readString(eq(mockPath))).thenReturn(document);
        when(mockInterpreter.parse(document)).thenReturn(sapl);
        return mockFile;
    }

    private File mockInvalidPolicyFile(String document, String path, MockedStatic<Files> mockedFiles,
            SAPLInterpreter mockInterpreter) {
        var mockPath = mock(Path.class);
        when(mockPath.toAbsolutePath()).thenReturn(mockPath);
        when(mockPath.toString()).thenReturn(path);
        var mockFile = mock(File.class);
        when(mockFile.toPath()).thenReturn(mockPath);
        mockedFiles.when(() -> Files.readString(eq(mockPath))).thenReturn(document);
        when(mockInterpreter.parse(document)).thenThrow(new PolicyEvaluationException());
        return mockFile;
    }

    private IsPojo<Update> isUpdateWithName(PrpUpdateEvent.Type type, String name) {
        // @formatter:off
		return pojo(Update.class)
			.withProperty("type", is(type))
			.withProperty("document",
				pojo(SAPL.class)
					.withProperty("policyElement",
						pojo(PolicyElement.class)
							.withProperty("saplName", is(name))
			    )
		);
		// @formatter:on
    }

    private IsPojo<Update> isUpdateType(PrpUpdateEvent.Type type) {
        return pojo(Update.class).withProperty("type", is(type));
    }

}
