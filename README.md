[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)](https://www.java.com/en/download/help/whatis_java.html)

# Jayo

Jayo is a synchronous I/O library for the JVM based on `java.io`. This leads to simple, readable and debuggable code,
just like a standard blocking program, yet it executes non-blocking I/O under the hood.

Jayo heavily relies on [virtual threads](https://wiki.openjdk.java.net/display/loom/Main), that allow to run as many
threads as we need without requiring thread pools or event-loop.

Jayo is written in Java without any external dependencies, to stay as light as possible. But we also love Kotlin ! Jayo
is fully usable and optimized from Kotlin code thanks to `@NonNull` annotations, Kotlin friendly method naming (`get*`
and `set*`) and a lot of Kotlin extension functions included in thi project.

Jayo's source code is derived from [Okio](https://github.com/square/okio) and
[kotlinx-io](https://github.com/Kotlin/kotlinx-io), but does not preserve strict backward compatibility with them.

See the project website (*coming soon*) for documentation and APIs.

You can also read [concepts](CONCEPT.md) and [draft ideas](DRAFT_IDEAS.md).

By the way, Jayo simply refers to **Ja**va **IO**, revisited.

## Requirements

Jayo requires at least Java 21.

## Main concepts

[jayo](./core) offers solid I/O foundations by providing the tools we need for binary data manipulation
* `Buffer` is a mutable sequence of bytes one can easily write to and read from.
* `ByteString` is an immutable and serializable sequence of bytes that stores a String binary content.
* `RawSource` and `RawSink` (and their buffered versions `Source` and `Sink`) offer great improvements over
`InputStream` and `OutputStream`.

### Third-party integration modules

Jayo includes modules to integrate it with third-party external libraries
* [jayo-3p-kotlinx-serialization](./third-party/kotlinx-serial) allow you to serialize JSON content directly into Jayo's sinks and
from Jayo's sources thanks to [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

## Contributors

Contributions are very welcome.

Use a JDK 21 to compile Jayo

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
