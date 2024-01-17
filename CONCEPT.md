Main design :
* Jayo is written in Java, but we also love Kotlin ! Jayo is fully usable and optimized for Kotlin thanks to @NonNull
annotations, method naming well suited for Kotlin (`get*` and `set*`) and a lot of included Kotlin extensions.
* Jayo modules use the good old Java IO (`java.io`).
* Project Loom provides [virtual threads](https://openjdk.org/jeps/425) since Java 19 as a preview feature, so highly
efficient UDP / TCP / HTTP servers and clients, and file system operations, are now possible with Java IO or NIO without
using the non-blocking mode. We are able to run as much virtual threads as needed to read and write from/to network,
without thread pools or event-loops.
* Jayo core is based on the amazing *Okio* [1] and *kotlinx-io* [2] libraries.
* Jayo never throws checked exceptions that poorly integrates with functional/lambda programming. All Jayo exceptions
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

### Virtual threads

* Virtual threads are available as preview state in Java 19 and reached stable status in Java 21 LTS. They are much
lighter than regular JDK platform threads, allowing millions of them running at the same time.
* No need for thread pooling anymore, we just create a fresh new virtual thread when needed.
* With virtual threads, we do not need complex synchronisation required by multithreaded event-loop + Selector based
NIO.
* See short-lived [non-pooled](https://openjdk.org/jeps/425#Do-not-pool-virtual-threads) nature of virtual threads.

### Why we created Jayo instead of using Okio ?

Okio is a valid choice to use as a great low-level I/O library basis. However, it suffered from several drawbacks that
we wanted to solve.
* Okio is written in Kotlin, yet it is fully usable from Java. Kotlin is a great language for the JVM, but it needs to
include the `kotlin-stdlib` dependency. Jayo targets JVM backend projects, that are still mostly written in Java, so
a Java full rewrite allowed to get rid of that `kotlin-stdlib` dependency. Jayo uses no external dependencies at all !
* Some naming enhancements are present in Jayo, they were mainly inspired by [kotlinx-io](https://github.com/Kotlin/kotlinx-io)
* Cancellation and timeout have been fully changed.
* Virtual threads are used in `Sink` and `Source` implementations so a thread produces segments in the buffer, and
another thread consumes segments from this same buffer, asynchronously.
