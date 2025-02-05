/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.tools.BasicFifoQueue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class SinglyLinkedBasicFifoQueue<T> implements BasicFifoQueue<T> {
    private @Nullable Node<T> head = null;
    private @Nullable Node<T> tail = null;

    @Override
    public boolean offer(final @NonNull T item) {
        Objects.requireNonNull(item);
        final var node = new Node<>(item);
        if (tail != null) {
            tail.next = node;
            tail = node;
            return false;
        }
        // queue was empty
        tail = node;
        head = node;
        return true;
    }

    @Override
    public T peek() {
        return head != null ? head.value : null;
    }

    @Override
    public T poll() {
        // queue was empty
        if (head == null) {
            return null;
        }
        // queue had only one item
        if (head == tail) {
            tail = null;
        }
        final var value = head.value;
        final var removed = head;
        head = head.next;
        removed.next = null;
        return value;
    }

    @Override
    public boolean isEmpty() {
        return head == null;
    }

    @Override
    public boolean contains(final @NonNull Object o) {
        Objects.requireNonNull(o);
        var node = head;
        while (node != null) {
            if (node.value.equals(o)) {
                return true;
            }
            node = node.next;
        }
        return false;
    }

    @Override
    public boolean remove(final @NonNull Object o) {
        Objects.requireNonNull(o);
        if (head == null) {
            return false;
        }
        var node = head;
        if (node.value.equals(o)) {
            head = node.next;
            return true;
        }

        var previous = head;
        node = head.next;
        while (node != null) {
            if (node.value.equals(o)) {
                if (tail == node) {
                    tail = previous;
                }
                previous.next = node.next;
                return true;
            }
            previous = node;
            node = node.next;
        }
        return false;
    }

    @Override
    public void clear() {
        // Clearing all the links between nodes is unnecessary, there should not be a lot of items left when we call the
        // clear method in our internal use cases
        head = null;
        tail = null;
    }

    @Override
    public @NonNull Iterator<T> iterator() {
        return head != null ? new SinglyLinkedIterator() : Collections.emptyIterator();
    }

    private final class SinglyLinkedIterator implements Iterator<T> {
        private final @NonNull Node<T> virtualOrigin = new Node<>(null); // ok to put null, this first item is virtual
        private @NonNull Node<T> previous;
        private @NonNull Node<T> current;
        private boolean canRemove = false;

        private SinglyLinkedIterator() {
            previous = virtualOrigin;
            current = previous;
            current.next = head;
        }

        @Override
        public boolean hasNext() {
            canRemove = false;
            return current.next != null;
        }

        @Override
        public T next() {
            if (current.next == null) {
                throw new NoSuchElementException();
            }
            previous = current;
            current = current.next;
            canRemove = true;
            return current.value;
        }

        @Override
        public void remove() {
            if (!canRemove) {
                throw new IllegalStateException();
            }
            if (head == current) {
                head = current.next;
            }
            if (tail == current) {
                tail = previous != virtualOrigin ? previous : null;
            }
            previous.next = current.next;
            current = previous;
            canRemove = false;
        }
    }

    private static final class Node<T> {
        private final @NonNull T value;
        private @Nullable Node<T> next = null;

        private Node(final @NonNull T value) {
            this.value = value;
        }
    }
}
