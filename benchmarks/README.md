You must comment benchmarks except the one you want to execute in `build.gralde.kts`

## BufferLatin1Benchmark

Benchmark                              (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferLatin1Benchmark.readLatin1Jayo        ascii        20  thrpt    5  10092957.817 ±   95721.158  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii      2000  thrpt    5   4430705.722 ±   16744.283  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii    200000  thrpt    5     28223.922 ±    1939.793  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1        20  thrpt    5  10171955.769 ±   96947.138  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1      2000  thrpt    5   4396340.417 ±   87191.722  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1    200000  thrpt    5     28095.765 ±    1657.595  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii        20  thrpt    5  24868449.323 ±  104375.243  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii      2000  thrpt    5   3186325.245 ±  122743.324  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii    200000  thrpt    5     17240.748 ±    1982.195  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1        20  thrpt    5  23645168.187 ±   51674.888  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1      2000  thrpt    5   3198706.461 ±   46020.099  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1    200000  thrpt    5     17318.530 ±    1468.537  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii        20  thrpt    5  11058714.482 ±    6177.459  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii      2000  thrpt    5   8308117.200 ±    5888.349  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii    200000  thrpt    5    167071.373 ±    2630.485  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1        20  thrpt    5  11036934.749 ±    9969.242  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1      2000  thrpt    5  10099662.086 ±    5230.430  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1    200000  thrpt    5    174910.509 ±    4156.986  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii        20  thrpt    5  28147045.830 ± 1239077.525  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii      2000  thrpt    5   6081335.189 ±   28247.864  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii    200000  thrpt    5     37555.629 ±    3301.002  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1        20  thrpt    5  28193104.538 ±  644001.172  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1      2000  thrpt    5   5510863.661 ±  166149.398  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1    200000  thrpt    5     38754.931 ±     219.970  ops/s

## BufferUtf8Benchmark

Benchmark                                (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferUtf8Benchmark.readUtf8Jayo              ascii        20  thrpt    5   9607654.096 ±   30140.242  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii      2000  thrpt    5   1654019.235 ±   39715.053  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii    200000  thrpt    5     26862.786 ±      19.562  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1        20  thrpt    5   9592557.024 ±  120003.427  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1      2000  thrpt    5    737535.529 ±   36708.392  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1    200000  thrpt    5      8760.649 ±     262.996  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8        20  thrpt    5   9160901.024 ± 1008247.435  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8      2000  thrpt    5    518762.148 ±    5489.668  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8    200000  thrpt    5      5540.591 ±     155.260  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes        20  thrpt    5   9197456.745 ± 1093668.751  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes      2000  thrpt    5    864169.419 ±   39622.391  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes    200000  thrpt    5      6094.473 ±     102.351  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii        20  thrpt    5  45891904.679 ±  278131.755  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii      2000  thrpt    5   6558835.430 ±  285026.355  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii    200000  thrpt    5     29438.479 ±    1281.058  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1        20  thrpt    5  45573455.434 ±  454100.808  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1      2000  thrpt    5    954247.871 ±   11984.312  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1    200000  thrpt    5      6267.637 ±     144.812  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8        20  thrpt    5  21673975.407 ±  130198.039  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8      2000  thrpt    5    276450.680 ±    3659.608  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8    200000  thrpt    5      2574.093 ±     204.791  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes        20  thrpt    5  20246509.364 ±  243026.428  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes      2000  thrpt    5    452268.242 ±    2041.831  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes    200000  thrpt    5      3151.849 ±      18.299  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii        20  thrpt    5   9445409.738 ± 1360959.138  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii      2000  thrpt    5   4173656.425 ±   31663.810  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii    200000  thrpt    5     28378.935 ±    1288.391  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1        20  thrpt    5   9446244.497 ± 1385239.746  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1      2000  thrpt    5    865761.106 ±   36673.271  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1    200000  thrpt    5      6310.653 ±      40.314  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8        20  thrpt    5   7477764.659 ±   61678.888  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8      2000  thrpt    5    272229.785 ±    1932.608  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8    200000  thrpt    5      2625.329 ±     102.685  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes        20  thrpt    5   7370530.799 ±   24311.259  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes      2000  thrpt    5    431765.471 ±   16824.225  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes    200000  thrpt    5      3111.979 ±     115.112  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii        20  thrpt    5  10910486.720 ±  311784.384  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii      2000  thrpt    5  10855244.625 ±   31876.855  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii    200000  thrpt    5    897908.845 ±   30704.188  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1        20  thrpt    5  10977946.307 ±  291387.140  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1      2000  thrpt    5  10844991.322 ±  139828.517  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1    200000  thrpt    5    834181.419 ±    3068.579  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8        20  thrpt    5  10998940.679 ±   88468.287  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8      2000  thrpt    5  10766771.482 ±  267334.090  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8    200000  thrpt    5    407643.689 ±    4683.017  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes        20  thrpt    5  10939377.118 ±  274172.891  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes      2000  thrpt    5  10799230.486 ±   32806.702  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes    200000  thrpt    5    409308.736 ±    1214.207  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii        20  thrpt    5  10215511.594 ±   60303.558  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii      2000  thrpt    5   1109497.784 ±    7164.146  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii    200000  thrpt    5     10357.807 ±     124.931  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1        20  thrpt    5  10214098.434 ±   65067.977  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1      2000  thrpt    5    830789.299 ±    5536.354  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1    200000  thrpt    5      8151.896 ±       3.504  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8        20  thrpt    5   8094331.713 ±  279087.699  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8      2000  thrpt    5    235469.697 ±    2599.075  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8    200000  thrpt    5      2529.904 ±      18.041  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes        20  thrpt    5   9595402.059 ±  461178.759  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes      2000  thrpt    5    456045.515 ±    7067.742  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes    200000  thrpt    5      4970.951 ±      50.896  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii        20  thrpt    5  28682120.912 ±   59511.189  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii      2000  thrpt    5   1876261.700 ±    7729.854  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii    200000  thrpt    5     16594.516 ±     247.551  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1        20  thrpt    5  28675147.368 ±   31713.850  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1      2000  thrpt    5   1187861.730 ±    4419.831  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1    200000  thrpt    5      8621.498 ±     195.644  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8        20  thrpt    5  16949483.554 ±   68787.597  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8      2000  thrpt    5    222127.808 ±     187.721  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8    200000  thrpt    5      2008.876 ±      33.385  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes        20  thrpt    5   8411014.478 ±  272071.345  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes      2000  thrpt    5    245236.450 ±     340.068  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes    200000  thrpt    5      1852.736 ±      43.473  ops/s

## JsonSerializationBenchmark

Benchmark                                         Mode  Cnt         Score       Error  Units
JsonSerializationBenchmark.jacksonFromStream     thrpt    5    687650.433 ±  5069.087  ops/s
JsonSerializationBenchmark.jacksonSmallToStream  thrpt    5  10914777.588 ± 63318.818  ops/s
JsonSerializationBenchmark.jacksonToStream       thrpt    5   1760362.476 ± 16246.094  ops/s
JsonSerializationBenchmark.kotlinxFromJayo       thrpt    5     88802.630 ±   761.519  ops/s
JsonSerializationBenchmark.kotlinxFromOkio       thrpt    5    290168.612 ±  3811.645  ops/s
JsonSerializationBenchmark.kotlinxFromStream     thrpt    5    870939.983 ±  9854.816  ops/s
JsonSerializationBenchmark.kotlinxSmallToJayo    thrpt    5   1599421.205 ± 18781.293  ops/s
JsonSerializationBenchmark.kotlinxSmallToOkio    thrpt    5   7646564.841 ± 92597.783  ops/s
JsonSerializationBenchmark.kotlinxSmallToStream  thrpt    5   7708147.427 ± 74971.534  ops/s
JsonSerializationBenchmark.kotlinxToJayo         thrpt    5    230981.369 ±  2106.655  ops/s
JsonSerializationBenchmark.kotlinxToOkio         thrpt    5    816681.579 ±  8367.758  ops/s
JsonSerializationBenchmark.kotlinxToStream       thrpt    5    914737.943 ±  8646.131  ops/s

## SlowReaderBenchmark

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowReaderBenchmark.readerJayo    jayo  thrpt    5  1.386 ± 0.071  ops/s
SlowReaderBenchmark.sourceOkio    okio  thrpt    5  0.732 ± 0.035  ops/s

## SlowWriterBenchmark

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowWriterBenchmark.sinkOkio      okio  thrpt    5  0.952 ± 0.043  ops/s
SlowWriterBenchmark.writerJayo    jayo  thrpt    5  2.878 ± 0.186  ops/s

## SocketReaderBenchmark

Benchmark                           (type)   Mode  Cnt      Score      Error  Units
SocketReaderBenchmark.readerJayo   jayo-io  thrpt    5  40136.438 ± 6479.301  ops/s
SocketReaderBenchmark.readerJayo  jayo-nio  thrpt    5  40425.845 ± 4430.204  ops/s
SocketReaderBenchmark.readerOkio      okio  thrpt    5  39895.467 ± 3488.032  ops/s

## TcpAndJsonSerializationBenchmark

Benchmark                                     Mode  Cnt      Score      Error  Units
TcpAndJsonSerializationBenchmark.readerJayo  thrpt    5  18453.195 ± 1109.536  ops/s
TcpAndJsonSerializationBenchmark.readerOkio  thrpt    5  20698.793 ± 1559.715  ops/s
TcpAndJsonSerializationBenchmark.senderJayo  thrpt    5   1886.880 ±   84.593  ops/s
TcpAndJsonSerializationBenchmark.senderOkio  thrpt    5   1892.732 ±  204.239  ops/s
