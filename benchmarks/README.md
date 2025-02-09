You must comment benchmarks except the one you want to execute in `build.gralde.kts`

## BufferLatin1Benchmark

Benchmark                              (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferLatin1Benchmark.readLatin1Jayo        ascii        20  thrpt    5  10408005.560 ± 2659822.514  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii      2000  thrpt    5   4363647.533 ±   48988.073  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii    200000  thrpt    5     27778.566 ±    3814.343  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1        20  thrpt    5  10534569.702 ± 2023660.066  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1      2000  thrpt    5   4563600.447 ±   55010.522  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1    200000  thrpt    5     28259.123 ±     947.044  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii        20  thrpt    5  25388436.530 ±  118406.760  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii      2000  thrpt    5   3298149.821 ±   37125.873  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii    200000  thrpt    5     17502.729 ±     321.218  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1        20  thrpt    5  24589568.373 ±   69535.758  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1      2000  thrpt    5   3301855.358 ±  144172.479  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1    200000  thrpt    5     17779.151 ±    1639.501  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii        20  thrpt    5  12148245.836 ±   69659.130  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii      2000  thrpt    5   9535205.807 ± 2192046.571  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii    200000  thrpt    5    175504.649 ±    5416.124  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1        20  thrpt    5  11913654.014 ±   54234.477  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1      2000  thrpt    5   8807604.937 ±  893774.084  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1    200000  thrpt    5    175934.855 ±    9675.335  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii        20  thrpt    5  27739903.155 ± 2780630.422  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii      2000  thrpt    5   6410280.036 ±  119670.949  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii    200000  thrpt    5     38338.368 ±    2167.158  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1        20  thrpt    5  28168551.526 ± 1706232.846  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1      2000  thrpt    5   5474363.281 ±  205283.564  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1    200000  thrpt    5     38453.852 ±    1495.209  ops/s

## BufferUtf8Benchmark

Benchmark                                (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferUtf8Benchmark.readUtf8Jayo              ascii        20  thrpt    5  11029328.569 ±   61776.775  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii      2000  thrpt    5   1694204.230 ±   56561.169  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii    200000  thrpt    5     23965.635 ±    3554.089  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1        20  thrpt    5  11007081.843 ±   39131.836  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1      2000  thrpt    5    793393.438 ±    7451.737  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1    200000  thrpt    5      8893.624 ±      56.164  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8        20  thrpt    5  10411423.075 ±   85973.813  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8      2000  thrpt    5    449605.236 ±    6964.846  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8    200000  thrpt    5      5169.905 ±      71.517  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes        20  thrpt    5  10827959.325 ±   56033.033  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes      2000  thrpt    5    862718.474 ±    7410.314  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes    200000  thrpt    5      6158.591 ±      95.589  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii        20  thrpt    5  47576239.438 ± 1043270.921  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii      2000  thrpt    5   6596733.732 ±  128058.777  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii    200000  thrpt    5     29500.532 ±    2152.737  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1        20  thrpt    5  47783878.405 ±  917096.901  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1      2000  thrpt    5    941478.295 ±   17013.509  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1    200000  thrpt    5      7925.512 ±     198.166  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8        20  thrpt    5  19913761.348 ±  151481.807  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8      2000  thrpt    5    246114.310 ±    5954.069  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8    200000  thrpt    5      2379.595 ±      55.235  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes        20  thrpt    5  19387169.929 ±  316437.212  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes      2000  thrpt    5    341490.856 ±    2851.085  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes    200000  thrpt    5      2733.492 ±      32.014  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii        20  thrpt    5  11183767.662 ±   88042.079  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii      2000  thrpt    5   4415578.867 ±  145536.832  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii    200000  thrpt    5     29148.620 ±    1179.140  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1        20  thrpt    5  11165312.767 ±  101002.848  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1      2000  thrpt    5   1048089.092 ±   29181.738  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1    200000  thrpt    5      7755.533 ±     972.243  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8        20  thrpt    5   8831932.075 ±   95756.045  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8      2000  thrpt    5    242564.999 ±    3116.886  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8    200000  thrpt    5      2331.146 ±     197.387  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes        20  thrpt    5   8534956.830 ±  554047.521  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes      2000  thrpt    5    335407.188 ±    4449.142  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes    200000  thrpt    5      2763.874 ±      21.888  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii        20  thrpt    5  13626760.386 ± 6003283.564  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii      2000  thrpt    5  12773514.074 ± 3083614.135  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii    200000  thrpt    5   1451868.979 ±    3435.405  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1        20  thrpt    5  13665493.603 ± 6028951.250  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1      2000  thrpt    5  12761623.860 ± 3055852.168  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1    200000  thrpt    5   1373842.348 ±    6500.461  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8        20  thrpt    5  13626489.782 ± 6309257.491  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8      2000  thrpt    5  12791625.761 ± 3111417.268  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8    200000  thrpt    5    754283.469 ±    4122.418  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes        20  thrpt    5  13711452.045 ± 6123383.380  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes      2000  thrpt    5  12806807.091 ± 3140583.986  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes    200000  thrpt    5    760063.549 ±    3865.234  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii        20  thrpt    5  11145029.995 ± 1298952.153  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii      2000  thrpt    5   1112232.982 ±    6060.303  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii    200000  thrpt    5      8196.960 ±      79.027  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1        20  thrpt    5  11302005.718 ±  546270.179  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1      2000  thrpt    5   1044308.950 ±   19457.107  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1    200000  thrpt    5     10198.709 ±      40.396  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8        20  thrpt    5   8445447.614 ±  464895.640  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8      2000  thrpt    5    238444.104 ±    1148.305  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8    200000  thrpt    5      2651.673 ±      28.896  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes        20  thrpt    5   9997585.848 ±  195112.618  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes      2000  thrpt    5    455004.029 ±    9119.084  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes    200000  thrpt    5      4967.222 ±      37.696  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii        20  thrpt    5  25316206.044 ±  108796.157  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii      2000  thrpt    5    545045.098 ±   25662.789  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii    200000  thrpt    5      5171.252 ±     142.294  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1        20  thrpt    5  25264834.270 ±  421798.370  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1      2000  thrpt    5    322854.469 ±   44549.104  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1    200000  thrpt    5      2650.532 ±      58.414  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8        20  thrpt    5  16639932.907 ±   78590.364  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8      2000  thrpt    5    207269.046 ±    8385.744  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8    200000  thrpt    5      2066.574 ±      16.744  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes        20  thrpt    5  11469211.698 ±   73918.294  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes      2000  thrpt    5    241405.826 ±   21664.272  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes    200000  thrpt    5      1837.586 ±      43.712  ops/s

## JsonSerializationBenchmark

Benchmark                                         Mode  Cnt        Score        Error  Units
JsonSerializationBenchmark.jacksonFromStream     thrpt    5   697463.411 ±   3846.401  ops/s
JsonSerializationBenchmark.jacksonSmallToStream  thrpt    5  9551196.029 ± 100858.879  ops/s
JsonSerializationBenchmark.jacksonToStream       thrpt    5  1303202.782 ± 129669.540  ops/s
JsonSerializationBenchmark.kotlinxFromJayo       thrpt    5    89266.993 ±   4019.886  ops/s
JsonSerializationBenchmark.kotlinxFromOkio       thrpt    5   293107.549 ±    582.249  ops/s
JsonSerializationBenchmark.kotlinxFromStream     thrpt    5   881897.623 ±   4844.540  ops/s
JsonSerializationBenchmark.kotlinxSmallToJayo    thrpt    5  1588082.731 ±  12893.364  ops/s
JsonSerializationBenchmark.kotlinxSmallToOkio    thrpt    5  7279684.528 ± 238885.246  ops/s
JsonSerializationBenchmark.kotlinxSmallToStream  thrpt    5  7811512.809 ± 121478.885  ops/s
JsonSerializationBenchmark.kotlinxToJayo         thrpt    5   208992.922 ±   4806.498  ops/s
JsonSerializationBenchmark.kotlinxToOkio         thrpt    5   508847.925 ±  12764.953  ops/s
JsonSerializationBenchmark.kotlinxToStream       thrpt    5   981922.670 ±   6360.457  ops/s

## SlowReaderBenchmark

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowReaderBenchmark.readerJayo    jayo  thrpt    5  1.406 ± 0.028  ops/s
SlowReaderBenchmark.sourceOkio    okio  thrpt    5  0.724 ± 0.052  ops/s

## SlowWriterBenchmark

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowWriterBenchmark.sinkOkio      okio  thrpt    5  0.964 ± 0.064  ops/s
SlowWriterBenchmark.writerJayo    jayo  thrpt    5  2.868 ± 0.167  ops/s

## SocketReaderBenchmark

Benchmark                           (type)   Mode  Cnt      Score      Error  Units
SocketReaderBenchmark.readerJayo   jayo-io  thrpt    5  41641.407 ± 4968.257  ops/s
SocketReaderBenchmark.readerJayo  jayo-nio  thrpt    5  42764.029 ± 2968.228  ops/s
SocketReaderBenchmark.readerOkio      okio  thrpt    5  42031.804 ± 1315.103  ops/s

## TcpAndJsonSerializationBenchmark

Benchmark                                     Mode  Cnt      Score      Error  Units
TcpAndJsonSerializationBenchmark.readerJayo  thrpt    5  18453.195 ± 1109.536  ops/s
TcpAndJsonSerializationBenchmark.readerOkio  thrpt    5  20698.793 ± 1559.715  ops/s
TcpAndJsonSerializationBenchmark.senderJayo  thrpt    5   1886.880 ±   84.593  ops/s
TcpAndJsonSerializationBenchmark.senderOkio  thrpt    5   1892.732 ±  204.239  ops/s
