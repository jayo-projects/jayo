/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.tls.helpers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.lang.System.Logger.Level.WARNING;

public class TlsTestUtil {

    // Reversed method not present before Java 21.
    public static <T> List<T> reversed(List<T> list) {
        @SuppressWarnings("unchecked")
        List<T> reversedSizes = (List<T>) new ArrayList<>(list).clone();
        Collections.reverse(reversedSizes);
        return reversedSizes;
    }

    @FunctionalInterface
    public interface ExceptionalRunnable {
        void run() throws Exception;
    }

    private static final System.Logger LOGGER = System.getLogger("jayo.tls.TlsTestUtil");

    public static void cannotFail(ExceptionalRunnable exceptionalRunnable) {
        cannotFailRunnable(exceptionalRunnable).run();
    }

    public static Runnable cannotFailRunnable(ExceptionalRunnable exceptionalRunnable) {
        return () -> {
            try {
                exceptionalRunnable.run();
            } catch (Throwable e) {
                String lastMessage = String.format(
                        "An essential thread (%s) failed unexpectedly, terminating process",
                        Thread.currentThread().getName());
                LOGGER.log(WARNING, lastMessage, e);
                System.err.println(lastMessage);
                e.printStackTrace(); // we are committing suicide, assure the reason to get through
                try {
                    Thread.sleep(1000); // give the process some time for flushing logs
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                System.exit(1);
            }
        };
    }

    public static class Memo<I, O> {
        private final ConcurrentHashMap<I, O> cache = new ConcurrentHashMap<>();
        private final Function<I, O> f;

        public Memo(Function<I, O> f) {
            this.f = f;
        }

        public O apply(I i) {
            return cache.computeIfAbsent(i, f);
        }
    }

    public static void nextBytes(SplittableRandom random, byte[] bytes) {
        nextBytes(random, bytes, bytes.length);
    }

    public static void nextBytes(SplittableRandom random, byte[] bytes, int len) {
        int i = 0;
        while (i < len) {
            int rnd = random.nextInt();
            int n = Math.min(len - i, Integer.SIZE / Byte.SIZE);
            while (n > 0) {
                bytes[i] = (byte) rnd;
                rnd >>= Byte.SIZE;
                n -= 1;
                i += 1;
            }
        }
    }
}
