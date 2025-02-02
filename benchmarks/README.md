You must comment benchmarks except the one you want to execute in `build.gralde.kts`

## BufferLatin1Benchmark

Benchmark                              (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferLatin1Benchmark.readLatin1Jayo        ascii        20  thrpt    5  11441645.622 ±  115034.605  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii      2000  thrpt    5   5102515.022 ±  152107.357  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii    200000  thrpt    5     27870.065 ±    3989.122  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1        20  thrpt    5  11052004.056 ±  164263.628  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1      2000  thrpt    5   4859537.729 ±  152681.971  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1    200000  thrpt    5     28255.822 ±     705.736  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii        20  thrpt    5  24492666.615 ±  118623.693  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii      2000  thrpt    5   3315696.264 ±   97719.096  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii    200000  thrpt    5     17425.632 ±    2551.681  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1        20  thrpt    5  24469214.806 ±  328714.818  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1      2000  thrpt    5   3193338.974 ±  171533.484  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1    200000  thrpt    5     17991.463 ±     837.070  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii        20  thrpt    5  12378451.831 ±   32818.880  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii      2000  thrpt    5   9496880.155 ±   10676.614  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii    200000  thrpt    5    176176.762 ±    6237.333  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1        20  thrpt    5  12278241.511 ±   62470.725  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1      2000  thrpt    5   9131409.871 ±   22692.116  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1    200000  thrpt    5    177959.385 ±    7759.794  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii        20  thrpt    5  28997730.218 ± 2403028.148  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii      2000  thrpt    5   5849399.646 ±  661847.588  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii    200000  thrpt    5     38833.826 ±    2317.578  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1        20  thrpt    5  28706078.801 ± 1705066.849  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1      2000  thrpt    5   5615501.803 ±  755323.610  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1    200000  thrpt    5     37927.026 ±    4248.656  ops/s

## BufferUtf8Benchmark

Benchmark                                (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferUtf8Benchmark.readUtf8Jayo              ascii        20  thrpt    5  13773502.829 ± 4513936.452  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii      2000  thrpt    5   1751850.097 ±    8930.036  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii    200000  thrpt    5     25495.061 ±    3776.945  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1        20  thrpt    5  13593788.514 ± 3518925.606  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1      2000  thrpt    5    798723.213 ±   10310.225  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1    200000  thrpt    5      8874.517 ±     274.170  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8        20  thrpt    5  12930164.036 ± 2381010.712  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8      2000  thrpt    5    471469.174 ±    6114.472  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8    200000  thrpt    5      5184.141 ±      96.578  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes        20  thrpt    5  13050370.075 ± 1158604.454  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes      2000  thrpt    5    884116.583 ±    5930.231  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes    200000  thrpt    5      6178.246 ±      89.396  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii        20  thrpt    5  45454414.415 ±  676531.395  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii      2000  thrpt    5   6636045.326 ±  387318.945  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii    200000  thrpt    5     29455.200 ±    1288.384  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1        20  thrpt    5  45544302.433 ±  669734.089  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1      2000  thrpt    5    933287.643 ±   32411.500  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1    200000  thrpt    5      7783.537 ±     221.969  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8        20  thrpt    5  19860193.237 ±  238394.162  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8      2000  thrpt    5    246437.144 ±    5227.160  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8    200000  thrpt    5      2377.551 ±      39.971  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes        20  thrpt    5  19551657.308 ±   82152.180  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes      2000  thrpt    5    345922.538 ±    6056.504  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes    200000  thrpt    5      2750.559 ±      73.804  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii        20  thrpt    5  13519031.849 ±   55950.856  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii      2000  thrpt    5   4697997.320 ±  558857.681  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii    200000  thrpt    5     29214.432 ±     472.798  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1        20  thrpt    5  13455128.163 ±   50813.952  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1      2000  thrpt    5   1061567.515 ±   11298.162  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1    200000  thrpt    5      7873.472 ±     180.992  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8        20  thrpt    5  10483155.206 ±   62078.135  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8      2000  thrpt    5    245999.329 ±    2857.831  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8    200000  thrpt    5      2369.898 ±      42.951  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes        20  thrpt    5  10054858.278 ±  152319.033  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes      2000  thrpt    5    338308.310 ±    6141.924  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes    200000  thrpt    5      2734.601 ±      68.206  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii        20  thrpt    5  19283897.196 ± 7173279.186  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii      2000  thrpt    5  17225650.609 ± 7119037.932  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii    200000  thrpt    5   1513777.528 ±  505605.580  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1        20  thrpt    5  19260302.694 ± 6949884.332  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1      2000  thrpt    5  17239508.942 ± 7017838.720  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1    200000  thrpt    5   1393400.217 ±  432407.221  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8        20  thrpt    5  19112472.725 ± 6619510.516  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8      2000  thrpt    5  17270579.870 ± 6928626.814  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8    200000  thrpt    5    720688.809 ±    1486.217  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes        20  thrpt    5  19155178.811 ± 6853946.690  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes      2000  thrpt    5  17236877.995 ± 6832468.616  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes    200000  thrpt    5    719277.012 ±    3073.499  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii        20  thrpt    5  11727264.666 ±   25266.460  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii      2000  thrpt    5   1129039.793 ±   16903.997  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii    200000  thrpt    5      8190.800 ±      55.564  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1        20  thrpt    5  11444705.902 ±   17531.362  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1      2000  thrpt    5   1018476.334 ±   15826.024  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1    200000  thrpt    5     10096.980 ±      83.492  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8        20  thrpt    5   8792140.035 ±  482815.257  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8      2000  thrpt    5    237931.571 ±    2671.746  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8    200000  thrpt    5      2643.193 ±      70.955  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes        20  thrpt    5  10329258.393 ±  416994.889  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes      2000  thrpt    5    458148.787 ±    1989.989  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes    200000  thrpt    5      4942.962 ±      39.523  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii        20  thrpt    5  24401561.946 ±  319670.214  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii      2000  thrpt    5    547159.549 ±    2717.486  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii    200000  thrpt    5      4973.963 ±      56.107  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1        20  thrpt    5  25382597.664 ±  126253.844  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1      2000  thrpt    5    282809.827 ±   30390.972  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1    200000  thrpt    5      2644.128 ±      15.262  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8        20  thrpt    5  16861305.753 ±  117625.200  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8      2000  thrpt    5    206124.919 ±     341.246  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8    200000  thrpt    5      2063.285 ±       7.900  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes        20  thrpt    5  11492830.346 ±  236503.395  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes      2000  thrpt    5    244020.117 ±     612.406  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes    200000  thrpt    5      1886.680 ±      20.603  ops/s

## JsonSerializationBenchmark

Benchmark                                         Mode  Cnt        Score        Error  Units
JsonSerializationBenchmark.jacksonFromStream     thrpt    5   691276.976 ±   4858.925  ops/s
JsonSerializationBenchmark.jacksonSmallToStream  thrpt    5  9662252.618 ± 137923.470  ops/s
JsonSerializationBenchmark.jacksonToStream       thrpt    5  1315232.819 ±  13095.342  ops/s
JsonSerializationBenchmark.kotlinxFromJayo       thrpt    5    88117.709 ±    307.472  ops/s
JsonSerializationBenchmark.kotlinxFromOkio       thrpt    5   292120.217 ±    390.747  ops/s
JsonSerializationBenchmark.kotlinxFromStream     thrpt    5   894825.623 ±   5935.343  ops/s
JsonSerializationBenchmark.kotlinxSmallToJayo    thrpt    5  1585576.901 ±   4173.508  ops/s
JsonSerializationBenchmark.kotlinxSmallToOkio    thrpt    5  7234332.279 ± 160879.538  ops/s
JsonSerializationBenchmark.kotlinxSmallToStream  thrpt    5  7816074.125 ±  99439.884  ops/s
JsonSerializationBenchmark.kotlinxToJayo         thrpt    5   214100.914 ±   1278.189  ops/s
JsonSerializationBenchmark.kotlinxToOkio         thrpt    5   506628.865 ±  23787.168  ops/s
JsonSerializationBenchmark.kotlinxToStream       thrpt    5   981607.947 ±   2827.628  ops/s

## SlowReaderBenchmark

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowReaderBenchmark.readerJayo    jayo  thrpt    5  1.408 ± 0.124  ops/s
SlowReaderBenchmark.sourceOkio    okio  thrpt    5  0.719 ± 0.072  ops/s

## SlowWriterBenchmark

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowWriterBenchmark.sinkOkio      okio  thrpt    5  0.978 ± 0.047  ops/s
SlowWriterBenchmark.writerJayo    jayo  thrpt    5  2.910 ± 0.210  ops/s

## SocketReaderBenchmark

Benchmark                           (type)   Mode  Cnt      Score      Error  Units
SocketReaderBenchmark.readerJayo   jayo-io  thrpt    5  43989.463 ± 1297.103  ops/s
SocketReaderBenchmark.readerJayo  jayo-nio  thrpt    5  41131.494 ± 8580.126  ops/s
SocketReaderBenchmark.readerOkio      okio  thrpt    5  41387.732 ± 2409.025  ops/s

## TcpAndJsonSerializationBenchmark

Benchmark                                     Mode  Cnt      Score      Error  Units
TcpAndJsonSerializationBenchmark.readerJayo  thrpt    5  18453.195 ± 1109.536  ops/s
TcpAndJsonSerializationBenchmark.readerOkio  thrpt    5  20698.793 ± 1559.715  ops/s
TcpAndJsonSerializationBenchmark.senderJayo  thrpt    5   1886.880 ±   84.593  ops/s
TcpAndJsonSerializationBenchmark.senderOkio  thrpt    5   1892.732 ±  204.239  ops/s
