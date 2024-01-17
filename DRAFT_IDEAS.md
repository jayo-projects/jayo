## A few code and design ideas, that may or may not be implemented
* Jayo loves Kotlin !
  * Support Kotlin specific types like `Sequence<T>`, `kotlin.time.DurationUnit`, and maybe some coroutine's stuff
like `Flow<T>` must only be implemented as extension functions (= Java will not see it).
  * If any Kotlin specific dependency has to be supported (
[kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) ?), it must be declared as `compileOnly` in gradle
build.
  * Expose [Type-safe Builders](https://kotlinlang.org/docs/type-safe-builders.html) for Kotlin to configure client and
server options
* For server modules : a platform Thread can be used to Accept incoming connections, and virtual thread will handle all
the rest.
* Servers will have a `close` method (from `AutoClosable`) that will refuse new incoming connections and requests, but
will wait until current responses are returned. A `closeImmediatly` could be provided also, that aggressively closes
immediately.
* Shenandoah and ZGC new garbage collectors are available now, and improvements have been added to the other garbage
collectors like G1. We could provide a guideline to GC configuration for Jayo users.

## Nice Java features Jayo will or could use

* Java 1.8
  * [`LongAdder` and `LongAccumulator`](https://www.baeldung.com/java-longadder-and-longaccumulator) : expose lock-free
operations with longs (also exists with doubles)
* Java 9
  * Project Jigsaw JPMS modules
  * [Compact Strings](https://openjdk.org/jeps/254) : The new String class store characters encoded either as
ISO-8859-1/Latin-1 (one byte per character), or as UTF-16 (two bytes per character).
* Java 11
  * Adds TLSv1.3 support
* Java 13
  * [Reimplementation of the Legacy Socket API](https://openjdk.org/jeps/353) with non-blocking under the hood
* Java 14
  * Foreign memory (incubator)
  * [Non-Volatile Mapped Byte Buffers](https://openjdk.org/jeps/352)
* Java 15
  * [Reimplementation of the Legacy DatagramSocket API](https://openjdk.org/jeps/373) with non-blocking under the hood
* Java 16
  * [Unix-domain Socket channel](https://openjdk.org/jeps/380)
* Java 18
  * [Internet-address resolution SPI](https://openjdk.org/jeps/418)
* Java 19
  * Project Loom virtual threads (preview)
  * Foreign memory (promoted from incubator to preview)
  * Structured Concurrency (incubator)
  * Quic / HTTP3
    * IP_DONTFRAGMENT was added, it is required for the QUIC implementation, see
[this JDK issue](https://bugs.openjdk.org/browse/JDK-8284890),
https://www.rfc-editor.org/rfc/rfc9000.html#name-datagram-size
`datagramSocket.setOption(ExtendedSocketOptions.IP_DONTFRAGMENT, true)`
* Java 20
  * Scoped Values (incubator)
* Java 21
  * Project Loom [virtual threads](https://openjdk.org/jeps/444) are promoted as stable
  * **Warning -> preview** : [Structured Concurrency](https://openjdk.org/jeps/453) treats multiple tasks running in
different threads as a single unit of work, thereby streamlining error handling and cancellation, improving reliability,
and enhancing observability.
  * **Warning -> preview** : [Scoped Values](https://openjdk.org/jeps/446) enable the sharing of immutable data within
and across threads. They are preferred to thread-local variables, especially when using large numbers of virtual
threads.
  * [Sequenced Collections](https://www.baeldung.com/java-21-sequenced-collections) This feature injects new interfaces
into the existing Java collections hierarchy, offering a seamless mechanism to access the first and last elements of a
collection using built-in default methods. Moreover, it provides support to obtain a reversed view of the collection.
* Java 22
  * [Foreign memory](https://openjdk.org/jeps/454) is promoted as stable. Sophisticated clients deserve an API that
can allocate, manipulate, and share off-heap memory with the same fluidity and safety as on-heap memory. Such an API
should balance the need for predictable deallocation with the need to prevent untimely deallocation that can lead to JVM
crashes or, worse, to silent memory corruption.

## Some inspirations

* Okio documentation and related articles
    * [Okio recipes](https://square.github.io/okio/recipes)
    * [Okio with socket](https://square.github.io/okio/recipes/#communicate-on-a-socket-javakotlin)
    * [A closer look at the Okio library](https://medium.com/@jerzy.chalupski/a-closer-look-at-the-okio-library-90336e37261)
    * [Okio.Options](https://medium.com/@jerzy.chalupski/okio-options-ce8f3ac1584f)
    * [Forcing bytes downward in Okio : flush() VS emit() VS emitCompleteSegments()](https://jakewharton.com/forcing-bytes-downward-in-okio/)
    * With a file you’re either reading or writing but with the network you can do both! Some protocols handle this by
taking turns: write a request, read a response, repeat. You can implement this kind of protocol with a single thread.
In other protocols you may read and write simultaneously. Typically, you’ll want one dedicated thread for reading. For
writing, you can use either a single dedicated thread or use `Locks` so that multiple threads can share a sink. Okio’s
streams are not safe for concurrent use.
* [kotlinx-io](https://github.com/Kotlin/kotlinx-io) is a Kotlin only simplified and optimized fork of Okio
* [ktor-io](https://github.com/ktorio/ktor/tree/main/ktor-io) is a Kotlin only coroutines I/O framework
* [Netty](https://github.com/netty/netty) is the most advanced and powerful async I/O framework written in Java
* [Helidon Nima](https://medium.com/helidon/helidon-n%C3%ADma-helidon-on-virtual-threads-130bb2ea2088) is a new recent
web server running on virtual threads
* [Quasar](https://github.com/puniverse/quasar) is the loom ancestor on JDK
* [Chronicle Bytes](https://github.com/OpenHFT/Chronicle-Bytes)
* Nice usage of java `Stream<T>` like in Spring's `ResultSetSpliterator` in `JdbcTemplate` class
* [Loom support added in Okio](https://github.com/square/okio/commit/f8434f575787198928a26334758ddbca9726b11c)
* [Channel per-stream for HTTP2 in Netty](https://github.com/netty/netty/pull/11603)
* [TLS implementation for NIO](https://github.com/marianobarrios/tls-channel)
* [Timeouts and cancellation for humans](https://vorpus.org/blog/timeouts-and-cancellation-for-humans/) inspiration for
the cancellation mechanism in Jayo
