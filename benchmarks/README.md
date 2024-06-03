You must comment benchmarks except the one you want to execute in `build.gralde.kts`

## BufferLatin1Benchmark

Benchmark                              (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferLatin1Benchmark.readLatin1Jayo        ascii        20  thrpt    5  11448268.972 ±  119142.092  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii      2000  thrpt    5   4925067.615 ±  138641.162  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii    200000  thrpt    5     27648.084 ±    1226.017  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1        20  thrpt    5  11400153.915 ±   87496.498  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1      2000  thrpt    5   4913847.306 ±  252140.628  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1    200000  thrpt    5     27169.091 ±    1279.085  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii        20  thrpt    5  24912879.698 ±  100345.521  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii      2000  thrpt    5   3250674.992 ±   16461.673  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii    200000  thrpt    5     17600.692 ±     660.549  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1        20  thrpt    5  25310543.815 ±  372489.838  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1      2000  thrpt    5   3227599.067 ±   23857.477  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1    200000  thrpt    5     17414.299 ±    1070.370  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii        20  thrpt    5  12116459.779 ±   12273.354  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii      2000  thrpt    5   9450969.237 ±  137083.827  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii    200000  thrpt    5    142275.019 ±    8242.716  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1        20  thrpt    5  12152537.135 ±  305197.285  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1      2000  thrpt    5  10696149.155 ±  115228.966  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1    200000  thrpt    5    144056.388 ±    7690.589  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii        20  thrpt    5  28453493.931 ± 2838877.333  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii      2000  thrpt    5   5773877.871 ±  325109.718  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii    200000  thrpt    5     37928.763 ±     969.686  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1        20  thrpt    5  28215912.536 ±  933287.370  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1      2000  thrpt    5   5951312.042 ±   58006.566  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1    200000  thrpt    5     38092.819 ±     818.043  ops/s

## BufferUtf8Benchmark

Benchmark                                (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferUtf8Benchmark.readUtf8Jayo              ascii        20  thrpt    5  12226409.425 ±   47045.915  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii      2000  thrpt    5   4535115.881 ±  548454.257  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii    200000  thrpt    5     28453.971 ±     291.496  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1        20  thrpt    5  12260084.594 ±  130257.420  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1      2000  thrpt    5    913443.084 ±   72082.456  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1    200000  thrpt    5      9082.326 ±    1276.951  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8        20  thrpt    5   9773661.881 ±   32190.265  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8      2000  thrpt    5    269925.147 ±    2476.228  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8    200000  thrpt    5      2609.550 ±      86.992  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes        20  thrpt    5   9547348.682 ±   23956.596  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes      2000  thrpt    5    435557.406 ±    2139.605  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes    200000  thrpt    5      3093.201 ±      84.101  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii        20  thrpt    5  47075847.650 ±  481353.293  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii      2000  thrpt    5   6633706.500 ±  852041.397  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii    200000  thrpt    5     29298.815 ±    1738.327  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1        20  thrpt    5  45623271.160 ± 1601333.657  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1      2000  thrpt    5    942848.102 ±   40236.602  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1    200000  thrpt    5      9894.043 ±     238.457  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8        20  thrpt    5  21754968.360 ±  126498.421  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8      2000  thrpt    5    276524.177 ±    4411.379  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8    200000  thrpt    5      2556.460 ±      82.705  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes        20  thrpt    5  20544521.626 ±  528794.731  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes      2000  thrpt    5    452754.207 ±    3064.777  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes    200000  thrpt    5      3119.805 ±     123.429  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii        20  thrpt    5  11719992.363 ±  244580.464  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii      2000  thrpt    5   1535727.676 ±   56495.401  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii    200000  thrpt    5     24785.354 ±    2816.275  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1        20  thrpt    5  11420257.978 ±  501831.784  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1      2000  thrpt    5    741546.181 ±   22488.046  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1    200000  thrpt    5      8558.863 ±     343.977  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8        20  thrpt    5  11001379.902 ±   80814.956  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8      2000  thrpt    5    447924.930 ±    3991.485  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8    200000  thrpt    5      5217.242 ±     148.907  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes        20  thrpt    5  11189664.220 ±   55708.876  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes      2000  thrpt    5    851333.521 ±   15458.203  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes    200000  thrpt    5      5850.211 ±     378.910  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii        20  thrpt    5  10588948.538 ±   12059.075  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii      2000  thrpt    5   1668200.863 ±    5620.438  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii    200000  thrpt    5     16370.315 ±     921.270  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1        20  thrpt    5  10670414.975 ±    6467.318  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1      2000  thrpt    5   1170405.593 ±    2826.234  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1    200000  thrpt    5     11123.502 ±    1834.414  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8        20  thrpt    5   8947569.422 ±    8795.868  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8      2000  thrpt    5    276526.742 ±    1055.118  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8    200000  thrpt    5      2309.665 ±     149.903  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes        20  thrpt    5   9927496.250 ±   10467.319  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes      2000  thrpt    5    716005.822 ±    1408.254  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes    200000  thrpt    5      2610.625 ±     204.683  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii        20  thrpt    5  28500172.915 ±   34885.043  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii      2000  thrpt    5   1902810.514 ±   66786.548  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii    200000  thrpt    5     16596.052 ±     235.245  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1        20  thrpt    5  28867970.893 ±  210196.416  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1      2000  thrpt    5   1189106.605 ±     681.500  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1    200000  thrpt    5      8923.567 ±     169.722  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8        20  thrpt    5  16736060.647 ±   84880.579  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8      2000  thrpt    5    221858.555 ±     940.085  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8    200000  thrpt    5      1996.944 ±     203.517  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes        20  thrpt    5  10207341.965 ±  183855.690  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes      2000  thrpt    5    241182.061 ±    1738.136  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes    200000  thrpt    5      1825.584 ±     491.125  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo       ascii        20  thrpt    5  13703094.201 ± 2942946.108  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo       ascii      2000  thrpt    5  13113871.099 ± 1740755.734  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo       ascii    200000  thrpt    5    449545.347 ±   23088.467  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo      latin1        20  thrpt    5  13649575.596 ± 2207116.286  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo      latin1      2000  thrpt    5  13314009.525 ± 1635965.009  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo      latin1    200000  thrpt    5    455738.007 ±   21122.773  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo        utf8        20  thrpt    5  13738135.073 ± 2921567.894  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo        utf8      2000  thrpt    5  13215146.888 ± 1675484.720  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo        utf8    200000  thrpt    5    238333.430 ±     591.296  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo      2bytes        20  thrpt    5  13600211.653 ± 3160821.680  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo      2bytes      2000  thrpt    5  13250578.398 ± 1509091.097  ops/s
BufferUtf8Benchmark.writeUtf8StringJayo      2bytes    200000  thrpt    5    230882.684 ±    6225.897  ops/s

## JsonSerializationBenchmark

Benchmark                                         Mode  Cnt         Score        Error  Units
JsonSerializationBenchmark.jacksonFromStream     thrpt    5    704965.574 ±  13563.994  ops/s
JsonSerializationBenchmark.jacksonSmallToStream  thrpt    5  10849402.436 ± 171655.119  ops/s
JsonSerializationBenchmark.jacksonToStream       thrpt    5   1742314.044 ±  11060.453  ops/s
JsonSerializationBenchmark.kotlinxFromJayo       thrpt    5     54583.186 ±    239.523  ops/s
JsonSerializationBenchmark.kotlinxFromOkio       thrpt    5    283561.381 ±   1324.797  ops/s
JsonSerializationBenchmark.kotlinxFromStream     thrpt    5    886080.056 ±   5449.562  ops/s
JsonSerializationBenchmark.kotlinxSmallToJayo    thrpt    5   1684896.160 ±   5120.958  ops/s
JsonSerializationBenchmark.kotlinxSmallToOkio    thrpt    5   7842201.486 ±  16188.654  ops/s
JsonSerializationBenchmark.kotlinxSmallToStream  thrpt    5   7735467.797 ±   4432.959  ops/s
JsonSerializationBenchmark.kotlinxToJayo         thrpt    5    244815.311 ±    416.029  ops/s
JsonSerializationBenchmark.kotlinxToOkio         thrpt    5    795187.332 ±   9733.731  ops/s
JsonSerializationBenchmark.kotlinxToStream       thrpt    5    905789.572 ±   5587.860  ops/s

## SlowSinkBenchmark

Benchmark                   (type)   Mode  Cnt  Score   Error  Units
SlowSinkBenchmark.sinkJayo    jayo  thrpt    5  0.713 ± 0.054  ops/s
SlowSinkBenchmark.sinkOkio    okio  thrpt    5  0.724 ± 0.049  ops/s

## SlowSourceBenchmark

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowSourceBenchmark.sourceJayo    jayo  thrpt    5  0.715 ± 0.070  ops/s
SlowSourceBenchmark.sourceOkio    okio  thrpt    5  0.723 ± 0.033  ops/s

## SocketSourceBenchmark

Benchmark                         (type)   Mode  Cnt      Score      Error  Units
SocketSourceBenchmark.readerJayo    jayo  thrpt    5  39887.722 ± 5395.977  ops/s
SocketSourceBenchmark.readerOkio    okio  thrpt    5  40876.257 ± 3928.426  ops/s

## TcpAndJsonSerializationBenchmark

Benchmark                                     Mode  Cnt      Score      Error  Units
TcpAndJsonSerializationBenchmark.readerJayo  thrpt    5  15251.100 ± 2851.148  ops/s
TcpAndJsonSerializationBenchmark.readerOkio  thrpt    5  21284.528 ± 2327.955  ops/s
TcpAndJsonSerializationBenchmark.senderJayo  thrpt    5   1914.824 ±   88.042  ops/s
TcpAndJsonSerializationBenchmark.senderOkio  thrpt    5   1866.909 ±  207.317  ops/s

## TcpBenchmark

Benchmark                 Mode  Cnt      Score       Error  Units
TcpBenchmark.readerJayo  thrpt    5  16873.752 ±   213.157  ops/s
TcpBenchmark.readerOkio  thrpt    5  11828.301 ± 15012.666  ops/s
TcpBenchmark.senderJayo  thrpt    5  15096.785 ± 11798.291  ops/s
TcpBenchmark.senderOkio  thrpt    5  28668.443 ±   710.793  ops/s
