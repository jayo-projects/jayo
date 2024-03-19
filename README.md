[![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?logo=apache&style=flat-square)](https://www.apache.org/licenses/LICENSE-2.0)
[![Version](https://img.shields.io/maven-central/v/dev.jayo/jayo?logo=apache-maven&color=&style=flat-square)](https://central.sonatype.com/artifact/dev.jayo/jayo)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white&style=flat-square)](https://www.java.com/en/download/help/whatis_java.html)

# Jayo

Jayo is a synchronous I/O library for the JVM based on `java.io`.

Jayo library is available on Maven Central.
```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.jayo:jayo:X.Y.Z")
}
```
```java
var freePortNumber = 54321;
var serverThread = Thread.startVirtualThread(() -> {
    try (var serverSocket = new ServerSocket(freePortNumber);
        var acceptedSocket = serverSocket.accept();
        var serverSink = Jayo.buffer(Jayo.sink(acceptedSocket))) {
        serverSink.writeUtf8("The Answer to the Ultimate Question of Life is ")
            .writeUtf8CodePoint('4')
            .writeUtf8CodePoint('2');
    } catch (IOException e) {
        fail("Unexpected exception", e);
    }
});
try (var clientSocket = new Socket("localhost", freePortNumber);
    var clientSource = Jayo.buffer(Jayo.source(clientSocket))) {
    assertThat(clientSource.readUtf8())
        .isEqualTo("The Answer to the Ultimate Question of Life is 42");
}
serverThread.join();
```

Jayo relies on Java 21 [Virtual Threads](https://wiki.openjdk.java.net/display/loom/Main), that allow to run as many
threads as we need without requiring thread pools or event-loop. With blocking code that uses virtual threads we achieve
blazing fast performances and scalability, leading to simple, readable and debuggable code.

Jayo is written in Java without any external dependencies, to stay as light as possible.

We also love Kotlin ! Jayo is fully usable and optimized from Kotlin code thanks to `@NonNull` annotations, Kotlin
friendly method naming (`get*` and `set*`) and a lot of Kotlin extension functions included in this project.

Jayo's source code is derived from [Okio](https://github.com/square/okio) and
[kotlinx-io](https://github.com/Kotlin/kotlinx-io), but does not preserve strict backward compatibility with them.

By the way, Jayo simply refers to **Ja**va **IO**, revisited.

See the project website (*coming soon*) for documentation and APIs.

Java 21 is required to use Jayo.

*Contributions are very welcome, simply clone this repo and submit a PR when your fix or feature is ready !*

## Main concepts

[Jayo](./core) offers solid I/O foundations by providing the tools we need for binary data manipulation
* `Buffer` is a mutable sequence of bytes one can easily write to and read from.
* `ByteString` is an immutable and serializable sequence of bytes that stores a String binary content.
* `RawSource` and `RawSink` (and their buffered versions `Source` and `Sink`) offer great improvements over
`InputStream` and `OutputStream`.

You can also read [concepts](CONCEPT.md) and [draft ideas](DRAFT_IDEAS.md).

### Third-party integration modules

Jayo includes modules to integrate it with third-party external libraries
* [jayo-3p-kotlinx-serialization](./third-party/kotlinx-serial) allow you to serialize JSON content directly into Jayo's sinks and
from Jayo's sources thanks to [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

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
