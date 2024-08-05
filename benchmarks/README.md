You must comment benchmarks except the one you want to execute in `build.gralde.kts`

## BufferLatin1Benchmark

Benchmark                              (encoding)  (length)   Mode  Cnt         Score        Error  Units
BufferLatin1Benchmark.readLatin1Jayo        ascii        20  thrpt    5  10134180.153 ± 194358.184  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii      2000  thrpt    5   4803603.483 ±  70043.632  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii    200000  thrpt    5     27050.212 ±   1940.333  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1        20  thrpt    5  10278327.741 ±  46215.238  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1      2000  thrpt    5   4965468.940 ±  65515.169  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1    200000  thrpt    5     27308.737 ±    764.688  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii        20  thrpt    5  25269480.806 ±  68242.978  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii      2000  thrpt    5   3319754.213 ± 120715.911  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii    200000  thrpt    5     17720.703 ±   1416.678  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1        20  thrpt    5  25102891.757 ± 126298.830  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1      2000  thrpt    5   3263944.321 ± 152132.269  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1    200000  thrpt    5     18080.041 ±   1482.158  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii        20  thrpt    5  11223055.041 ± 213283.427  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii      2000  thrpt    5   8381798.576 ±   6399.798  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii    200000  thrpt    5    139003.799 ±   7478.936  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1        20  thrpt    5  11221843.730 ±  16311.500  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1      2000  thrpt    5   9873246.457 ±  12827.104  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1    200000  thrpt    5    141564.128 ±  11682.676  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii        20  thrpt    5  28014831.456 ± 572837.636  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii      2000  thrpt    5   6114996.779 ± 216807.327  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii    200000  thrpt    5     37988.969 ±   2407.964  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1        20  thrpt    5  28328216.749 ± 745246.019  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1      2000  thrpt    5   5647105.442 ±  44681.174  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1    200000  thrpt    5     37355.720 ±    556.051  ops/s

## BufferUtf8Benchmark

Benchmark                                (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferUtf8Benchmark.readUtf8Jayo              ascii        20  thrpt    5   9644758.922 ±   73104.743  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii      2000  thrpt    5   1567872.567 ±  101178.119  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii    200000  thrpt    5     24997.493 ±    2673.234  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1        20  thrpt    5   9567963.816 ±  209983.462  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1      2000  thrpt    5    764501.981 ±   15137.138  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1    200000  thrpt    5      8333.618 ±     210.542  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8        20  thrpt    5   9181372.010 ±  113919.562  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8      2000  thrpt    5    466862.926 ±    4127.981  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8    200000  thrpt    5      5282.200 ±     161.447  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes        20  thrpt    5   9155047.551 ± 1184696.820  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes      2000  thrpt    5    880622.753 ±   25862.574  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes    200000  thrpt    5      5873.647 ±      71.945  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii        20  thrpt    5  47444720.497 ±  535443.371  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii      2000  thrpt    5   6928910.852 ±  413115.428  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii    200000  thrpt    5     29057.815 ±    1041.827  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1        20  thrpt    5  45626839.745 ±  352157.607  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1      2000  thrpt    5    953655.663 ±   10933.355  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1    200000  thrpt    5      6256.401 ±     232.156  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8        20  thrpt    5  21669878.343 ±  199497.306  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8      2000  thrpt    5    274756.028 ±    1687.693  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8    200000  thrpt    5      2656.219 ±      16.079  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes        20  thrpt    5  20536525.255 ±  100368.612  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes      2000  thrpt    5    445571.121 ±   13497.854  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes    200000  thrpt    5      3149.165 ±      31.714  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii        20  thrpt    5   9314211.021 ± 1354300.136  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii      2000  thrpt    5   4218213.434 ±  147753.349  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii    200000  thrpt    5     27365.038 ±    1294.185  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1        20  thrpt    5   9345724.750 ± 1312268.486  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1      2000  thrpt    5    986923.686 ±   32607.107  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1    200000  thrpt    5      6218.410 ±      60.159  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8        20  thrpt    5   7580149.856 ±  102118.667  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8      2000  thrpt    5    272503.531 ±    2842.756  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8    200000  thrpt    5      2622.681 ±      38.828  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes        20  thrpt    5   7328781.574 ±   18095.294  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes      2000  thrpt    5    440493.492 ±    8885.026  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes    200000  thrpt    5      3073.679 ±      65.897  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii        20  thrpt    5  10852485.465 ±   46231.768  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii      2000  thrpt    5  10739931.706 ±   39173.978  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii    200000  thrpt    5    424703.744 ±    1945.735  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1        20  thrpt    5  10885958.795 ±   96312.242  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1      2000  thrpt    5  10657186.996 ±  134494.521  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1    200000  thrpt    5    425207.871 ±    1145.481  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8        20  thrpt    5  10880152.768 ±   47134.889  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8      2000  thrpt    5  10674417.703 ±   35443.923  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8    200000  thrpt    5    203046.984 ±     583.692  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes        20  thrpt    5  10856392.621 ±   88857.461  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes      2000  thrpt    5  10724730.453 ±  185370.527  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes    200000  thrpt    5    199021.263 ±     494.467  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii        20  thrpt    5  10372997.582 ±   26025.698  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii      2000  thrpt    5   1110233.449 ±   10560.530  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii    200000  thrpt    5     10221.343 ±     173.885  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1        20  thrpt    5  10183444.210 ±   55513.376  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1      2000  thrpt    5    832098.111 ±    3920.777  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1    200000  thrpt    5      7896.776 ±      81.075  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8        20  thrpt    5   8381533.309 ±  496588.044  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8      2000  thrpt    5    235590.683 ±    5181.269  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8    200000  thrpt    5      2507.906 ±      89.642  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes        20  thrpt    5   9786751.503 ±  155984.082  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes      2000  thrpt    5    456771.373 ±    2126.096  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes    200000  thrpt    5      4814.494 ±     194.520  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii        20  thrpt    5  28618348.838 ±   43010.528  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii      2000  thrpt    5   1918594.124 ±    4737.942  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii    200000  thrpt    5     16452.484 ±     174.958  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1        20  thrpt    5  28734801.499 ±   11986.833  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1      2000  thrpt    5   1190004.893 ±    2962.725  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1    200000  thrpt    5      8948.139 ±     134.446  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8        20  thrpt    5  17148104.332 ±  102753.090  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8      2000  thrpt    5    222480.378 ±     463.798  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8    200000  thrpt    5      2018.149 ±       8.295  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes        20  thrpt    5  11472809.132 ±  113242.835  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes      2000  thrpt    5    242671.943 ±     197.201  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes    200000  thrpt    5      1893.883 ±       6.556  ops/s

## JsonSerializationBenchmark

Benchmark                                         Mode  Cnt         Score        Error  Units
JsonSerializationBenchmark.jacksonFromStream     thrpt    5    697648.848 ±  19349.669  ops/s
JsonSerializationBenchmark.jacksonSmallToStream  thrpt    5  10910232.840 ± 189201.898  ops/s
JsonSerializationBenchmark.jacksonToStream       thrpt    5   1757848.710 ±  14722.368  ops/s
JsonSerializationBenchmark.kotlinxFromJayo       thrpt    5     90422.172 ±    273.576  ops/s
JsonSerializationBenchmark.kotlinxFromOkio       thrpt    5    289669.678 ±   1142.667  ops/s
JsonSerializationBenchmark.kotlinxFromStream     thrpt    5    865444.281 ±   2138.751  ops/s
JsonSerializationBenchmark.kotlinxSmallToJayo    thrpt    5   1592952.477 ±   6990.280  ops/s
JsonSerializationBenchmark.kotlinxSmallToOkio    thrpt    5   7599551.105 ±  55740.983  ops/s
JsonSerializationBenchmark.kotlinxSmallToStream  thrpt    5   7647579.141 ±  40630.732  ops/s
JsonSerializationBenchmark.kotlinxToJayo         thrpt    5    233400.488 ±    795.981  ops/s
JsonSerializationBenchmark.kotlinxToOkio         thrpt    5    879225.956 ±   8266.453  ops/s
JsonSerializationBenchmark.kotlinxToStream       thrpt    5    914578.656 ±   5719.205  ops/s

## SlowReaderBenchmark

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowReaderBenchmark.readerJayo    jayo  thrpt    5  1.436 ± 0.120  ops/s
SlowReaderBenchmark.sourceOkio    okio  thrpt    5  0.715 ± 0.044  ops/s

## SlowWriterBenchmark

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowWriterBenchmark.sinkOkio      okio  thrpt    5  0.716 ± 0.045  ops/s
SlowWriterBenchmark.writerJayo    jayo  thrpt    5  1.445 ± 0.088  ops/s

## SocketReaderBenchmark

Benchmark                         (type)   Mode  Cnt      Score      Error  Units
SocketReaderBenchmark.readerJayo    jayo  thrpt    5  39777.235 ± 1817.616  ops/s
SocketReaderBenchmark.readerOkio    okio  thrpt    5  39867.936 ± 3544.144  ops/s

## TcpAndJsonSerializationBenchmark

Benchmark                                     Mode  Cnt      Score      Error  Units
TcpAndJsonSerializationBenchmark.readerJayo  thrpt    5  17650.097 ± 2345.999  ops/s
TcpAndJsonSerializationBenchmark.readerOkio  thrpt    5  21070.105 ± 2353.939  ops/s
TcpAndJsonSerializationBenchmark.senderJayo  thrpt    5   1877.874 ±   76.283  ops/s
TcpAndJsonSerializationBenchmark.senderOkio  thrpt    5   1892.128 ±  199.102  ops/s
