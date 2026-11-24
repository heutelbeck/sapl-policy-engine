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
package io.sapl.pdp.configuration.bundle;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jspecify.annotations.Nullable;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Tells whether the central directory of a ZIP archive and its local entries
 * agree, so that a reader of the directory and a reader of the local entries
 * read the same files. They agree when no name occurs twice in the directory,
 * the local header of each entry matches its directory entry, no two entries
 * overlap, and no local header lies between the entries outside the directory.
 */
@UtilityClass
class ZipConsistency {

    private static final int  LOCAL_HEADER_SIGNATURE  = 0x04034b50;
    private static final int  LOCAL_HEADER_LENGTH     = 30;
    private static final int  END_RECORD_SIGNATURE    = 0x06054b50;
    private static final int  END_RECORD_LENGTH       = 22;
    private static final int  MAX_COMMENT_LENGTH      = 0xFFFF;
    private static final int  ZIP64_LOCATOR_SIGNATURE = 0x07064b50;
    private static final int  ZIP64_LOCATOR_LENGTH    = 20;
    private static final int  ZIP64_END_RECORD_LENGTH = 56;
    private static final int  ENCRYPTED_FLAG          = 1;
    private static final int  DATA_DESCRIPTOR_FLAG    = 1 << 3;
    private static final int  UTF8_NAME_FLAG          = 1 << 11;
    private static final int  COMPARED_FLAGS          = ENCRYPTED_FLAG | DATA_DESCRIPTOR_FLAG | UTF8_NAME_FLAG;
    private static final long ZIP64_MARKER            = 0xFFFFFFFFL;
    private static final int  SCAN_CHUNK_LENGTH       = 64 * 1024;

    // How far from the end of an archive its end record can start: the record
    // and the longest comment after it.
    private static final int END_RECORD_REACH = END_RECORD_LENGTH + MAX_COMMENT_LENGTH;

    // A chunk of a gap is read with the bytes that a local header starting at its
    // last position needs.
    private static final int SCAN_READ_LENGTH = SCAN_CHUNK_LENGTH + LOCAL_HEADER_LENGTH - 1;

    /**
     * The bytes an entry occupies: from its local header to the end of its
     * compressed data.
     */
    private record Range(long start, long end) {}

    /**
     * @param zip the archive as read through its central directory
     * @param archive the bytes of the archive
     * @return whether the directory and the local entries agree
     * @throws IOException if the archive cannot be read
     */
    static boolean isConsistent(ZipFile zip, SeekableByteChannel archive) throws IOException {
        val names  = new HashSet<ByteBuffer>();
        val ranges = new ArrayList<Range>();
        for (val entry : Collections.list(zip.getEntries())) {
            if (!names.add(ByteBuffer.wrap(entry.getRawName()))) {
                return false;
            }
            val range = localRangeOf(entry, archive);
            if (range == null) {
                return false;
            }
            ranges.add(range);
        }
        ranges.sort(Comparator.comparingLong(Range::start).thenComparingLong(Range::end));
        val directoryStart = directoryStartOf(archive);
        var cursor         = 0L;
        for (val range : ranges) {
            if (range.start() < cursor || hasLocalHeaderBetween(archive, cursor, range.start())) {
                return false;
            }
            cursor = range.end();
        }
        return cursor <= directoryStart && !hasLocalHeaderBetween(archive, cursor, directoryStart);
    }

    // The local header of an entry must name it as the directory does and agree
    // on how its data is stored. Sizes and checksum may be left zero in the local
    // header when a data descriptor follows the data, and a ZIP64 header keeps
    // them in an extra field.
    private static @Nullable Range localRangeOf(ZipArchiveEntry entry, SeekableByteChannel archive) throws IOException {
        val start = entry.getLocalHeaderOffset();
        if (start < 0 || start + LOCAL_HEADER_LENGTH > archive.size()) {
            return null;
        }
        val header = littleEndian(read(archive, start, LOCAL_HEADER_LENGTH));
        if (header.getInt(0) != LOCAL_HEADER_SIGNATURE) {
            return null;
        }
        val flags       = Short.toUnsignedInt(header.getShort(6));
        val method      = Short.toUnsignedInt(header.getShort(8));
        val nameLength  = Short.toUnsignedInt(header.getShort(26));
        val extraLength = Short.toUnsignedInt(header.getShort(28));
        val dataStart   = start + LOCAL_HEADER_LENGTH + nameLength + extraLength;
        // Data that would reach beyond the archive is refused before its end is
        // computed, so that a crafted ZIP64 size cannot overflow it.
        if (dataStart > archive.size() || entry.getCompressedSize() < 0
                || entry.getCompressedSize() > archive.size() - dataStart) {
            return null;
        }
        val name         = read(archive, start + LOCAL_HEADER_LENGTH, nameLength);
        val sameIdentity = Arrays.equals(name, entry.getRawName()) && method == entry.getMethod()
                && (flags & COMPARED_FLAGS) == (directoryFlagsOf(entry) & COMPARED_FLAGS);
        val pendingData  = (flags & DATA_DESCRIPTOR_FLAG) != 0;
        val sameData     = agrees(Integer.toUnsignedLong(header.getInt(14)), entry.getCrc(), pendingData)
                && agrees(Integer.toUnsignedLong(header.getInt(18)), entry.getCompressedSize(), pendingData)
                && agrees(Integer.toUnsignedLong(header.getInt(22)), entry.getSize(), pendingData);
        return sameIdentity && sameData ? new Range(start, dataStart + entry.getCompressedSize()) : null;
    }

    private static int directoryFlagsOf(ZipArchiveEntry entry) {
        val bits = entry.getGeneralPurposeBit();
        return (bits.usesEncryption() ? ENCRYPTED_FLAG : 0) | (bits.usesDataDescriptor() ? DATA_DESCRIPTOR_FLAG : 0)
                | (bits.usesUTF8ForNames() ? UTF8_NAME_FLAG : 0);
    }

    private static boolean agrees(long local, long central, boolean pendingData) {
        return local == central || pendingData && local == 0 || local == ZIP64_MARKER;
    }

    // The offset of the central directory, as the end record names it, or the
    // ZIP64 end record where the end record defers to it.
    private static long directoryStartOf(SeekableByteChannel archive) throws IOException {
        val tailLength = (int) Math.min(archive.size(), END_RECORD_REACH);
        val tailStart  = archive.size() - tailLength;
        val tail       = littleEndian(read(archive, tailStart, tailLength));
        for (var position = tailLength - END_RECORD_LENGTH; position >= 0; position--) {
            if (tail.getInt(position) == END_RECORD_SIGNATURE) {
                val offset = Integer.toUnsignedLong(tail.getInt(position + 16));
                return offset == ZIP64_MARKER ? zip64DirectoryStartOf(archive, tailStart + position) : offset;
            }
        }
        throw new EOFException();
    }

    private static long zip64DirectoryStartOf(SeekableByteChannel archive, long endRecord) throws IOException {
        if (endRecord < ZIP64_LOCATOR_LENGTH) {
            throw new EOFException();
        }
        val locator = littleEndian(read(archive, endRecord - ZIP64_LOCATOR_LENGTH, ZIP64_LOCATOR_LENGTH));
        if (locator.getInt(0) != ZIP64_LOCATOR_SIGNATURE) {
            throw new EOFException();
        }
        val zip64EndRecord = locator.getLong(8);
        if (zip64EndRecord < 0 || zip64EndRecord > archive.size() - ZIP64_END_RECORD_LENGTH) {
            throw new EOFException();
        }
        return littleEndian(read(archive, zip64EndRecord, ZIP64_END_RECORD_LENGTH)).getLong(48);
    }

    // Whether a complete local header, with its name and extra field, lies
    // between two offsets, which no entry of the directory occupies.
    private static boolean hasLocalHeaderBetween(SeekableByteChannel archive, long from, long to) throws IOException {
        for (var chunkStart = from; chunkStart < to; chunkStart += SCAN_CHUNK_LENGTH) {
            val chunkLength = (int) Math.min(to - chunkStart, SCAN_READ_LENGTH);
            val chunk       = littleEndian(read(archive, chunkStart, chunkLength));
            val lastStart   = Math.min(SCAN_CHUNK_LENGTH, chunkLength - LOCAL_HEADER_LENGTH);
            for (var position = 0; position <= lastStart; position++) {
                if (chunk.getInt(position) == LOCAL_HEADER_SIGNATURE) {
                    val variableLength = Short.toUnsignedInt(chunk.getShort(position + 26))
                            + Short.toUnsignedInt(chunk.getShort(position + 28));
                    if (chunkStart + position + LOCAL_HEADER_LENGTH + variableLength <= to) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static byte[] read(SeekableByteChannel archive, long position, int length) throws IOException {
        val buffer = ByteBuffer.allocate(length);
        archive.position(position);
        while (buffer.hasRemaining()) {
            if (archive.read(buffer) < 0) {
                throw new EOFException();
            }
        }
        return buffer.array();
    }

    private static ByteBuffer littleEndian(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }
}
