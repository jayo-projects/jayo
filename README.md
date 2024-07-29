[![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?logo=apache&style=flat-square)](https://www.apache.org/licenses/LICENSE-2.0)
[![Version](https://img.shields.io/maven-central/v/dev.jayo/jayo?logo=apache-maven&color=&style=flat-square)](https://central.sonatype.com/artifact/dev.jayo/jayo)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white&style=flat-square)](https://www.java.com/en/download/help/whatis_java.html)

# Jayo

A fast and simple synchronous I/O library for the JVM that relies on Java 21+ virtual threads, see
[Project Loom](https://openjdk.org/projects/loom/).

Virtual threads allow to run as many threads as we need without requiring thread pools or event-loop. With blocking IO
that runs inside virtual threads, Jayo offers great performances and scalability, leading to simple, readable and
debuggable code.

Jayo is available on Maven Central.

Maven:

```xml

<dependency>
    <groupId>dev.jayo</groupId>
    <artifactId>jayo</artifactId>
    <version>X.Y.Z</version>
</dependency>
```

Gradle:

```groovy
dependencies {
    implementation("dev.jayo:jayo:X.Y.Z")
}
```

```java
var freePortNumber = 54321;
var serverThread = Thread.startVirtualThread(() -> {
    try (var serverSocket = new ServerSocket(freePortNumber);
        var acceptedSocket = serverSocket.accept();
        var serverWriter = Jayo.buffer(Jayo.writer(acceptedSocket))) {
        serverWriter.writeUtf8("The Answer to the Ultimate Question of Life is ")
            .writeUtf8CodePoint('4')
            .writeUtf8CodePoint('2');
    } catch (IOException e) {
        fail("Unexpected exception", e);
    }
});
try (var clientSocket = new Socket("localhost", freePortNumber);
    var clientReader = Jayo.buffer(Jayo.reader(clientSocket))) {
    assertThat(clientReader.readUtf8String())
        .isEqualTo("The Answer to the Ultimate Question of Life is 42");
}
serverThread.join();
```

Jayo is written in Java without any external dependencies, to stay as light as possible.

We also love Kotlin ! Jayo is fully usable and optimized from Kotlin code thanks to `@NonNull` annotations, Kotlin
friendly method naming (`get*` and `set*`) and a lot of Kotlin extension functions are natively included in this project.

Jayo's source code is derived and inspired from [Okio](https://github.com/square/okio) and
[kotlinx-io](https://github.com/Kotlin/kotlinx-io), but does not preserve strict backward compatibility with them.

By the way, Jayo simply refers to **Ja**va **IO**, revisited.

See the project website (*coming soon*) for documentation and APIs.

Java 21 is required to use Jayo.

*Contributions are very welcome, simply clone this repo and submit a PR when your fix or feature is ready !*

## Main concepts

[Jayo](./core) offers solid I/O foundations by providing the tools we need for binary data manipulation
* `Buffer` is a mutable sequence of bytes one can easily write to and read from.
* `ByteString` is an immutable and serializable sequence of bytes that stores a String related binary content
  * `Utf8` is a `ByteString` that contains UTF-8 encoded bytes only.
* `RawReader` and `RawWriter` (and their buffered versions `Reader` and `Writer`) offer great improvements over
`InputStream` and `OutputStream`.

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
