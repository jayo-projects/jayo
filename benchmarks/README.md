You must comment benchmarks except the one you want to execute in `build.gralde.kts`

## BufferLatin1Benchmark

Benchmark                              (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferLatin1Benchmark.readLatin1Jayo        ascii        20  thrpt    5  31485329.355 ± 1069620.891  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii      2000  thrpt    5   6899800.491 ± 1842656.163  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii    200000  thrpt    5     32971.414 ±    1321.662  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1        20  thrpt    5  31795296.330 ±  140716.106  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1      2000  thrpt    5   7557112.800 ± 1008359.636  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1    200000  thrpt    5     33501.575 ±    1929.914  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii        20  thrpt    5  51760919.169 ±  803641.757  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii      2000  thrpt    5   7642162.530 ±  990932.164  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii    200000  thrpt    5     33507.174 ±    1246.048  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1        20  thrpt    5  52061979.008 ±  568914.683  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1      2000  thrpt    5   7724973.980 ±  900143.395  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1    200000  thrpt    5     33370.869 ±    1706.230  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii        20  thrpt    5  37914725.482 ±  402484.568  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii      2000  thrpt    5  17404789.103 ±   33188.487  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii    200000  thrpt    5    195666.315 ±    6026.285  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1        20  thrpt    5  38086776.381 ±   46245.735  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1      2000  thrpt    5  27806106.684 ±   80608.144  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1    200000  thrpt    5    194881.705 ±     166.635  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii        20  thrpt    5  28459956.997 ± 2228721.455  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii      2000  thrpt    5   5796086.062 ±  797586.823  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii    200000  thrpt    5     38068.557 ±    1016.071  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1        20  thrpt    5  28278163.395 ± 1223375.836  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1      2000  thrpt    5   5591339.675 ±  258043.321  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1    200000  thrpt    5     38991.070 ±    1782.449  ops/s

## BufferUtf8Benchmark

Benchmark                                        (encoding)  (length)   Mode  Cnt         Score        Error  Units
BufferUtf8Benchmark.readUtf8ByteStringJayo            ascii        20  thrpt    5  31461308.540 ± 321916.746  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo            ascii      2000  thrpt    5   2151735.641 ± 157876.342  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo            ascii    200000  thrpt    5     31636.077 ±   2082.289  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo           latin1        20  thrpt    5  31549523.764 ± 176470.771  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo           latin1      2000  thrpt    5    471306.327 ±  24198.190  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo           latin1    200000  thrpt    5      8923.891 ±    460.582  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo  utf8MostlyAscii        20  thrpt    5  27608124.882 ±  58720.231  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo  utf8MostlyAscii      2000  thrpt    5    917006.055 ±   7351.826  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo  utf8MostlyAscii    200000  thrpt    5      6144.131 ±   2507.367  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo             utf8        20  thrpt    5  25661799.117 ± 151904.624  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo             utf8      2000  thrpt    5    509229.960 ±  10033.908  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo             utf8    200000  thrpt    5      5406.302 ±     65.859  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo           2bytes        20  thrpt    5  29086222.161 ± 357536.700  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo           2bytes      2000  thrpt    5   1062274.411 ±  13850.973  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo           2bytes    200000  thrpt    5      7418.441 ±    166.889  ops/s
BufferUtf8Benchmark.readUtf8Jayo                      ascii        20  thrpt    5  32881603.813 ± 215210.442  ops/s
BufferUtf8Benchmark.readUtf8Jayo                      ascii      2000  thrpt    5   6248159.267 ±  99644.763  ops/s
BufferUtf8Benchmark.readUtf8Jayo                      ascii    200000  thrpt    5     29207.498 ±   1326.738  ops/s
BufferUtf8Benchmark.readUtf8Jayo                     latin1        20  thrpt    5  32862140.082 ± 132633.330  ops/s
BufferUtf8Benchmark.readUtf8Jayo                     latin1      2000  thrpt    5   1086399.928 ±  10776.858  ops/s
BufferUtf8Benchmark.readUtf8Jayo                     latin1    200000  thrpt    5      7932.393 ±    162.496  ops/s
BufferUtf8Benchmark.readUtf8Jayo            utf8MostlyAscii        20  thrpt    5  21699064.315 ± 285812.089  ops/s
BufferUtf8Benchmark.readUtf8Jayo            utf8MostlyAscii      2000  thrpt    5    581416.146 ±  10591.074  ops/s
BufferUtf8Benchmark.readUtf8Jayo            utf8MostlyAscii    200000  thrpt    5      5414.859 ±    116.712  ops/s
BufferUtf8Benchmark.readUtf8Jayo                       utf8        20  thrpt    5  18279244.486 ± 133985.302  ops/s
BufferUtf8Benchmark.readUtf8Jayo                       utf8      2000  thrpt    5    240149.779 ±   6173.109  ops/s
BufferUtf8Benchmark.readUtf8Jayo                       utf8    200000  thrpt    5      2480.062 ±     73.457  ops/s
BufferUtf8Benchmark.readUtf8Jayo                     2bytes        20  thrpt    5  17462310.756 ±  86299.900  ops/s
BufferUtf8Benchmark.readUtf8Jayo                     2bytes      2000  thrpt    5    340691.375 ±   9153.419  ops/s
BufferUtf8Benchmark.readUtf8Jayo                     2bytes    200000  thrpt    5      2739.306 ±     39.712  ops/s
BufferUtf8Benchmark.readUtf8Okio                      ascii        20  thrpt    5  46892322.878 ± 834494.386  ops/s
BufferUtf8Benchmark.readUtf8Okio                      ascii      2000  thrpt    5   7035021.726 ± 182211.512  ops/s
BufferUtf8Benchmark.readUtf8Okio                      ascii    200000  thrpt    5     29087.139 ±   1469.271  ops/s
BufferUtf8Benchmark.readUtf8Okio                     latin1        20  thrpt    5  47915905.898 ± 568057.035  ops/s
BufferUtf8Benchmark.readUtf8Okio                     latin1      2000  thrpt    5   1014873.376 ±   6781.228  ops/s
BufferUtf8Benchmark.readUtf8Okio                     latin1    200000  thrpt    5      7966.547 ±    240.422  ops/s
BufferUtf8Benchmark.readUtf8Okio            utf8MostlyAscii        20  thrpt    5  25040895.217 ± 338260.546  ops/s
BufferUtf8Benchmark.readUtf8Okio            utf8MostlyAscii      2000  thrpt    5    590474.683 ±   9130.025  ops/s
BufferUtf8Benchmark.readUtf8Okio            utf8MostlyAscii    200000  thrpt    5      5388.457 ±     75.231  ops/s
BufferUtf8Benchmark.readUtf8Okio                       utf8        20  thrpt    5  19298888.876 ± 871963.808  ops/s
BufferUtf8Benchmark.readUtf8Okio                       utf8      2000  thrpt    5    239192.211 ±   6424.914  ops/s
BufferUtf8Benchmark.readUtf8Okio                       utf8    200000  thrpt    5      2485.283 ±     25.406  ops/s
BufferUtf8Benchmark.readUtf8Okio                     2bytes        20  thrpt    5  19510768.857 ±  72417.878  ops/s
BufferUtf8Benchmark.readUtf8Okio                     2bytes      2000  thrpt    5    337301.897 ±  11311.768  ops/s
BufferUtf8Benchmark.readUtf8Okio                     2bytes    200000  thrpt    5      2744.608 ±     26.795  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                     ascii        20  thrpt    5  34230501.695 ±  77377.441  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                     ascii      2000  thrpt    5   5345881.399 ±  70358.088  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                     ascii    200000  thrpt    5     44548.656 ±   2142.431  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                    latin1        20  thrpt    5  33706673.654 ± 119819.000  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                    latin1      2000  thrpt    5    824035.337 ±  20397.207  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                    latin1    200000  thrpt    5      7983.500 ±    266.342  ops/s
BufferUtf8Benchmark.writeUtf8Jayo           utf8MostlyAscii        20  thrpt    5  23616639.846 ± 150725.084  ops/s
BufferUtf8Benchmark.writeUtf8Jayo           utf8MostlyAscii      2000  thrpt    5    670414.693 ±   5388.136  ops/s
BufferUtf8Benchmark.writeUtf8Jayo           utf8MostlyAscii    200000  thrpt    5      6632.128 ±     52.195  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                      utf8        20  thrpt    5  19702473.102 ±  49868.612  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                      utf8      2000  thrpt    5    319910.889 ±   1821.627  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                      utf8    200000  thrpt    5      2851.075 ±     25.249  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                    2bytes        20  thrpt    5  22887703.832 ± 226821.540  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                    2bytes      2000  thrpt    5    575384.925 ±  46229.371  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                    2bytes    200000  thrpt    5      2741.886 ±     35.103  ops/s
BufferUtf8Benchmark.writeUtf8Okio                     ascii        20  thrpt    5  25398038.804 ±  99831.068  ops/s
BufferUtf8Benchmark.writeUtf8Okio                     ascii      2000  thrpt    5    610784.276 ±   1872.409  ops/s
BufferUtf8Benchmark.writeUtf8Okio                     ascii    200000  thrpt    5      5114.881 ±     34.760  ops/s
BufferUtf8Benchmark.writeUtf8Okio                    latin1        20  thrpt    5  25269599.689 ± 145294.518  ops/s
BufferUtf8Benchmark.writeUtf8Okio                    latin1      2000  thrpt    5    256292.514 ±   7729.749  ops/s
BufferUtf8Benchmark.writeUtf8Okio                    latin1    200000  thrpt    5      3316.026 ±    256.343  ops/s
BufferUtf8Benchmark.writeUtf8Okio           utf8MostlyAscii        20  thrpt    5  25873724.103 ±  34076.509  ops/s
BufferUtf8Benchmark.writeUtf8Okio           utf8MostlyAscii      2000  thrpt    5   1620515.464 ±  16855.275  ops/s
BufferUtf8Benchmark.writeUtf8Okio           utf8MostlyAscii    200000  thrpt    5     16191.812 ±    236.710  ops/s
BufferUtf8Benchmark.writeUtf8Okio                      utf8        20  thrpt    5  17096095.290 ±  49524.091  ops/s
BufferUtf8Benchmark.writeUtf8Okio                      utf8      2000  thrpt    5    210147.712 ±    539.553  ops/s
BufferUtf8Benchmark.writeUtf8Okio                      utf8    200000  thrpt    5      2016.484 ±     26.706  ops/s
BufferUtf8Benchmark.writeUtf8Okio                    2bytes        20  thrpt    5  10963481.054 ± 348065.485  ops/s
BufferUtf8Benchmark.writeUtf8Okio                    2bytes      2000  thrpt    5    243011.471 ±    780.132  ops/s
BufferUtf8Benchmark.writeUtf8Okio                    2bytes    200000  thrpt    5      1662.396 ±     28.329  ops/s

## JsonSerializationBenchmark

Benchmark                                         Mode  Cnt        Score        Error  Units
JsonSerializationBenchmark.jacksonFromStream     thrpt    5   702833.104 ±  13367.592  ops/s
JsonSerializationBenchmark.jacksonSmallToStream  thrpt    5  9742877.748 ± 186365.403  ops/s
JsonSerializationBenchmark.jacksonToStream       thrpt    5  1298580.821 ±  33353.126  ops/s
JsonSerializationBenchmark.kotlinxFromJayo       thrpt    5   198257.148 ±   1111.644  ops/s
JsonSerializationBenchmark.kotlinxFromOkio       thrpt    5   290501.178 ±    413.637  ops/s
JsonSerializationBenchmark.kotlinxFromStream     thrpt    5   878194.536 ±  10475.351  ops/s
JsonSerializationBenchmark.kotlinxSmallToJayo    thrpt    5  7259242.023 ±  39208.100  ops/s
JsonSerializationBenchmark.kotlinxSmallToOkio    thrpt    5  7575559.082 ±  98341.271  ops/s
JsonSerializationBenchmark.kotlinxSmallToStream  thrpt    5  7823614.560 ±  15354.463  ops/s
JsonSerializationBenchmark.kotlinxToJayo         thrpt    5   811134.040 ±   2965.335  ops/s
JsonSerializationBenchmark.kotlinxToOkio         thrpt    5   522327.307 ±   5665.237  ops/s
JsonSerializationBenchmark.kotlinxToStream       thrpt    5   973170.206 ±  10138.059  ops/s

## SlowReaderBenchmark

Deprecated since Jayo is non-concurrent now.

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowReaderBenchmark.readerJayo    jayo  thrpt    5  1.406 ± 0.028  ops/s
SlowReaderBenchmark.sourceOkio    okio  thrpt    5  0.724 ± 0.052  ops/s

## SlowWriterBenchmark

Deprecated since Jayo is non-concurrent now.

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowWriterBenchmark.sinkOkio      okio  thrpt    5  0.964 ± 0.064  ops/s
SlowWriterBenchmark.writerJayo    jayo  thrpt    5  2.868 ± 0.167  ops/s

## SocketReaderBenchmark

Benchmark                           (type)   Mode  Cnt      Score      Error  Units
SocketReaderBenchmark.readerJayo   jayo-io  thrpt    5  44309.167 ± 4205.392  ops/s
SocketReaderBenchmark.readerJayo  jayo-nio  thrpt    5  44886.240 ±  966.981  ops/s
SocketReaderBenchmark.readerOkio      okio  thrpt    5  41373.612 ± 5465.003  ops/s

## TcpAndJsonSerializationBenchmark

Benchmark                                            Mode  Cnt       Score       Error  Units
TcpAndJsonSerializationBenchmark.readerJayo         thrpt    5   21723.665 ±  4244.658  ops/s
TcpAndJsonSerializationBenchmark.readerOkio         thrpt    5   20749.124 ±  2929.057  ops/s
TcpAndJsonSerializationBenchmark.senderJayo         thrpt    5  491301.825 ±  8363.082  ops/s
TcpAndJsonSerializationBenchmark.senderJayoJackson  thrpt    5  494534.199 ± 22901.186  ops/s
TcpAndJsonSerializationBenchmark.senderOkio         thrpt    5  361126.635 ± 10857.917  ops/s
