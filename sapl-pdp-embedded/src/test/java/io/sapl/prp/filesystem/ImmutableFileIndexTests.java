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
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.is;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.spotify.hamcrest.pojo.IsPojo;

import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.util.filemonitoring.FileChangedEvent;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import lombok.SneakyThrows;

class ImmutableFileIndexTests {

    private static final String          POLICIES_PATH = "policies";
    private static final SAPLInterpreter INTERPRETER   = new DefaultSAPLInterpreter();
    private static final String          POLICY_1      = "policy \"policy1\" permit";
    private static final String          POLICY_2      = "policy \"policy2\" permit";

    private static Stream<Arguments> provideFileSystem() {
        return Stream.of(Arguments.of(Jimfs.newFileSystem(Configuration.unix())),
                Arguments.of(Jimfs.newFileSystem(Configuration.windows())),
                Arguments.of(Jimfs.newFileSystem(Configuration.osX())));
    }

    @ParameterizedTest
    @MethodSource("provideFileSystem")
    void whe_startingWithEmptyDirectory_thenNoUpdates(FileSystem fileSystem) throws Exception {
        var policiesFolder = fileSystem.getPath(POLICIES_PATH);
        Files.createDirectory(policiesFolder);
        var index = new ImmutableFileIndex(policiesFolder, INTERPRETER);
        assertThat(index.getUpdateEvent().getUpdates(), is(arrayWithSize(0)));
    }

    @ParameterizedTest
    @MethodSource("provideFileSystem")
    void whe_startingNonExistingDirectory_thenNoUpdates(FileSystem fileSystem) throws Exception {
        var policiesFolder = fileSystem.getPath(POLICIES_PATH);
        var sut            = new ImmutableFileIndex(policiesFolder, INTERPRETER);
        var actualUpdates  = sut.getUpdateEvent().getUpdates();
        assertThat(actualUpdates, is(arrayWithSize(1)));
        assertThat(actualUpdates,
                arrayContainingInAnyOrder(pojo(Update.class).withProperty("type", is(Type.INCONSISTENT))));
    }

    @ParameterizedTest
    @MethodSource("provideFileSystem")
    void when_initializingWithDirectoryThatContainsUnreadableFile_then_updatesContainOnlyInconsistent(
            FileSystem fileSystem) throws Exception {
        var policiesFolder = fileSystem.getPath(POLICIES_PATH);
        Files.createDirectory(policiesFolder);
        writeFile(fileSystem, "badpolicy.sapl", "p oli cy bad");
        var sut           = new ImmutableFileIndex(policiesFolder, INTERPRETER);
        var actualUpdates = sut.getUpdateEvent().getUpdates();
        assertThat(actualUpdates, is(arrayWithSize(1)));
        assertThat(actualUpdates,
                arrayContainingInAnyOrder(pojo(Update.class).withProperty("type", is(Type.INCONSISTENT))));
    }

    @ParameterizedTest
    @MethodSource("provideFileSystem")
    void when_initializingWithTwoFilesInDirectory_and_update_then_updatePublish(FileSystem fileSystem)
            throws Exception {
        var policiesFolder = fileSystem.getPath(POLICIES_PATH);
        Files.createDirectory(policiesFolder);
        writeFile(fileSystem, "policy1.sapl", POLICY_1);
        writeFile(fileSystem, "policy2.sapl", POLICY_2);
        var sut           = new ImmutableFileIndex(policiesFolder, INTERPRETER);
        var actualUpdates = sut.getUpdateEvent().getUpdates();
        assertThat(actualUpdates, is(arrayWithSize(2)));
        assertThat(actualUpdates, arrayContainingInAnyOrder(isUpdateWithName(Type.PUBLISH, "policy1"),
                isUpdateWithName(Type.PUBLISH, "policy2")));

        var event      = new FileChangedEvent(writeFile(fileSystem, "policy2.sapl", "policy \"p2 update\" permit"));
        var updatedSut = sut.afterFileEvent(event);
        var newUpdates = updatedSut.getUpdateEvent().getUpdates();
        assertThat(newUpdates, is(arrayWithSize(2)));
        assertThat(newUpdates, arrayContainingInAnyOrder(isUpdateWithName(Type.WITHDRAW, "policy2"),
                isUpdateWithName(Type.PUBLISH, "p2 update")));
    }

    @ParameterizedTest
    @MethodSource("provideFileSystem")
    void when_initializingWithTwoFilesInDirectory_and_addOne_then_Publish(FileSystem fileSystem) throws Exception {
        var policiesFolder = fileSystem.getPath(POLICIES_PATH);
        Files.createDirectory(policiesFolder);
        writeFile(fileSystem, "policy1.sapl", POLICY_1);
        writeFile(fileSystem, "policy2.sapl", POLICY_2);
        var sut           = new ImmutableFileIndex(policiesFolder, INTERPRETER);
        var actualUpdates = sut.getUpdateEvent().getUpdates();
        assertThat(actualUpdates, is(arrayWithSize(2)));
        assertThat(actualUpdates, arrayContainingInAnyOrder(isUpdateWithName(Type.PUBLISH, "policy1"),
                isUpdateWithName(Type.PUBLISH, "policy2")));

        var event      = new FileCreatedEvent(writeFile(fileSystem, "policy3.sapl", "policy \"p3\" permit"));
        var updatedSut = sut.afterFileEvent(event);
        var newUpdates = updatedSut.getUpdateEvent().getUpdates();
        assertThat(newUpdates, is(arrayWithSize(1)));
        assertThat(newUpdates, arrayContainingInAnyOrder(isUpdateWithName(Type.PUBLISH, "p3")));
    }

    @ParameterizedTest
    @MethodSource("provideFileSystem")
    void when_initializingWithTwoFilesInDirectory_and_addOneWithCollision_then_Inconsistenz(FileSystem fileSystem)
            throws Exception {
        var policiesFolder = fileSystem.getPath(POLICIES_PATH);
        Files.createDirectory(policiesFolder);
        writeFile(fileSystem, "policy1.sapl", POLICY_1);
        writeFile(fileSystem, "policy2.sapl", POLICY_2);
        var sut           = new ImmutableFileIndex(policiesFolder, INTERPRETER);
        var actualUpdates = sut.getUpdateEvent().getUpdates();
        assertThat(actualUpdates, is(arrayWithSize(2)));
        assertThat(actualUpdates, arrayContainingInAnyOrder(isUpdateWithName(Type.PUBLISH, "policy1"),
                isUpdateWithName(Type.PUBLISH, "policy2")));

        var event      = new FileCreatedEvent(writeFile(fileSystem, "policy3.sapl", "policy \"policy1\" permit"));
        var updatedSut = sut.afterFileEvent(event);
        var newUpdates = updatedSut.getUpdateEvent().getUpdates();
        assertThat(newUpdates, is(arrayWithSize(1)));
        assertThat(newUpdates, arrayContainingInAnyOrder(isUpdateType(Type.INCONSISTENT)));
    }

    @ParameterizedTest
    @MethodSource("provideFileSystem")
    void when_initializingWithNameCollision_and_deleteSecond_then_updateConsistent(FileSystem fileSystem)
            throws Exception {
        var policiesFolder = fileSystem.getPath(POLICIES_PATH);
        Files.createDirectory(policiesFolder);
        writeFile(fileSystem, "policy1.sapl", POLICY_1);
        writeFile(fileSystem, "policy2.sapl", POLICY_1);
        var sut           = new ImmutableFileIndex(policiesFolder, INTERPRETER);
        var actualUpdates = sut.getUpdateEvent().getUpdates();
        assertThat(actualUpdates, is(arrayWithSize(2)));
        assertThat(actualUpdates,
                arrayContainingInAnyOrder(isUpdateWithName(Type.PUBLISH, "policy1"), isUpdateType(Type.INCONSISTENT)));

        var event      = new FileDeletedEvent(deleteFile(fileSystem, "policy2.sapl"));
        var updatedSut = sut.afterFileEvent(event);
        var newUpdates = updatedSut.getUpdateEvent().getUpdates();
        assertThat(newUpdates, is(arrayWithSize(1)));
        assertThat(newUpdates, arrayContainingInAnyOrder(isUpdateType(Type.CONSISTENT)));
    }

    @ParameterizedTest
    @MethodSource("provideFileSystem")
    void when_initializingWithNameCollision_and_deleteFirst_then_updateConsistent(FileSystem fileSystem)
            throws Exception {
        var policiesFolder = fileSystem.getPath(POLICIES_PATH);
        Files.createDirectory(policiesFolder);
        writeFile(fileSystem, "policy1.sapl", POLICY_1);
        writeFile(fileSystem, "policy2.sapl", POLICY_1);
        var sut           = new ImmutableFileIndex(policiesFolder, INTERPRETER);
        var actualUpdates = sut.getUpdateEvent().getUpdates();
        assertThat(actualUpdates, is(arrayWithSize(2)));
        assertThat(actualUpdates,
                arrayContainingInAnyOrder(isUpdateWithName(Type.PUBLISH, "policy1"), isUpdateType(Type.INCONSISTENT)));

        var event      = new FileDeletedEvent(deleteFile(fileSystem, "policy1.sapl"));
        var updatedSut = sut.afterFileEvent(event);
        var newUpdates = updatedSut.getUpdateEvent().getUpdates();
        assertThat(newUpdates, is(arrayWithSize(3)));
        assertThat(newUpdates, arrayContainingInAnyOrder(isUpdateWithName(Type.PUBLISH, "policy1"),
                isUpdateWithName(Type.WITHDRAW, "policy1"), isUpdateType(Type.CONSISTENT)));
    }

    @ParameterizedTest
    @MethodSource("provideFileSystem")
    void when_initializingWithTwoDocs_and_deleteSomethingIrrelevant_then_updateConsistent(FileSystem fileSystem)
            throws Exception {
        var policiesFolder = fileSystem.getPath(POLICIES_PATH);
        Files.createDirectory(policiesFolder);
        writeFile(fileSystem, "policy1.sapl", POLICY_1);
        writeFile(fileSystem, "policy2.sapl", POLICY_2);
        var sut           = new ImmutableFileIndex(policiesFolder, INTERPRETER);
        var actualUpdates = sut.getUpdateEvent().getUpdates();
        assertThat(actualUpdates, is(arrayWithSize(2)));
        assertThat(actualUpdates, arrayContainingInAnyOrder(isUpdateWithName(Type.PUBLISH, "policy1"),
                isUpdateWithName(Type.PUBLISH, "policy2")));

        var event      = new FileDeletedEvent(fileSystem.getPath("not_there.sapl"));
        var updatedSut = sut.afterFileEvent(event);
        var newUpdates = updatedSut.getUpdateEvent().getUpdates();
        assertThat(newUpdates, is(arrayWithSize(0)));
    }

    @ParameterizedTest
    @MethodSource("provideFileSystem")
    void when_initializingWithBadDocument_and_deleteIt_then_updateConsistent(FileSystem fileSystem) throws Exception {
        var policiesFolder = fileSystem.getPath(POLICIES_PATH);
        Files.createDirectory(policiesFolder);
        writeFile(fileSystem, "policy1.sapl", POLICY_1);
        writeFile(fileSystem, "policy2.sapl", "broken");
        var sut           = new ImmutableFileIndex(policiesFolder, INTERPRETER);
        var actualUpdates = sut.getUpdateEvent().getUpdates();
        assertThat(actualUpdates, is(arrayWithSize(2)));
        assertThat(actualUpdates,
                arrayContainingInAnyOrder(isUpdateWithName(Type.PUBLISH, "policy1"), isUpdateType(Type.INCONSISTENT)));

        var event      = new FileDeletedEvent(deleteFile(fileSystem, "policy2.sapl"));
        var updatedSut = sut.afterFileEvent(event);
        var newUpdates = updatedSut.getUpdateEvent().getUpdates();
        assertThat(newUpdates, is(arrayWithSize(1)));
        assertThat(newUpdates, arrayContainingInAnyOrder(isUpdateType(Type.CONSISTENT)));
    }

    @ParameterizedTest
    @MethodSource("provideFileSystem")
    void when_initializingWithNameCollision_and_updateOne_then_updateConsistent(FileSystem fileSystem)
            throws Exception {
        var policiesFolder = fileSystem.getPath(POLICIES_PATH);
        Files.createDirectory(policiesFolder);
        writeFile(fileSystem, "policy1.sapl", POLICY_1);
        writeFile(fileSystem, "policy2.sapl", POLICY_1);
        var sut           = new ImmutableFileIndex(policiesFolder, INTERPRETER);
        var actualUpdates = sut.getUpdateEvent().getUpdates();
        assertThat(actualUpdates, is(arrayWithSize(2)));
        assertThat(actualUpdates,
                arrayContainingInAnyOrder(isUpdateWithName(Type.PUBLISH, "policy1"), isUpdateType(Type.INCONSISTENT)));
        var event      = new FileDeletedEvent(writeFile(fileSystem, "policy2.sapl", POLICY_2));
        var updatedSut = sut.afterFileEvent(event);
        var newUpdates = updatedSut.getUpdateEvent().getUpdates();
        assertThat(newUpdates, is(arrayWithSize(1)));
        assertThat(newUpdates, arrayContainingInAnyOrder(isUpdateType(Type.CONSISTENT)));
    }

    private Matcher<Update> isUpdateWithName(final Type type, final String name) {
        return new BaseMatcher<Update>() {
            @Override
            public boolean matches(final Object oUpdate) {
                if (oUpdate instanceof Update update) {
                    try {
                        return update.getType().equals(type)
                                && Objects.equals(name, update.getDocument().sapl().getPolicyElement().getSaplName());
                    } catch (Exception e) {
                        return false;
                    }
                } else {
                    return false;
                }
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("update should have name ").appendValue(name);
            }
        };
    }

    private IsPojo<Update> isUpdateType(Type type) {
        return pojo(Update.class).withProperty("type", is(type));
    }

    @SneakyThrows
    private Path writeFile(FileSystem fileSystem, String fileName, String newContent) {
        return Files.write(fileSystem.getPath(POLICIES_PATH, fileName), newContent.getBytes());
    }

    @SneakyThrows
    private Path deleteFile(FileSystem fileSystem, String fileName) {
        var path = fileSystem.getPath(POLICIES_PATH, fileName);
        Files.delete(path);
        return path;
    }
}
