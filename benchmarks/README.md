You must comment benchmarks except the one you want to execute in `build.gralde.kts`

## BufferLatin1Benchmark

Benchmark                              (encoding)  (length)   Mode  Cnt         Score        Error  Units
BufferLatin1Benchmark.readLatin1Jayo        ascii        20  thrpt    5  22383905.216 ±  59880.828  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii      2000  thrpt    5   5924583.762 ±  90672.437  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii    200000  thrpt    5     25175.504 ±    833.622  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1        20  thrpt    5  22407795.647 ± 103034.549  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1      2000  thrpt    5   5822850.259 ± 152438.669  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1    200000  thrpt    5     24588.130 ±    903.133  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii        20  thrpt    5  24858660.313 ±  91611.166  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii      2000  thrpt    5   3321196.644 ± 120756.924  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii    200000  thrpt    5     17390.484 ±   1512.924  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1        20  thrpt    5  24845905.660 ± 265955.215  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1      2000  thrpt    5   3315869.725 ±  51114.833  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1    200000  thrpt    5     17778.715 ±   1276.154  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii        20  thrpt    5  25474708.817 ±   6316.688  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii      2000  thrpt    5  16085863.727 ±  16028.949  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii    200000  thrpt    5    103973.701 ±   2353.004  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1        20  thrpt    5  25125875.932 ±  13016.110  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1      2000  thrpt    5  15515121.206 ±  29500.954  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1    200000  thrpt    5    105756.476 ±   1063.647  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii        20  thrpt    5  28102420.615 ± 139374.831  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii      2000  thrpt    5   6113950.439 ±  50269.677  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii    200000  thrpt    5     37161.343 ±   1397.960  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1        20  thrpt    5  29500039.501 ± 232227.025  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1      2000  thrpt    5   6073858.674 ± 136290.859  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1    200000  thrpt    5     38484.116 ±    810.186  ops/s

## BufferUtf8Benchmark

Benchmark                                (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferUtf8Benchmark.readUtf8Jayo              ascii        20  thrpt    5  29241358.784 ±  127421.805  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii      2000  thrpt    5   6128364.676 ±  348914.951  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii    200000  thrpt    5     29375.957 ±    1688.710  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1        20  thrpt    5  27302999.278 ±  503490.118  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1      2000  thrpt    5   1141580.175 ±  107069.212  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1    200000  thrpt    5      6269.397 ±     125.404  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8        20  thrpt    5  16768780.075 ±  103209.272  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8      2000  thrpt    5    273539.345 ±    4685.648  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8    200000  thrpt    5      2657.338 ±      21.129  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes        20  thrpt    5  15697441.666 ±   85908.690  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes      2000  thrpt    5    452365.461 ±   15959.288  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes    200000  thrpt    5      3125.209 ±      18.110  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii        20  thrpt    5  45279858.889 ± 1530466.345  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii      2000  thrpt    5   6898214.687 ±  244290.592  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii    200000  thrpt    5     29393.452 ±     774.835  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1        20  thrpt    5  47167633.750 ± 6201742.627  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1      2000  thrpt    5   1109355.476 ±   11541.527  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1    200000  thrpt    5      6294.309 ±     114.187  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8        20  thrpt    5  21214226.878 ±  236743.333  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8      2000  thrpt    5    273704.169 ±   10830.074  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8    200000  thrpt    5      2647.071 ±      36.564  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes        20  thrpt    5  20157044.686 ±  656083.937  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes      2000  thrpt    5    449726.747 ±    8244.623  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes    200000  thrpt    5      3153.389 ±      59.665  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii        20  thrpt    5  26460100.637 ±  610824.527  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii      2000  thrpt    5   2154524.636 ±   24895.765  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii    200000  thrpt    5     25861.401 ±    3171.331  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1        20  thrpt    5  26964808.614 ±  477638.640  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1      2000  thrpt    5    840591.928 ±    7752.036  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1    200000  thrpt    5      8561.286 ±     698.725  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8        20  thrpt    5  23126542.548 ±  507087.485  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8      2000  thrpt    5    510299.229 ±    9592.697  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8    200000  thrpt    5      5618.969 ±     352.940  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes        20  thrpt    5  24129184.104 ±  197001.403  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes      2000  thrpt    5    954660.973 ±   24739.352  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes    200000  thrpt    5      6126.480 ±     123.325  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii        20  thrpt    5  22245141.540 ±  146349.549  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii      2000  thrpt    5   1742281.392 ±    6137.687  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii    200000  thrpt    5     17774.708 ±     304.997  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1        20  thrpt    5  22131086.202 ±    6765.846  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1      2000  thrpt    5   1038287.081 ±    6249.074  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1    200000  thrpt    5      8001.772 ±     181.738  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8        20  thrpt    5  13404657.310 ±   95440.195  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8      2000  thrpt    5    195198.820 ±    3861.046  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8    200000  thrpt    5      1755.671 ±     128.958  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes        20  thrpt    5   9430844.847 ±   96820.270  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes      2000  thrpt    5    204249.765 ±    1190.281  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes    200000  thrpt    5      1919.814 ±       8.074  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii        20  thrpt    5  27665081.486 ±   43509.293  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii      2000  thrpt    5   1916617.022 ±   18630.237  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii    200000  thrpt    5     16581.034 ±     224.175  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1        20  thrpt    5  28490885.906 ±   16779.402  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1      2000  thrpt    5   1190700.062 ±    1222.600  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1    200000  thrpt    5      8846.811 ±     114.027  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8        20  thrpt    5  16906759.323 ±   98620.409  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8      2000  thrpt    5    222213.608 ±     747.244  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8    200000  thrpt    5      2021.739 ±       7.924  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes        20  thrpt    5  11329991.682 ±  182260.984  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes      2000  thrpt    5    232209.884 ±     537.623  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes    200000  thrpt    5      1906.549 ±       4.826  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo       ascii        20  thrpt    5  36736254.292 ±   94620.908  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo       ascii      2000  thrpt    5  35512326.056 ±   61729.770  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo       ascii    200000  thrpt    5   1574414.795 ±    4470.725  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo      latin1        20  thrpt    5  36704287.616 ±  225926.320  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo      latin1      2000  thrpt    5  35543235.163 ±  169767.436  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo      latin1    200000  thrpt    5   1579234.632 ±   11606.929  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo        utf8        20  thrpt    5  36740228.213 ±  107608.434  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo        utf8      2000  thrpt    5  35472714.295 ±  117077.032  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo        utf8    200000  thrpt    5    866604.444 ±     949.888  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo      2bytes        20  thrpt    5  36757846.033 ±  179854.593  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo      2bytes      2000  thrpt    5  35454201.522 ±  166416.678  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo      2bytes    200000  thrpt    5    847264.503 ±    7826.389  ops/s

## JsonSerializationBenchmark

Benchmark                                         Mode  Cnt        Score        Error  Units
JsonSerializationBenchmark.jacksonFromStream     thrpt    5   654485.280 ±  19944.221  ops/s
JsonSerializationBenchmark.jacksonSmallToStream  thrpt    5  9803817.009 ± 348137.928  ops/s
JsonSerializationBenchmark.jacksonToStream       thrpt    5  1644423.883 ± 192702.985  ops/s
JsonSerializationBenchmark.kotlinxFromJayo       thrpt    5   197081.321 ±  11741.856  ops/s
JsonSerializationBenchmark.kotlinxFromOkio       thrpt    5   203192.807 ±   9632.799  ops/s
JsonSerializationBenchmark.kotlinxFromStream     thrpt    5   783199.409 ±  69482.735  ops/s
JsonSerializationBenchmark.kotlinxSmallToJayo    thrpt    5  4910215.322 ± 289819.371  ops/s
JsonSerializationBenchmark.kotlinxSmallToOkio    thrpt    5  7184581.581 ± 331309.372  ops/s
JsonSerializationBenchmark.kotlinxSmallToStream  thrpt    5  7555626.122 ± 344436.745  ops/s
JsonSerializationBenchmark.kotlinxToJayo         thrpt    5   579531.229 ±  36016.540  ops/s
JsonSerializationBenchmark.kotlinxToOkio         thrpt    5   846498.145 ±  66272.769  ops/s
JsonSerializationBenchmark.kotlinxToStream       thrpt    5   928452.983 ±  39533.901  ops/s

## SlowSinkBenchmark

Benchmark                   (type)   Mode  Cnt  Score   Error  Units
SlowSinkBenchmark.sinkJayo    jayo  thrpt    5  1.439 ± 0.101  ops/s
SlowSinkBenchmark.sinkOkio    okio  thrpt    5  0.738 ± 0.033  ops/s

## SlowSourceBenchmark

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowSourceBenchmark.sourceJayo    jayo  thrpt    5  1.446 ± 0.165  ops/s
SlowSourceBenchmark.sourceOkio    okio  thrpt    5  0.738 ± 0.028  ops/s

## SocketSourceBenchmark

Benchmark                         (type)   Mode  Cnt      Score      Error  Units
SocketSourceBenchmark.readerJayo    jayo  thrpt    5  36918.609 ± 2367.091  ops/s
SocketSourceBenchmark.readerOkio    okio  thrpt    5  45338.049 ± 8744.595  ops/s

## TcpAndJsonSerializationBenchmark

Benchmark                                            Mode  Cnt      Score     Error  Units
TcpAndJsonSerializationBenchmark.readerJayo         thrpt    5  15225.560 ±   219.601  ops/s
TcpAndJsonSerializationBenchmark.readerOkio         thrpt    5  22575.264 ±   694.424  ops/s
TcpAndJsonSerializationBenchmark.senderJayo         thrpt    5    325.355 ±    94.082  ops/s
TcpAndJsonSerializationBenchmark.senderJayoJackson  thrpt    5    253.068 ±   326.333  ops/s
TcpAndJsonSerializationBenchmark.senderOkio         thrpt    5    336.756 ±    97.709  ops/s

## TcpBenchmark

Benchmark                 Mode  Cnt      Score       Error  Units
TcpBenchmark.readerJayo  thrpt    5  16873.752 ±   213.157  ops/s
TcpBenchmark.readerOkio  thrpt    5  11828.301 ± 15012.666  ops/s
TcpBenchmark.senderJayo  thrpt    5  15096.785 ± 11798.291  ops/s
TcpBenchmark.senderOkio  thrpt    5  28668.443 ±   710.793  ops/s
