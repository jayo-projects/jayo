/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal;

import jayo.Buffer;
import jayo.bytestring.ByteString;
import jayo.Options;
import org.jspecify.annotations.NonNull;

import java.util.*;

public final class RealOptions extends AbstractList<ByteString> implements Options {
    final @NonNull ByteString @NonNull [] byteStrings;
    final int @NonNull [] trie;

    public RealOptions(final @NonNull ByteString @NonNull [] byteStrings, final int @NonNull [] trie) {
        this.byteStrings = Objects.requireNonNull(byteStrings);
        this.trie = Objects.requireNonNull(trie);
    }

    @Override
    public int size() {
        return byteStrings.length;
    }

    @Override
    public ByteString get(final int index) {
        return byteStrings[index];
    }

    public static Options of(final @NonNull ByteString @NonNull ... byteStrings) {
        if (Objects.requireNonNull(byteStrings).length == 0) {
            // With no choices we must always return -1. Create a trie that selects from an empty set.
            return new RealOptions(new ByteString[0], new int[]{0, -1});
        }

        // Sort the byte strings which is required when recursively building the trie. Map the sorted indexes to the
        // caller's indexes.
        final var list = new ArrayList<>(List.of(byteStrings));
        Collections.sort(list);
        final var indexes = new ArrayList<>(Collections.nCopies(list.size(), -1));
        for (var callerIndex = 0; callerIndex < byteStrings.length; callerIndex++) {
            final var byteString = byteStrings[callerIndex];
            final var sortedIndex = Collections.binarySearch(list, byteString);
            indexes.set(sortedIndex, callerIndex);
        }
        if (list.get(0).byteSize() <= 0) {
            throw new IllegalArgumentException("the empty byte string is not a supported option");
        }

        // Strip elements that will never be returned because they follow their own prefixes. For
        // example, if the caller provides ["abc", "abcde"] we will never return "abcde" because we
        // return as soon as we encounter "abc".
        var a = 0;
        while (a < list.size()) {
            final var prefix = list.get(a);
            var b = a + 1;
            while (b < list.size()) {
                final var byteString = list.get(b);
                if (!byteString.startsWith(prefix)) {
                    break;
                }
                if (byteString.byteSize() == prefix.byteSize()) {
                    throw new IllegalArgumentException("duplicate option: " + byteString);
                }
                if (indexes.get(b) > indexes.get(a)) {
                    list.remove(b);
                    indexes.remove(b);
                } else {
                    b++;
                }
            }
            a++;
        }

        final var trieBytes = new RealBuffer();
        buildTrieRecursive(0L, trieBytes, 0, list, 0, list.size(), indexes);

        final var trie = new int[(int) intCount(trieBytes)];
        Arrays.setAll(trie, _unused -> trieBytes.readInt());

        return new RealOptions(byteStrings.clone() /* Defensive copy. */, trie);
    }

    /**
     * Builds a trie encoded as an int array. Nodes in the trie are of two types: SELECT and SCAN.
     * <p>
     * SELECT nodes are encoded as:
     * - selectChoiceCount: the number of bytes to choose between (a positive int)
     * - prefixIndex: the result index at the current position or -1 if the current position is not
     * a result on its own
     * - a sorted list of selectChoiceCount bytes to match against the input string
     * - a heterogeneous list of selectChoiceCount result indexes (>= 0) or offsets (< 0) of the
     * next node to follow. Elements in this list correspond to elements in the preceding list.
     * Offsets are negative and must be multiplied by -1 before being used.
     * <p>
     * SCAN nodes are encoded as:
     * - scanByteCount: the number of bytes to match in sequence. This count is negative and must
     * be multiplied by -1 before being used.
     * - prefixIndex: the result index at the current position or -1 if the current position is not
     * a result on its own
     * - a list of scanByteCount bytes to match
     * - nextStep: the result index (>= 0) or offset (< 0) of the next node to follow. Offsets are
     * negative and must be multiplied by -1 before being used.
     * <p>
     * This structure is used to improve locality and performance when selecting from a list of
     * options.
     */
    private static void buildTrieRecursive(
            long nodeOffset,
            Buffer node,
            int byteStringOffset,
            List<ByteString> byteStrings,
            int startIndex,
            int endIndex,
            List<Integer> indexes
    ) {
        if (startIndex >= endIndex) {
            throw new IllegalArgumentException("startIndex >= endIndex");
        }
        for (var i = startIndex; i < endIndex; i++) {
            if (byteStrings.get(i).byteSize() < byteStringOffset) {
                throw new IllegalArgumentException();
            }
        }

        var from = byteStrings.get(startIndex);
        final var to = byteStrings.get(endIndex - 1);
        var prefixIndex = -1;

        var _startIndex = startIndex;
        // If the first element is already matched, that's our prefix.
        if (byteStringOffset == from.byteSize()) {
            prefixIndex = indexes.get(_startIndex);
            _startIndex++;
            from = byteStrings.get(_startIndex);
        }

        if (from.getByte(byteStringOffset) != to.getByte(byteStringOffset)) {
            // If we have multiple bytes to choose from, encode a SELECT node.
            var selectChoiceCount = 1;
            for (var i = _startIndex + 1; i < endIndex; i++) {
                if (byteStrings.get(i - 1).getByte(byteStringOffset) != byteStrings.get(i).getByte(byteStringOffset)) {
                    selectChoiceCount++;
                }
            }

            // Compute the offset that childNodes will get when we append it to node.
            final var childNodesOffset = nodeOffset + intCount(node) + 2L + (selectChoiceCount * 2L);

            node.writeInt(selectChoiceCount);
            node.writeInt(prefixIndex);

            for (var i = _startIndex; i < endIndex; i++) {
                final var rangeByte = byteStrings.get(i).getByte(byteStringOffset);
                if (i == _startIndex || rangeByte != byteStrings.get(i - 1).getByte(byteStringOffset)) {
                    node.writeInt(rangeByte & 0xff);
                }
            }

            final var childNodes = new RealBuffer();
            var rangeStart = _startIndex;
            while (rangeStart < endIndex) {
                final var rangeByte = byteStrings.get(rangeStart).getByte(byteStringOffset);
                var rangeEnd = endIndex;
                for (var i = rangeStart + 1; i < endIndex; i++) {
                    if (rangeByte != byteStrings.get(i).getByte(byteStringOffset)) {
                        rangeEnd = i;
                        break;
                    }
                }

                if (rangeStart + 1 == rangeEnd &&
                        byteStringOffset + 1 == byteStrings.get(rangeStart).byteSize()
                ) {
                    // The result is a single index.
                    node.writeInt(indexes.get(rangeStart));
                } else {
                    // The result is another node.
                    node.writeInt((int) (-1 * (childNodesOffset + intCount(childNodes))));
                    buildTrieRecursive(
                            childNodesOffset,
                            childNodes,
                            byteStringOffset + 1,
                            byteStrings,
                            rangeStart,
                            rangeEnd,
                            indexes
                    );
                }

                rangeStart = rangeEnd;
            }

            node.writeAllFrom(childNodes);
        } else {
            // If all the bytes are the same, encode a SCAN node.
            var scanByteCount = 0;
            for (var i = byteStringOffset; i < Math.min(from.byteSize(), to.byteSize()); i++) {
                if (from.getByte(i) == to.getByte(i)) {
                    scanByteCount++;
                } else {
                    break;
                }
            }

            // Compute the offset that childNodes will get when we append it to node.
            final var childNodesOffset = nodeOffset + intCount(node) + 2 + scanByteCount + 1;

            node.writeInt(-scanByteCount);
            node.writeInt(prefixIndex);

            for (var i = byteStringOffset; i < byteStringOffset + scanByteCount; i++) {
                node.writeInt(from.getByte(i) & 0xff);
            }

            if (_startIndex + 1 == endIndex) {
                // The result is a single index.
                if (byteStringOffset + scanByteCount != byteStrings.get(_startIndex).byteSize()) {
                    throw new IllegalStateException();
                }
                node.writeInt(indexes.get(_startIndex));
            } else {
                // The result is another node.
                final var childNodes = new RealBuffer();
                node.writeInt((int) (-1 * (childNodesOffset + intCount(childNodes))));
                buildTrieRecursive(
                        childNodesOffset,
                        childNodes,
                        byteStringOffset + scanByteCount,
                        byteStrings,
                        _startIndex,
                        endIndex,
                        indexes
                );
                node.writeAllFrom(childNodes);
            }
        }
    }

    private static long intCount(final @NonNull Buffer buffer) {
        return buffer.bytesAvailable() / 4;
    }
}
