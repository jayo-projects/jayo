[![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?logo=apache&style=flat-square)](https://www.apache.org/licenses/LICENSE-2.0)
[![Version](https://img.shields.io/maven-central/v/dev.jayo/jayo?logo=apache-maven&color=&style=flat-square)](https://central.sonatype.com/artifact/dev.jayo/jayo)
[![Java](https://img.shields.io/badge/Java-17%20/%2021%20/%2025-ED8B00?logo=openjdk&logoColor=white&style=flat-square)](https://www.java.com/en/download/help/whatis_java.html)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.20-blue.svg?logo=kotlin&style=flat-square)](http://kotlinlang.org)

# Jayo

A fast synchronous I/O and TLS library for the JVM.

Synchronous APIs are easier to work with than asynchronous and non-blocking APIs; the code is easier to write, easier to
read, and easier to debug (with stack traces that make sense!).

```java
// Let the system pick up a local free port
try (NetworkServer listener = NetworkServer.bindTcp(new InetSocketAddress(0))) {
  Thread serverThread = Thread.startVirtualThread(() -> {
    Socket serverSocket = listener.accept();
    try (Writer serverWriter = serverSocket.getWriter()) {
        serverWriter.write("The Answer to the Ultimate Question of Life is ")
                .writeUtf8CodePoint('4')
                .writeUtf8CodePoint('2');
    }
  });
  Socket clientSocket = NetworkSocket.connectTcp(listener.getLocalAddress());
  try (Reader clientReader = clientSocket.getReader()) {
    assertThat(clientReader.readString())
        .isEqualTo("The Answer to the Ultimate Question of Life is 42");
  }
  serverThread.join();
}
```

Jayo is available on Maven Central.

Gradle:
```groovy
dependencies {
    implementation("dev.jayo:jayo:X.Y.Z")
}
```

Maven:
```xml

<dependency>
    <groupId>dev.jayo</groupId>
    <artifactId>jayo</artifactId>
    <version>X.Y.Z</version>
</dependency>
```

Jayo is written in Java without the use of any external dependencies, to be as light as possible.

We also love Kotlin ! Jayo is fully usable and optimized from Kotlin code thanks to `@NonNull` annotations, Kotlin
friendly method naming (`get*` and `set*`) and Kotlin extension functions are natively included in this project.

Jayo's source code is derived and inspired from [Okio](https://github.com/square/okio), but does not preserve backward
compatibility with it.

By the way, Jayo simply refers to **Ja**va **IO**, revisited.

See the project website (*coming soon*) for documentation and APIs.

Jayo is a Multi-Release JAR with base Java version set to 17. Its Java 21 sources activate internal use of virtual
threads.

*Contributions are very welcome, simply clone this repo and submit a PR when your fix, new feature, or optimization is
ready !*

## Main concepts

[Jayo](./core) offers solid I/O foundations by providing the tools we need for binary data manipulation
* `Buffer` is a mutable sequence of bytes one can easily write to and read from.
* `ByteString` is an immutable and serializable sequence of bytes that stores a String related binary content as-is.
  * `Utf8` is a specific `ByteString` that contains UTF-8 encoded bytes only with a lot more functions.
* `RawReader` and `RawWriter`, and mostly their buffered versions `Reader` and `Writer`, offer great improvements over
`InputStream` and `OutputStream`.
* `NetworkSocket` is a nice replacement for `java.net.Socket`, and `NetworkServer` for `java.net.ServerSocket`.

Jayo also provides some useful tools for TLS
* `ClientTlsSocket` and `ServerTlsSocket` are easy-to-use APIs based on Jayo's reader and writer, that allow to
secure JVM applications with minimal added complexity.
* `JssePlatform` eases access to platform-specific Java Secure Socket Extension (JSSE) features.

When used within Java 21+ virtual threads (see [Project Loom](https://openjdk.org/projects/loom/)), synchronous I/O will
use non-blocking network APIs and trigger rapid context switches between a few platform OS-level threads instead of
blocking them like they did before Loom. Virtual threads allow running as many threads as we need without requiring
thread pools or event-loop.

You can also read [concepts](CONCEPT.md) and [draft ideas](DRAFT_IDEAS.md).

### Third-party integration modules

Jayo includes modules to integrate it with third-party external libraries
* [jayo-3p-kotlinx-serialization](./third-party/kotlinx-serial) allow you to serialize JSON content directly into Jayo's writers and
from Jayo's readers thanks to [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

## Build

You need a JDK 21 to build Jayo.

1. Clone this repo

```bash
git clone git@github.com:jayo-projects/jayo.git
```

2. Build the project

```bash
./gradlew clean build
```

## License

[Apache-2.0](https://opensource.org/license/apache-2-0)

Copyright (c) 2024-present, pull-vert and Jayo contributors
