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

Benchmark                                     (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferUtf8Benchmark.readUtf8Jayo                   ascii        20  thrpt    5  11658763.440 ±   48154.550  ops/s
BufferUtf8Benchmark.readUtf8Jayo                   ascii      2000  thrpt    5   1877774.103 ±   19235.071  ops/s
BufferUtf8Benchmark.readUtf8Jayo                   ascii    200000  thrpt    5     31140.677 ±      45.987  ops/s
BufferUtf8Benchmark.readUtf8Jayo                  latin1        20  thrpt    5  11646272.778 ±   94108.636  ops/s
BufferUtf8Benchmark.readUtf8Jayo                  latin1      2000  thrpt    5    752834.840 ±   25818.631  ops/s
BufferUtf8Benchmark.readUtf8Jayo                  latin1    200000  thrpt    5      8522.461 ±     220.736  ops/s
BufferUtf8Benchmark.readUtf8Jayo         utf8MostlyAscii        20  thrpt    5  10868947.355 ±  100159.117  ops/s
BufferUtf8Benchmark.readUtf8Jayo         utf8MostlyAscii      2000  thrpt    5    766311.432 ±   14445.796  ops/s
BufferUtf8Benchmark.readUtf8Jayo         utf8MostlyAscii    200000  thrpt    5      9764.875 ±     151.883  ops/s
BufferUtf8Benchmark.readUtf8Jayo                    utf8        20  thrpt    5  10993241.566 ±   93018.907  ops/s
BufferUtf8Benchmark.readUtf8Jayo                    utf8      2000  thrpt    5    448613.139 ±    9282.406  ops/s
BufferUtf8Benchmark.readUtf8Jayo                    utf8    200000  thrpt    5      5451.370 ±     156.386  ops/s
BufferUtf8Benchmark.readUtf8Jayo                  2bytes        20  thrpt    5  11357481.131 ±   62261.258  ops/s
BufferUtf8Benchmark.readUtf8Jayo                  2bytes      2000  thrpt    5   1032565.317 ±   23853.593  ops/s
BufferUtf8Benchmark.readUtf8Jayo                  2bytes    200000  thrpt    5      7219.863 ±      99.333  ops/s
BufferUtf8Benchmark.readUtf8Okio                   ascii        20  thrpt    5  45585674.032 ± 1394695.797  ops/s
BufferUtf8Benchmark.readUtf8Okio                   ascii      2000  thrpt    5   6737631.620 ±  743935.193  ops/s
BufferUtf8Benchmark.readUtf8Okio                   ascii    200000  thrpt    5     28966.371 ±    1257.989  ops/s
BufferUtf8Benchmark.readUtf8Okio                  latin1        20  thrpt    5  47335672.014 ±  541010.731  ops/s
BufferUtf8Benchmark.readUtf8Okio                  latin1      2000  thrpt    5    956938.603 ±    6157.448  ops/s
BufferUtf8Benchmark.readUtf8Okio                  latin1    200000  thrpt    5      7965.860 ±     182.693  ops/s
BufferUtf8Benchmark.readUtf8Okio         utf8MostlyAscii        20  thrpt    5  24015527.559 ±  158250.193  ops/s
BufferUtf8Benchmark.readUtf8Okio         utf8MostlyAscii      2000  thrpt    5    593315.063 ±    3434.034  ops/s
BufferUtf8Benchmark.readUtf8Okio         utf8MostlyAscii    200000  thrpt    5      5142.819 ±     543.803  ops/s
BufferUtf8Benchmark.readUtf8Okio                    utf8        20  thrpt    5  19838268.850 ±  340369.393  ops/s
BufferUtf8Benchmark.readUtf8Okio                    utf8      2000  thrpt    5    247879.275 ±    3067.361  ops/s
BufferUtf8Benchmark.readUtf8Okio                    utf8    200000  thrpt    5      2378.850 ±      65.642  ops/s
BufferUtf8Benchmark.readUtf8Okio                  2bytes        20  thrpt    5  19345291.505 ±  409594.205  ops/s
BufferUtf8Benchmark.readUtf8Okio                  2bytes      2000  thrpt    5    343764.895 ±    2605.211  ops/s
BufferUtf8Benchmark.readUtf8Okio                  2bytes    200000  thrpt    5      2780.293 ±      46.491  ops/s
BufferUtf8Benchmark.readUtf8StringJayo             ascii        20  thrpt    5  11822305.439 ±  134360.805  ops/s
BufferUtf8Benchmark.readUtf8StringJayo             ascii      2000  thrpt    5   4578161.611 ±  439497.598  ops/s
BufferUtf8Benchmark.readUtf8StringJayo             ascii    200000  thrpt    5     29059.227 ±     522.225  ops/s
BufferUtf8Benchmark.readUtf8StringJayo            latin1        20  thrpt    5  11894262.783 ±   29632.603  ops/s
BufferUtf8Benchmark.readUtf8StringJayo            latin1      2000  thrpt    5   1045543.955 ±   35068.098  ops/s
BufferUtf8Benchmark.readUtf8StringJayo            latin1    200000  thrpt    5      8027.763 ±     138.389  ops/s
BufferUtf8Benchmark.readUtf8StringJayo   utf8MostlyAscii        20  thrpt    5  10158355.270 ±  158093.854  ops/s
BufferUtf8Benchmark.readUtf8StringJayo   utf8MostlyAscii      2000  thrpt    5    571259.960 ±    7961.411  ops/s
BufferUtf8Benchmark.readUtf8StringJayo   utf8MostlyAscii    200000  thrpt    5      5331.135 ±     146.630  ops/s
BufferUtf8Benchmark.readUtf8StringJayo              utf8        20  thrpt    5   9117602.194 ±   88393.256  ops/s
BufferUtf8Benchmark.readUtf8StringJayo              utf8      2000  thrpt    5    244585.382 ±    1834.298  ops/s
BufferUtf8Benchmark.readUtf8StringJayo              utf8    200000  thrpt    5      2383.359 ±      19.943  ops/s
BufferUtf8Benchmark.readUtf8StringJayo            2bytes        20  thrpt    5   9012732.887 ±   27101.347  ops/s
BufferUtf8Benchmark.readUtf8StringJayo            2bytes      2000  thrpt    5    333829.729 ±    8428.392  ops/s
BufferUtf8Benchmark.readUtf8StringJayo            2bytes    200000  thrpt    5      2748.659 ±      58.381  ops/s
BufferUtf8Benchmark.writeByteStringJayo            ascii        20  thrpt    5  14353803.641 ± 6857050.954  ops/s
BufferUtf8Benchmark.writeByteStringJayo            ascii      2000  thrpt    5  13359648.308 ±  538061.770  ops/s
BufferUtf8Benchmark.writeByteStringJayo            ascii    200000  thrpt    5   1610099.516 ±   10354.564  ops/s
BufferUtf8Benchmark.writeByteStringJayo           latin1        20  thrpt    5  14412105.982 ± 7112023.145  ops/s
BufferUtf8Benchmark.writeByteStringJayo           latin1      2000  thrpt    5  13326168.949 ±  649291.463  ops/s
BufferUtf8Benchmark.writeByteStringJayo           latin1    200000  thrpt    5   1516982.318 ±    6048.355  ops/s
BufferUtf8Benchmark.writeByteStringJayo  utf8MostlyAscii        20  thrpt    5  14355330.982 ± 6803687.205  ops/s
BufferUtf8Benchmark.writeByteStringJayo  utf8MostlyAscii      2000  thrpt    5  13363175.993 ±  743928.086  ops/s
BufferUtf8Benchmark.writeByteStringJayo  utf8MostlyAscii    200000  thrpt    5   1529016.927 ±    6501.484  ops/s
BufferUtf8Benchmark.writeByteStringJayo             utf8        20  thrpt    5  14315264.621 ± 6403135.132  ops/s
BufferUtf8Benchmark.writeByteStringJayo             utf8      2000  thrpt    5  12996094.282 ±  526088.651  ops/s
BufferUtf8Benchmark.writeByteStringJayo             utf8    200000  thrpt    5    821493.453 ±    4659.735  ops/s
BufferUtf8Benchmark.writeByteStringJayo           2bytes        20  thrpt    5  14343266.984 ± 6570124.063  ops/s
BufferUtf8Benchmark.writeByteStringJayo           2bytes      2000  thrpt    5  13359194.665 ±  485465.911  ops/s
BufferUtf8Benchmark.writeByteStringJayo           2bytes    200000  thrpt    5    832543.062 ±    3710.442  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                  ascii        20  thrpt    5  12463511.126 ±  111258.957  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                  ascii      2000  thrpt    5   1311770.197 ±   31042.907  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                  ascii    200000  thrpt    5      8194.607 ±      56.131  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                 latin1        20  thrpt    5  12433961.148 ±  158018.641  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                 latin1      2000  thrpt    5   1005183.293 ±    7461.922  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                 latin1    200000  thrpt    5     10215.777 ±      13.351  ops/s
BufferUtf8Benchmark.writeUtf8Jayo        utf8MostlyAscii        20  thrpt    5  11211176.072 ±  216472.339  ops/s
BufferUtf8Benchmark.writeUtf8Jayo        utf8MostlyAscii      2000  thrpt    5   1077840.859 ±    3060.030  ops/s
BufferUtf8Benchmark.writeUtf8Jayo        utf8MostlyAscii    200000  thrpt    5     10298.936 ±      36.755  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                   utf8        20  thrpt    5   8979038.201 ±  278890.013  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                   utf8      2000  thrpt    5    239172.525 ±    1507.863  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                   utf8    200000  thrpt    5      2653.441 ±      31.125  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                 2bytes        20  thrpt    5  10867915.738 ±   24211.451  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                 2bytes      2000  thrpt    5    456618.778 ±    7138.511  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                 2bytes    200000  thrpt    5      4954.272 ±      64.078  ops/s
BufferUtf8Benchmark.writeUtf8Okio                  ascii        20  thrpt    5  25301109.744 ±   33977.080  ops/s
BufferUtf8Benchmark.writeUtf8Okio                  ascii      2000  thrpt    5    552817.261 ±    1355.397  ops/s
BufferUtf8Benchmark.writeUtf8Okio                  ascii    200000  thrpt    5      5145.845 ±     166.146  ops/s
BufferUtf8Benchmark.writeUtf8Okio                 latin1        20  thrpt    5  25349069.671 ±  161213.672  ops/s
BufferUtf8Benchmark.writeUtf8Okio                 latin1      2000  thrpt    5    264739.081 ±   18536.604  ops/s
BufferUtf8Benchmark.writeUtf8Okio                 latin1    200000  thrpt    5      2645.751 ±     103.703  ops/s
BufferUtf8Benchmark.writeUtf8Okio        utf8MostlyAscii        20  thrpt    5  25732793.602 ±   90545.235  ops/s
BufferUtf8Benchmark.writeUtf8Okio        utf8MostlyAscii      2000  thrpt    5   1621876.668 ±   51230.151  ops/s
BufferUtf8Benchmark.writeUtf8Okio        utf8MostlyAscii    200000  thrpt    5     16169.542 ±     402.574  ops/s
BufferUtf8Benchmark.writeUtf8Okio                   utf8        20  thrpt    5  16865544.362 ±   81507.241  ops/s
BufferUtf8Benchmark.writeUtf8Okio                   utf8      2000  thrpt    5    209048.262 ±    1149.325  ops/s
BufferUtf8Benchmark.writeUtf8Okio                   utf8    200000  thrpt    5      2054.599 ±      49.373  ops/s
BufferUtf8Benchmark.writeUtf8Okio                 2bytes        20  thrpt    5  10258055.903 ±  337771.257  ops/s
BufferUtf8Benchmark.writeUtf8Okio                 2bytes      2000  thrpt    5    244005.516 ±    4866.587  ops/s
BufferUtf8Benchmark.writeUtf8Okio                 2bytes    200000  thrpt    5      1853.102 ±       6.050  ops/s

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
