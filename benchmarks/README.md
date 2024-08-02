You must comment benchmarks except the one you want to execute in `build.gralde.kts`

## BufferLatin1Benchmark

Benchmark                              (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferLatin1Benchmark.readLatin1Jayo        ascii        20  thrpt    5   9833411.033 ±   37741.242  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii      2000  thrpt    5   4418743.130 ±   18210.869  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii    200000  thrpt    5     27701.675 ±     565.514  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1        20  thrpt    5   9736261.327 ±   67796.027  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1      2000  thrpt    5   4249456.487 ±   56161.870  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1    200000  thrpt    5     27117.962 ±     512.828  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii        20  thrpt    5  25045382.088 ±   88896.814  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii      2000  thrpt    5   3319931.306 ±  188883.109  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii    200000  thrpt    5     18226.151 ±     619.291  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1        20  thrpt    5  23767693.188 ±  122200.884  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1      2000  thrpt    5   3267268.857 ±  163274.740  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1    200000  thrpt    5     18140.204 ±     211.358  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii        20  thrpt    5  10727269.941 ±   19689.047  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii      2000  thrpt    5   9915585.817 ±    5028.235  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii    200000  thrpt    5    140412.966 ±    7731.442  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1        20  thrpt    5  10713089.784 ±   16175.805  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1      2000  thrpt    5   9921557.023 ±   10176.875  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1    200000  thrpt    5    137414.312 ±    6158.663  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii        20  thrpt    5  28965085.923 ± 2176489.606  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii      2000  thrpt    5   5669161.244 ±  427815.939  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii    200000  thrpt    5     38540.486 ±    1166.838  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1        20  thrpt    5  27996767.524 ±  728641.521  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1      2000  thrpt    5   5535420.457 ±  692663.882  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1    200000  thrpt    5     38594.038 ±    2439.195  ops/s

## BufferUtf8Benchmark

Benchmark                                (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferUtf8Benchmark.readUtf8Jayo              ascii        20  thrpt    5  11211015.343 ±  563228.301  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii      2000  thrpt    5   1802765.883 ±   43334.017  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1        20  thrpt    5  11310241.087 ±  210510.214  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1      2000  thrpt    5    737721.586 ±   20231.534  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8        20  thrpt    5  10622596.964 ±  857764.511  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8      2000  thrpt    5    531900.986 ±   12677.747  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes        20  thrpt    5  10546828.564 ± 1382274.335  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes      2000  thrpt    5    854012.906 ±   24310.636  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii        20  thrpt    5  47912721.853 ±  805845.606  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii      2000  thrpt    5   6991981.405 ±  441350.921  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii    200000  thrpt    5     28951.638 ±     626.073  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1        20  thrpt    5  46062465.494 ±  460936.462  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1      2000  thrpt    5    943091.176 ±    6455.263  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1    200000  thrpt    5      9267.174 ±     875.721  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8        20  thrpt    5  21781554.608 ±   66578.009  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8      2000  thrpt    5    272974.749 ±   14035.841  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8    200000  thrpt    5      2581.558 ±       7.681  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes        20  thrpt    5  20623252.307 ±  263422.452  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes      2000  thrpt    5    452383.005 ±    9095.863  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes    200000  thrpt    5      3154.344 ±       6.985  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii        20  thrpt    5  10929464.475 ± 2099755.868  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii      2000  thrpt    5   4332417.669 ±  611195.823  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii    200000  thrpt    5     27429.642 ±    2570.540  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1        20  thrpt    5  10942226.846 ± 2116990.660  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1      2000  thrpt    5    913219.464 ±   24975.869  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1    200000  thrpt    5      9350.572 ±     123.772  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8        20  thrpt    5   8651110.945 ±   41972.771  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8      2000  thrpt    5    271875.426 ±    1231.523  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8    200000  thrpt    5      2661.004 ±      16.020  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes        20  thrpt    5   8302775.927 ±   27367.469  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes      2000  thrpt    5    432214.041 ±    7917.601  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes    200000  thrpt    5      3111.869 ±      16.909  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii        20  thrpt    5  13391073.852 ±  172144.737  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii      2000  thrpt    5  13118849.494 ±   41803.003  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii    200000  thrpt    5    469485.955 ±     920.479  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1        20  thrpt    5  13410903.831 ±  112027.779  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1      2000  thrpt    5  13085067.782 ±   90698.948  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1    200000  thrpt    5    469364.035 ±    1473.248  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8        20  thrpt    5  13453560.006 ±  108489.271  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8      2000  thrpt    5  13116014.398 ±   32909.243  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8    200000  thrpt    5    234276.152 ±     658.979  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes        20  thrpt    5  13449895.386 ±   64470.237  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes      2000  thrpt    5  13101512.426 ±   51577.889  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes    200000  thrpt    5    236929.400 ±     638.641  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii        20  thrpt    5   9794338.058 ±  371159.926  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii      2000  thrpt    5   1106966.955 ±    7474.623  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii    200000  thrpt    5     10339.017 ±      15.893  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1        20  thrpt    5   9842168.614 ±  116069.611  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1      2000  thrpt    5    828929.662 ±    4189.396  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1    200000  thrpt    5      7949.429 ±      83.147  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8        20  thrpt    5   7805831.172 ±  240487.985  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8      2000  thrpt    5    232946.957 ±    2500.191  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8    200000  thrpt    5      2514.369 ±      30.355  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes        20  thrpt    5   9516646.439 ±  199753.569  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes      2000  thrpt    5    455762.822 ±    2220.358  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes    200000  thrpt    5      4862.958 ±     111.886  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii        20  thrpt    5  28747708.117 ±   23360.321  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii      2000  thrpt    5   1919045.045 ±    3359.707  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii    200000  thrpt    5     16731.363 ±     193.240  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1        20  thrpt    5  28643985.444 ±   20162.832  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1      2000  thrpt    5   1188946.352 ±    3628.643  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1    200000  thrpt    5      8948.337 ±      42.116  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8        20  thrpt    5  16869806.502 ±   66036.824  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8      2000  thrpt    5    222146.757 ±     425.757  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8    200000  thrpt    5      2015.271 ±      13.421  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes        20  thrpt    5  11399684.594 ±  116373.606  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes      2000  thrpt    5    245231.402 ±     921.329  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes    200000  thrpt    5      1892.323 ±       8.045  ops/s

## JsonSerializationBenchmark

Benchmark                                         Mode  Cnt         Score        Error  Units
JsonSerializationBenchmark.jacksonFromStream     thrpt    5    706329.299 ±   6409.159  ops/s
JsonSerializationBenchmark.jacksonSmallToStream  thrpt    5  10940804.350 ± 138304.114  ops/s
JsonSerializationBenchmark.jacksonToStream       thrpt    5   1623720.643 ±  33985.854  ops/s
JsonSerializationBenchmark.kotlinxFromJayo       thrpt    5     91773.940 ±    346.009  ops/s
JsonSerializationBenchmark.kotlinxFromOkio       thrpt    5    292305.054 ±    824.433  ops/s
JsonSerializationBenchmark.kotlinxFromStream     thrpt    5    877751.812 ±   4020.056  ops/s
JsonSerializationBenchmark.kotlinxSmallToJayo    thrpt    5   1613403.366 ±   7268.406  ops/s
JsonSerializationBenchmark.kotlinxSmallToOkio    thrpt    5   8288222.640 ±  35545.170  ops/s
JsonSerializationBenchmark.kotlinxSmallToStream  thrpt    5   7782592.887 ±  73756.899  ops/s
JsonSerializationBenchmark.kotlinxToJayo         thrpt    5    234947.491 ±    707.947  ops/s
JsonSerializationBenchmark.kotlinxToOkio         thrpt    5    902382.650 ±   8282.147  ops/s
JsonSerializationBenchmark.kotlinxToStream       thrpt    5    917100.889 ±   2963.783  ops/s

## SlowReaderBenchmark

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowReaderBenchmark.readerJayo    jayo  thrpt    5  1.438 ± 0.106  ops/s
SlowReaderBenchmark.sourceOkio    okio  thrpt    5  0.736 ± 0.035  ops/s

## SlowWriterBenchmark

Benchmark                       (type)   Mode  Cnt  Score   Error  Units
SlowWriterBenchmark.sinkOkio      okio  thrpt    5  0.730 ± 0.069  ops/s
SlowWriterBenchmark.writerJayo    jayo  thrpt    5  1.461 ± 0.089  ops/s

## SocketReaderBenchmark

Benchmark                         (type)   Mode  Cnt      Score      Error  Units
SocketReaderBenchmark.readerJayo    jayo  thrpt    5  39482.379 ± 3386.058  ops/s
SocketReaderBenchmark.readerOkio    okio  thrpt    5  41041.106 ± 2035.309  ops/s

## TcpAndJsonSerializationBenchmark

Benchmark                                     Mode  Cnt      Score      Error  Units
TcpAndJsonSerializationBenchmark.readerJayo  thrpt    5  17421.217 ± 2869.813  ops/s
TcpAndJsonSerializationBenchmark.readerOkio  thrpt    5  21181.138 ± 1557.620  ops/s
TcpAndJsonSerializationBenchmark.senderJayo  thrpt    5   1913.616 ±   75.854  ops/s
TcpAndJsonSerializationBenchmark.senderOkio  thrpt    5   1921.792 ±  138.354  ops/s
