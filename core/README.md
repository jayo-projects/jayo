# jayo-core

`jayo-core` module makes I/O on the JVM easier by providing you a deliberately reduced range of powerful tools. \
It is based the amazing *Okio library* [1], but does not preserve backward compatibility with it, that has been
rewritten in Java. \
It also contains some naming, designs and documentation from the great *kotlinx-io library* [2].

_[1] : [Okio](https://square.github.io/okio/) is a very nice Kotlin multiplatform IO library ! \
[2] : [kotlinx-io](https://github.com/Kotlin/kotlinx-io) is a simplified and optimized fork of Okio that targets
exclusively Kotlin, with multiplatform support._

## Buffer and ByteString

* `Buffer` is a mutable sequence of bytes one can easily write to and read from. It works like a Queue and will
automatically grow when you write data to its end, and shrink when you read data from the start.
* `ByteString` is an immutable and serializable sequence of bytes that stores a String binary content as-is. You can
encode or decode it as hex, base64, and UTF-8, you can also search content in it, build substring and much more.

Buffer relies on a linked list of memory segments. It is optimized fo zero-copy segment sharing with other buffers and
uses pooled `byte[]` based segments to avoid GC churn and zero-fill of the memory.

## Sources and Sinks

`RawSource` and `RawSink`, although conceptually identical, offer great improvements over `InputStream` and
`OutputStream`
* Create a `RawSource` for reading from or a `RawSink` for writing to a file or a network socket, then just obtain a
buffered `Source` or a `Sink` from it. With them, you have access to feature rich interfaces that provide all you need,
yet remaining relatively light with a few dozen useful functions.
* `RawSource` and `RawSink`, and their buffered versions, are all you need ; wether you manipulate String, ByteStrings,
numbers, pure binary content and so on. No more additional readers needed here.
* `RawSource` and `RawSink` are reasonably easy to implement, feel free to try it !

## Timeouts and cancellation

Define cancellable code blocks thanks to our **cancellable builders** (like `Cancellable.withTimeout(..)`,
`Cancellable.executeCancellable(..)`, etc.) that creates a `CancelScope` implementation called a *CancelToken* that is
bound to the current thread thanks to ThreadLocal or Scoped Values, and will automatically propagate cancellation and
timeouts to inner children threads, if any.

The *CancelToken* will only apply inside the code block, and will be removed when the code block has ended.
