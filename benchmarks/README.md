You must comment benchmarks except the one you want to execute in `build.gralde.kts`

## BufferLatin1Benchmark

Benchmark                              (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferLatin1Benchmark.readLatin1Jayo        ascii        20  thrpt    5  33843237.135 ±  875461.207  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii      2000  thrpt    5   8216116.345 ±  276775.433  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii    200000  thrpt    5     33599.055 ±     788.022  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1        20  thrpt    5  34178317.612 ±  219591.881  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1      2000  thrpt    5   8036267.571 ±  113790.942  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1    200000  thrpt    5     33328.436 ±    1113.859  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii        20  thrpt    5  52856065.934 ±  634048.541  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii      2000  thrpt    5   8137480.293 ±  714164.347  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii    200000  thrpt    5     33840.273 ±     924.352  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1        20  thrpt    5  53043994.237 ±  272704.156  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1      2000  thrpt    5   8037666.006 ± 1473634.192  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1    200000  thrpt    5     33430.073 ±    1167.913  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii        20  thrpt    5  36499769.497 ±  210896.986  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii      2000  thrpt    5  19615040.564 ±   41133.500  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii    200000  thrpt    5    199327.699 ±    5999.938  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1        20  thrpt    5  36473267.010 ±  399151.586  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1      2000  thrpt    5  19734095.120 ±  221270.822  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1    200000  thrpt    5    203438.269 ±    1035.186  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii        20  thrpt    5  29833435.888 ±   78966.340  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii      2000  thrpt    5   6229558.815 ±  264384.736  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii    200000  thrpt    5     38966.338 ±     331.088  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1        20  thrpt    5  27795378.039 ±  624036.531  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1      2000  thrpt    5   5933968.232 ±   75484.837  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1    200000  thrpt    5     38970.682 ±    1087.842  ops/s

## BufferUtf8Benchmark

Benchmark                                        (encoding)  (length)   Mode  Cnt         Score        Error  Units
BufferUtf8Benchmark.readUtf8ByteStringJayo            ascii        20  thrpt    5  32059934.524 ± 351000.419  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo            ascii      2000  thrpt    5   2154186.617 ± 157726.491  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo            ascii    200000  thrpt    5     31608.809 ±   1182.571  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo           latin1        20  thrpt    5  31525000.599 ± 120882.788  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo           latin1      2000  thrpt    5    818964.115 ±  15747.929  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo           latin1    200000  thrpt    5      8571.385 ±    235.588  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo  utf8MostlyAscii        20  thrpt    5  27668155.748 ±  94505.028  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo  utf8MostlyAscii      2000  thrpt    5    914437.340 ±   8918.470  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo  utf8MostlyAscii    200000  thrpt    5      8729.128 ±    252.338  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo             utf8        20  thrpt    5  26395170.490 ± 387328.314  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo             utf8      2000  thrpt    5    492177.240 ±   7612.302  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo             utf8    200000  thrpt    5      5382.731 ±     69.942  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo           2bytes        20  thrpt    5  29151341.334 ± 222985.544  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo           2bytes      2000  thrpt    5   1045535.235 ±  43114.740  ops/s
BufferUtf8Benchmark.readUtf8ByteStringJayo           2bytes    200000  thrpt    5      7248.948 ±    612.965  ops/s
BufferUtf8Benchmark.readUtf8Jayo                      ascii        20  thrpt    5  32938728.149 ± 434687.649  ops/s
BufferUtf8Benchmark.readUtf8Jayo                      ascii      2000  thrpt    5   6558915.839 ± 501663.140  ops/s
BufferUtf8Benchmark.readUtf8Jayo                      ascii    200000  thrpt    5     29511.916 ±    241.524  ops/s
BufferUtf8Benchmark.readUtf8Jayo                     latin1        20  thrpt    5  32941221.950 ± 648125.252  ops/s
BufferUtf8Benchmark.readUtf8Jayo                     latin1      2000  thrpt    5   1085608.966 ±  10791.886  ops/s
BufferUtf8Benchmark.readUtf8Jayo                     latin1    200000  thrpt    5      8110.158 ±     78.749  ops/s
BufferUtf8Benchmark.readUtf8Jayo            utf8MostlyAscii        20  thrpt    5  21676278.414 ± 202684.497  ops/s
BufferUtf8Benchmark.readUtf8Jayo            utf8MostlyAscii      2000  thrpt    5    589918.313 ±  10995.061  ops/s
BufferUtf8Benchmark.readUtf8Jayo            utf8MostlyAscii    200000  thrpt    5      6752.447 ±     89.768  ops/s
BufferUtf8Benchmark.readUtf8Jayo                       utf8        20  thrpt    5  18416630.432 ± 198052.809  ops/s
BufferUtf8Benchmark.readUtf8Jayo                       utf8      2000  thrpt    5    242939.480 ±   4241.356  ops/s
BufferUtf8Benchmark.readUtf8Jayo                       utf8    200000  thrpt    5      2469.989 ±     60.856  ops/s
BufferUtf8Benchmark.readUtf8Jayo                     2bytes        20  thrpt    5  17478187.209 ±  30704.106  ops/s
BufferUtf8Benchmark.readUtf8Jayo                     2bytes      2000  thrpt    5    341319.521 ±   8823.804  ops/s
BufferUtf8Benchmark.readUtf8Jayo                     2bytes    200000  thrpt    5      2748.938 ±     46.463  ops/s
BufferUtf8Benchmark.readUtf8Okio                      ascii        20  thrpt    5  46384267.540 ± 402451.597  ops/s
BufferUtf8Benchmark.readUtf8Okio                      ascii      2000  thrpt    5   6971576.244 ± 410052.074  ops/s
BufferUtf8Benchmark.readUtf8Okio                      ascii    200000  thrpt    5     29630.242 ±   1419.859  ops/s
BufferUtf8Benchmark.readUtf8Okio                     latin1        20  thrpt    5  47451677.563 ± 225412.910  ops/s
BufferUtf8Benchmark.readUtf8Okio                     latin1      2000  thrpt    5    974257.104 ±   6922.459  ops/s
BufferUtf8Benchmark.readUtf8Okio                     latin1    200000  thrpt    5      8019.848 ±     57.488  ops/s
BufferUtf8Benchmark.readUtf8Okio            utf8MostlyAscii        20  thrpt    5  25322169.886 ± 132414.460  ops/s
BufferUtf8Benchmark.readUtf8Okio            utf8MostlyAscii      2000  thrpt    5    593250.897 ±  10016.600  ops/s
BufferUtf8Benchmark.readUtf8Okio            utf8MostlyAscii    200000  thrpt    5      5390.253 ±     41.642  ops/s
BufferUtf8Benchmark.readUtf8Okio                       utf8        20  thrpt    5  19834871.503 ± 230883.228  ops/s
BufferUtf8Benchmark.readUtf8Okio                       utf8      2000  thrpt    5    242900.705 ±   1191.270  ops/s
BufferUtf8Benchmark.readUtf8Okio                       utf8    200000  thrpt    5      2487.033 ±     24.158  ops/s
BufferUtf8Benchmark.readUtf8Okio                     2bytes        20  thrpt    5  19514695.856 ±  91517.985  ops/s
BufferUtf8Benchmark.readUtf8Okio                     2bytes      2000  thrpt    5    345067.316 ±   3863.782  ops/s
BufferUtf8Benchmark.readUtf8Okio                     2bytes    200000  thrpt    5      2761.710 ±     49.602  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                     ascii        20  thrpt    5  34022445.860 ± 130822.759  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                     ascii      2000  thrpt    5   5225349.256 ±  66631.606  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                     ascii    200000  thrpt    5     44308.330 ±   2524.261  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                    latin1        20  thrpt    5  34019302.604 ±  73754.794  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                    latin1      2000  thrpt    5    790806.836 ±  31051.802  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                    latin1    200000  thrpt    5      8012.502 ±     88.743  ops/s
BufferUtf8Benchmark.writeUtf8Jayo           utf8MostlyAscii        20  thrpt    5  23639751.327 ± 113056.120  ops/s
BufferUtf8Benchmark.writeUtf8Jayo           utf8MostlyAscii      2000  thrpt    5    674772.488 ±   6932.807  ops/s
BufferUtf8Benchmark.writeUtf8Jayo           utf8MostlyAscii    200000  thrpt    5      6604.659 ±    103.049  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                      utf8        20  thrpt    5  19739914.720 ±  78933.812  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                      utf8      2000  thrpt    5    310642.545 ±   3871.964  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                      utf8    200000  thrpt    5      3499.729 ±     34.360  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                    2bytes        20  thrpt    5  22988262.291 ±  35402.406  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                    2bytes      2000  thrpt    5    587313.027 ±   1492.520  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                    2bytes    200000  thrpt    5      3727.119 ±     54.708  ops/s
BufferUtf8Benchmark.writeUtf8Okio                     ascii        20  thrpt    5  25321496.534 ± 144763.928  ops/s
BufferUtf8Benchmark.writeUtf8Okio                     ascii      2000  thrpt    5    470160.182 ±  14766.412  ops/s
BufferUtf8Benchmark.writeUtf8Okio                     ascii    200000  thrpt    5      5228.101 ±     26.796  ops/s
BufferUtf8Benchmark.writeUtf8Okio                    latin1        20  thrpt    5  23631536.440 ± 138333.566  ops/s
BufferUtf8Benchmark.writeUtf8Okio                    latin1      2000  thrpt    5    256278.922 ±   6154.976  ops/s
BufferUtf8Benchmark.writeUtf8Okio                    latin1    200000  thrpt    5      2728.414 ±    177.151  ops/s
BufferUtf8Benchmark.writeUtf8Okio           utf8MostlyAscii        20  thrpt    5  25825211.141 ±  35644.843  ops/s
BufferUtf8Benchmark.writeUtf8Okio           utf8MostlyAscii      2000  thrpt    5   1630831.714 ±  13526.136  ops/s
BufferUtf8Benchmark.writeUtf8Okio           utf8MostlyAscii    200000  thrpt    5     15583.934 ±    281.346  ops/s
BufferUtf8Benchmark.writeUtf8Okio                      utf8        20  thrpt    5  16178027.543 ±  41189.148  ops/s
BufferUtf8Benchmark.writeUtf8Okio                      utf8      2000  thrpt    5    205881.421 ±    897.025  ops/s
BufferUtf8Benchmark.writeUtf8Okio                      utf8    200000  thrpt    5      2031.199 ±      6.886  ops/s
BufferUtf8Benchmark.writeUtf8Okio                    2bytes        20  thrpt    5  10224382.559 ± 239781.894  ops/s
BufferUtf8Benchmark.writeUtf8Okio                    2bytes      2000  thrpt    5    244067.928 ±   1730.957  ops/s
BufferUtf8Benchmark.writeUtf8Okio                    2bytes    200000  thrpt    5      1708.509 ±     23.473  ops/s

## JsonSerializationBenchmark

Benchmark                                         Mode  Cnt        Score        Error  Units
JsonSerializationBenchmark.jacksonFromStream     thrpt    5   684617.363 ±   5347.376  ops/s
JsonSerializationBenchmark.jacksonSmallToStream  thrpt    5  9783911.305 ± 156223.424  ops/s
JsonSerializationBenchmark.jacksonToStream       thrpt    5  1315750.753 ±  16541.348  ops/s
JsonSerializationBenchmark.kotlinxFromJayo       thrpt    5   257428.409 ±   1385.956  ops/s
JsonSerializationBenchmark.kotlinxFromOkio       thrpt    5   289551.473 ±   2567.453  ops/s
JsonSerializationBenchmark.kotlinxFromStream     thrpt    5   890960.203 ±   3866.859  ops/s
JsonSerializationBenchmark.kotlinxSmallToJayo    thrpt    5  7672497.775 ±  29467.595  ops/s
JsonSerializationBenchmark.kotlinxSmallToOkio    thrpt    5  7271182.594 ± 107977.180  ops/s
JsonSerializationBenchmark.kotlinxSmallToStream  thrpt    5  7857239.532 ±  21710.718  ops/s
JsonSerializationBenchmark.kotlinxToJayo         thrpt    5   704349.757 ±  13778.688  ops/s
JsonSerializationBenchmark.kotlinxToOkio         thrpt    5   490711.549 ±  20368.655  ops/s
JsonSerializationBenchmark.kotlinxToStream       thrpt    5   975733.219 ±   5519.163  ops/s

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
SocketReaderBenchmark.readerJayo   jayo-io  thrpt    5  45024.388 ± 3553.323  ops/s
SocketReaderBenchmark.readerJayo  jayo-nio  thrpt    5  42771.983 ± 2038.744  ops/s
SocketReaderBenchmark.readerOkio      okio  thrpt    5  42404.185 ± 3085.909  ops/s

## TcpAndJsonSerializationBenchmark

Benchmark                                            Mode  Cnt       Score       Error  Units
TcpAndJsonSerializationBenchmark.readerJayo         thrpt    5   22997.122 ±  2233.987  ops/s
TcpAndJsonSerializationBenchmark.readerOkio         thrpt    5   22813.714 ±  3438.853  ops/s
TcpAndJsonSerializationBenchmark.senderJayo         thrpt    5  459701.623 ±  9430.803  ops/s
TcpAndJsonSerializationBenchmark.senderJayoJackson  thrpt    5  478318.671 ±  7845.783  ops/s
TcpAndJsonSerializationBenchmark.senderOkio         thrpt    5  348639.577 ± 12296.264  ops/s
