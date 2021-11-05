/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.filesystem.ImmutableFileIndex.Document;
import io.sapl.util.filemonitoring.FileChangedEvent;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.spotify.hamcrest.pojo.IsPojo.pojo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class ImmutableFileIndexTest {

	@TempDir
	File folder;

	private static SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

	@Test
	void test_after_file_event() throws Exception {
		var p1 = new File(folder, "policy1.sapl");

		var indexMock = mock(ImmutableFileIndex.class, withSettings().useConstructor(folder.getPath(), interpreter));
		when(indexMock.afterFileEvent(any())).thenCallRealMethod();
		when(indexMock.getUpdateEvent()).thenCallRealMethod();

		// WHEN

		// this will break the immutability of the index (the same mock will be returned
		// for each constructor call)
		try (MockedConstruction<ImmutableFileIndex> mocked = Mockito.mockConstruction(ImmutableFileIndex.class,
				(mock, context) -> {
					doNothing().when(mock).load(any());
					doNothing().when(mock).unload(any());
					doCallRealMethod().when(mock).change(any());
					// when(mock.becameInconsistentComparedTo(any())).thenReturn(true);

					when(mock.afterFileEvent(any())).thenCallRealMethod();
					when(mock.getUpdateEvent()).thenCallRealMethod();
				})) {

			// DO
			indexMock.afterFileEvent(new FileCreatedEvent(p1));
			verify(mocked.constructed().get(0), times(1)).load(any());

			indexMock.afterFileEvent(new FileChangedEvent(p1));
			verify(mocked.constructed().get(1), times(1)).load(any());
		}

	}

	@Test
	void should_become_inconsistent_after_file_event() {
		File notADirectoryFile = new File(folder, "not_a_directory_file");

		var consistentIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/empty", interpreter);
		var inconsistentIndex = consistentIndex.afterFileEvent(new FileCreatedEvent(notADirectoryFile));

		//
		assertThat(Arrays.stream(inconsistentIndex.getUpdateEvent().getUpdates())
				.anyMatch(update -> update.getType() == Type.INCONSISTENT), is(true));

	}

	@Test
	void test_internal_copy_constructor() throws Exception {
		var p1 = new File(folder, "policy1.sapl");
		FileUtils.writeStringToFile(p1, String
				.format("policy \"%s\"\n" + "permit\n" + "    action == \"read\"\n" + "\n" + "\n" + "\n", "policy1"),
				Charset.defaultCharset());

		var p2 = new File(folder, "policy2.sapl");
		FileUtils.writeStringToFile(p2, String
				.format("policy \"%s\"\n" + "permit\n" + "    action == \"read\"\n" + "\n" + "\n" + "\n", "policy1"),
				Charset.defaultCharset());

		var fileIndex = new ImmutableFileIndex(folder.getPath(), interpreter);

		assertThat(fileIndex, notNullValue());

		assertThat(Arrays.stream(fileIndex.getUpdateEvent().getUpdates())
				.anyMatch(update -> update.getType() == Type.PUBLISH), is(true));

		var newIndex = fileIndex.afterFileEvent(new FileCreatedEvent(new File("NOTFOUND")));

		// in case of collision published file is randomly selected depending on OS
		// behavior
		var fileToDelete = newIndex.documentsByPath.get(p1.getAbsolutePath()).isPublished() ? p1 : p2;

		newIndex = fileIndex.afterFileEvent(new FileDeletedEvent(fileToDelete));

		assertThat(newIndex, notNullValue());
		assertThat(Arrays.stream(newIndex.getUpdateEvent().getUpdates())
				.anyMatch(update -> update.getType() == Type.WITHDRAW), is(true));
	}

	@Test
	void should_not_throw_exception_when_watchdir_can_not_be_opened() throws Exception {
		File notADirectoryFile = new File(folder, "not_a_directory_file");

		var fileIndex = new ImmutableFileIndex(notADirectoryFile.getPath(), interpreter);
		var updateEvent = fileIndex.getUpdateEvent();

		assertThat(updateEvent, notNullValue());
		assertThat(updateEvent.getUpdates().length, not(is(0)));

		assertThat(updateEvent.getUpdates(),
				arrayContaining(pojo(PrpUpdateEvent.Update.class).withProperty("type", is(Type.INCONSISTENT))));
	}

	@Test
	void return_no_event_for_empty_policy_directory() {
		var fileIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/empty", interpreter);
		var updateEvent = fileIndex.getUpdateEvent();

		assertThat(updateEvent, notNullValue());
		assertThat(updateEvent.getUpdates(), emptyArray());
	}

	@Test
	void return_inconsistent_event_for_name_collision() {
		var fileIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/namecollision", interpreter);
		var updateEvent = fileIndex.getUpdateEvent();

		assertThat(updateEvent, notNullValue());
		assertThat(updateEvent.getUpdates().length, not(is(0)));

		assertThat(Arrays.stream(updateEvent.getUpdates()).anyMatch(update -> update.getType() == Type.INCONSISTENT),
				is(true));
	}

	@Test
	void return_inconsistent_event_for_invalid_document() {
		var fileIndex = new ImmutableFileIndex("src/test/resources/filemonitoring/invalid", interpreter);
		var updateEvent = fileIndex.getUpdateEvent();

		assertThat(updateEvent, notNullValue());
		assertThat(updateEvent.getUpdates().length, not(is(0)));

		assertThat(updateEvent.getUpdates(),
				arrayContaining(pojo(PrpUpdateEvent.Update.class).withProperty("type", is(Type.INCONSISTENT))));
	}

	@Test
	void testBecameConsistent() {
		var oldIndexMock = mock(ImmutableFileIndex.class);
		var newIndexMock = mock(ImmutableFileIndex.class);
		when(newIndexMock.becameConsistentComparedTo(any())).thenCallRealMethod();

		when(oldIndexMock.isInconsistent()).thenReturn(true);
		when(newIndexMock.isConsistent()).thenReturn(true);
		assertThat(newIndexMock.becameConsistentComparedTo(oldIndexMock), is(true));

		when(oldIndexMock.isInconsistent()).thenReturn(false);
		when(newIndexMock.isConsistent()).thenReturn(false);
		assertThat(newIndexMock.becameConsistentComparedTo(oldIndexMock), is(false));

		when(oldIndexMock.isInconsistent()).thenReturn(true);
		when(newIndexMock.isConsistent()).thenReturn(false);
		assertThat(newIndexMock.becameConsistentComparedTo(oldIndexMock), is(false));

		when(oldIndexMock.isInconsistent()).thenReturn(false);
		when(newIndexMock.isConsistent()).thenReturn(true);
		assertThat(newIndexMock.becameConsistentComparedTo(oldIndexMock), is(false));
	}

	@Test
	void testBecameInconsistent() {
		var oldIndexMock = mock(ImmutableFileIndex.class);
		var newIndexMock = mock(ImmutableFileIndex.class);
		when(newIndexMock.becameInconsistentComparedTo(any())).thenCallRealMethod();

		when(oldIndexMock.isConsistent()).thenReturn(true);
		when(newIndexMock.isInconsistent()).thenReturn(true);
		assertThat(newIndexMock.becameInconsistentComparedTo(oldIndexMock), is(true));

		when(oldIndexMock.isConsistent()).thenReturn(false);
		when(newIndexMock.isInconsistent()).thenReturn(false);
		assertThat(newIndexMock.becameInconsistentComparedTo(oldIndexMock), is(false));

		when(oldIndexMock.isConsistent()).thenReturn(true);
		when(newIndexMock.isInconsistent()).thenReturn(false);
		assertThat(newIndexMock.becameInconsistentComparedTo(oldIndexMock), is(false));

		when(oldIndexMock.isConsistent()).thenReturn(false);
		when(newIndexMock.isInconsistent()).thenReturn(true);
		assertThat(newIndexMock.becameInconsistentComparedTo(oldIndexMock), is(false));
	}

	@Test
	void testUnload() {
		/* MOCKS */
		var fileNameMock1 = mock(Path.class, RETURNS_DEEP_STUBS);
		when(fileNameMock1.toString()).thenReturn("policy_1.sapl");
		var fileNameMock2 = mock(Path.class, RETURNS_DEEP_STUBS);
		when(fileNameMock2.toString()).thenReturn("policy_2.sapl");
		var fileNameMock3 = mock(Path.class, RETURNS_DEEP_STUBS);
		when(fileNameMock3.toString()).thenReturn("policy_3.sapl");

		var documentMock1 = mock(Document.class, RETURNS_DEEP_STUBS);
		when(documentMock1.getDocumentName()).thenReturn("doc1");
		when(documentMock1.getPath().getFileName()).thenReturn(fileNameMock1);
		when(documentMock1.toString()).thenReturn("policy_1.sapl");
		var documentMock2 = mock(Document.class, RETURNS_DEEP_STUBS);
		when(documentMock2.getDocumentName()).thenReturn("doc1");
		when(documentMock2.getPath().getFileName()).thenReturn(fileNameMock2);
		when(documentMock2.toString()).thenReturn("policy_2.sapl");
		var documentMock3 = mock(Document.class, RETURNS_DEEP_STUBS);
		when(documentMock3.getDocumentName()).thenReturn("doc1");
		when(documentMock3.getPath().getFileName()).thenReturn(fileNameMock3);
		when(documentMock3.toString()).thenReturn("policy_3.sapl");

		List<Document> docsByNameList = new ArrayList<>();
		docsByNameList.add(documentMock1);
		docsByNameList.add(documentMock2);
		docsByNameList.add(documentMock3);

		/* EXECUTE TEST */
		var fileIndex = new ImmutableFileIndex("src/test/resources/policies", interpreter);
		var spy = spy(fileIndex);

		// unload "policy_1.sapl"
		doReturn("path1").when(spy).getAbsolutePathAsString(any());
		when(spy.containsDocumentWithPath("path1")).thenReturn(true);
		when(spy.removeDocumentFromMap("path1")).thenReturn(documentMock1);
		when(spy.getDocumentByName("doc1")).thenReturn(docsByNameList);
		when(documentMock1.isValid()).thenReturn(true);
		when(documentMock1.isPublished()).thenReturn(true);

		spy.unload(mock(Path.class));
		verify(spy, times(1)).addWithdrawUpdate(documentMock1);
		reset(spy);

		// unload "policy_2.sapl"
		doReturn("path2").when(spy).getAbsolutePathAsString(any());
		when(spy.containsDocumentWithPath("path2")).thenReturn(true);
		when(spy.removeDocumentFromMap("path2")).thenReturn(documentMock2);
		when(spy.getDocumentByName("doc1")).thenReturn(docsByNameList);
		when(documentMock2.isValid()).thenReturn(false);

		spy.unload(mock(Path.class));
		verify(spy, times(1)).decrementInvalidDocumentCount();
		reset(spy);

		// unload "policy_3.sapl"
		doReturn("path3").when(spy).getAbsolutePathAsString(any());
		when(spy.containsDocumentWithPath("path3")).thenReturn(true);
		when(spy.removeDocumentFromMap("path3")).thenReturn(documentMock3);
		when(spy.getDocumentByName("doc1")).thenReturn(docsByNameList);
		when(documentMock3.isValid()).thenReturn(true);

		spy.unload(mock(Path.class));
		verify(spy, times(1)).decrementNameCollisions();
		reset(spy);
	}

}
