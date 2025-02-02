/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.tools;

import jayo.internal.SinglyLinkedBasicFifoQueue;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A basic FIFO queue that only supports {@link #offer(Object)}, {@link #peek()}, {@link #poll()},
 * {@link #contains(Object)}, {@link #remove(Object)}, {@link #isEmpty()} and {@link #iterator()}. All other methods
 * throw {@code UnsupportedOperationException}.
 * <p>
 * <b>Be careful</b>, the returned boolean of our {@link #offer(Object)} method does not respect the
 * {@link Queue#offer(Object)} rationale, it has been adapted to our need. Read its javadoc for details.
 */
public sealed interface BasicFifoQueue<T> extends Queue<T> permits SinglyLinkedBasicFifoQueue {
    static <T> BasicFifoQueue<T> create() {
        return new SinglyLinkedBasicFifoQueue<>();
    }

    /**
     * Inserts the specified element into this queue.
     *
     * @return true if element is alone in this queue, meaning this queue was empty before that.
     * @apiNote This operation's result differs from the {@link Queue#offer(Object)} rationale. Our offer operation
     * always succeed.
     */
    @Override
    boolean offer(final @NonNull T item);

    @Override
    T peek();

    @Override
    T poll();

    @Override
    boolean isEmpty();

    @Override
    boolean contains(final @NonNull Object o);

    @Override
    boolean remove(final @NonNull Object o);

    /**
     * @return an {@code Iterator} over the elements in this queue in the same order as they were inserted.
     */
    @NonNull
    @Override
    Iterator<T> iterator();

    @Override
    default void forEach(final @NonNull Consumer<? super T> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    default int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    default Object @NonNull [] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    default <T1> T1 @NonNull [] toArray(final @NonNull T1 @NonNull [] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    default <T1> T1 @NonNull [] toArray(final @NonNull IntFunction<T1 @NonNull []> generator) {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean containsAll(final @NonNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean addAll(final @NonNull Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean removeAll(final @NonNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean removeIf(final @NonNull Predicate<? super T> filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean retainAll(final @NonNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    default void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    default @NonNull Spliterator<T> spliterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    default @NonNull Stream<T> stream() {
        throw new UnsupportedOperationException();
    }

    @Override
    default @NonNull Stream<T> parallelStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean add(final @NonNull T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    default T remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    default T element() {
        throw new UnsupportedOperationException();
    }
}
