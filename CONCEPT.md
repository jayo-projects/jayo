## Main design
* Jayo is written in Java, but we also love Kotlin! Jayo is fully usable and optimized for Kotlin thanks to JSpecify
nullability annotations, method naming well suited for Kotlin (`get*` and `set*`) and a lot of included Kotlin extensions.
* Jayo modules use the good old Java IO (`java.io`) and NIO (`java.nio.channels`) configured in blocking-mode.
* Project Loom provides [virtual threads](https://openjdk.org/jeps/425) since Java 19 as a preview feature, so highly
efficient UDP / TCP / HTTP servers and clients, and file system operations are now possible with Java IO or NIO without
using the non-blocking mode. We are able to run as many virtual threads as needed to read and write from/to network,
without thread pools or event-loops.
* Jayo core is based on the amazing *Okio* [1] and *kotlinx-io* [2] libraries.
* Jayo never throws checked exceptions that poorly integrate with functional/lambda programming. All Jayo exceptions
extend `UncheckedIOException`.
* Jayo logs with *Java 9 Platform Logging API* [3], so it does not include any additional dependency for logs.
* Jayo is JPMS compliant

_[1] : [Okio](https://square.github.io/okio/) is a Kotlin multiplatform IO library that mainly provides useful
`ByteString` and `Buffer`. `Source` and `Sink` (and their buffered versions) offer great improvements over `InputStream`
/ `OutputStream`. \
[2] : [kotlinx-io](https://github.com/Kotlin/kotlinx-io) is a simplified and optimized fork of Okio that targets
exclusively Kotlin, with multiplatform support. \
[4] : [Java 9 Platform Logging API](https://www.baeldung.com/java-9-logging-api) has been introduced in Java to provide
a common mechanism to handle all the platform logs and to expose a service interface that can be customized by libraries
and applications. This way, the JDK platform logs can use the same logging framework as the application, and the project
dependencies can be reduced._

## Virtual threads

* Virtual threads were previewed in Java 19 and reached stable status in Java 21 LTS. They are much lighter than regular
JDK platform threads, allowing millions of them to run at the same time.
* No need for thread pooling anymore, we just create a fresh new virtual thread when needed.
* With virtual threads, we do not need complex synchronization required by multithreaded event-loop and selector-based
NIO.
* See the short-lived [non-pooled](https://openjdk.org/jeps/425#Do-not-pool-virtual-threads) nature of virtual threads.

## The on-heap VS direct ByteBuffer dilemma

Each Jayo `Segment` has to store binary data, basically we have 2 options : `byte[]` or native (direct) `ByteBuffer`.

Java's NIO channels, IO sockets since Java 13 with the new default implementation `NioSocketImpl` (see
[Reimplementation of the Legacy Socket API](https://openjdk.org/jeps/353)), and IO Datagram Sockets since Java 15 only
read and write to the network thanks to direct ByteBuffers. When using IO or NIO with on-heap ByteBuffer, the JVM uses
its internal direct ByteBuffers pool to avoid allocating and zero-fill a new direct ByteBuffer every time. However, it
will always copy binary data between heap and native memory on each I/O operation.

Using on-heap `byte[]` like we do in our `Segment` implementation does not allow avoiding this memory copy
operation from heap to native `ByteBuffer`, so it obviously cannot be called zero-copy. But it is way better than
manually allocating a new direct `ByteBuffer` ourselves because Java will use its own pool.

The `SSLEngine` implementation provided by the JDK exclusively supports on-heap ByteBuffers (see
`CipherSpi.bufferCrypt(ByteBuffer input, ByteBuffer output, boolean isUpdate)`). If direct ByteBuffers are provided to
the `SSLEngineImpl`, then some intermediate `byte[]` are created and memory copy operations are involved.

Gzip, inflater, and deflater operations based on the JDK provided classes support both `byte[]` and ByteBuffers in their
native calls, so this subject was neutral in our choice.

As we saw before, depending on what we do, the on-heap or the direct ByteBuffer works better. We chose the on-heap
option after comparing benchmark results and for the sake of simplicity. A direct ByteBuffer is slower to allocate,
read/write to, and often imply to use ugly deallocation / cleaner `Unsafe` based tricks.
Our segments use a `byte[]` to store binary data, and we provide a *lateinit* on-heap `ByteBuffer` field that is
instantiated only once when firstly required as a result of a `ByteBuffer.wrap()` call on the underlying byte array.

### Why did we create Jayo instead of using Okio?

Okio is a solid choice as a great low-level I/O library basis. However, it suffered from several drawbacks that we
wanted to solve.
* Okio is written in Kotlin, yet it is almost fully usable from Java. Kotlin is a great language for the JVM, but it
requires to include the `kotlin-stdlib` dependency. Jayo targets JVM backend projects, that are still mostly written in
plain Java, so a Java full rewrite allowed to get rid of that `kotlin-stdlib` dependency. Jayo uses no external
dependencies at all!
* Some naming enhancements are present in Jayo, they were mainly inspired by [kotlinx-io](https://github.com/Kotlin/kotlinx-io)
* Cancellation and timeout have been fully reworked.

While staying small, Jayo offers a few more tools than Okio : 
* `Utf8` and `Ascii` types were added. They are specific subclasses of `ByteString` that contains UTF-8 and ASCII
encoded bytes only.
* `NetworkEndpoint` is a nice replacement for `java.net.Socket`, and `NetworkServer` for `java.net.ServerSocket`.
* `ClientTlsEndpoint` and `ServerTlsEndpoint` are easy-to-use TLS APIs based on Jayo's reader and writer, that allow to
secure JVM applications with minimal added complexity.
* We provide our built-in `Proxy` implementation, because sadly the JVM only supports proxy on IO TCP sockets, not for
NIO nor UDP.
