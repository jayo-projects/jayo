You must comment benchmarks except the one you want to execute in `build.gralde.kts`

## BufferLatin1Benchmark

Benchmark                              (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferLatin1Benchmark.readLatin1Jayo        ascii        20  thrpt    5  10596994.629 ±  252185.472  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii      2000  thrpt    5   4509259.943 ±   41236.292  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii    200000  thrpt    5     27541.146 ±    1690.718  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1        20  thrpt    5  10627415.748 ±   20253.008  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1      2000  thrpt    5   4495136.302 ±   71041.360  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1    200000  thrpt    5     28186.858 ±     900.219  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii        20  thrpt    5  23752821.453 ±   26460.400  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii      2000  thrpt    5   3280696.487 ±   69933.027  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii    200000  thrpt    5     17307.754 ±     619.056  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1        20  thrpt    5  23771580.070 ±  104640.621  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1      2000  thrpt    5   3424376.345 ±   42232.328  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1    200000  thrpt    5     17484.750 ±     666.802  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii        20  thrpt    5  11230038.643 ±    3848.777  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii      2000  thrpt    5  10387308.133 ±   17677.776  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii    200000  thrpt    5    165451.989 ±    7006.619  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1        20  thrpt    5  11189557.902 ±   15291.473  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1      2000  thrpt    5   8469030.898 ±    7207.873  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1    200000  thrpt    5    172265.930 ±     485.327  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii        20  thrpt    5  29445421.968 ± 2277729.876  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii      2000  thrpt    5   6354060.152 ±   54238.363  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii    200000  thrpt    5     38415.559 ±    3145.094  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1        20  thrpt    5  29422605.178 ± 2485199.804  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1      2000  thrpt    5   5658440.196 ±  727360.353  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1    200000  thrpt    5     38399.037 ±    1512.807  ops/s

## BufferUtf8Benchmark

Benchmark                                (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferUtf8Benchmark.readUtf8Jayo              ascii        20  thrpt    5   9628216.177 ±  604552.706  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii      2000  thrpt    5   1553377.283 ±   48695.897  ops/s
BufferUtf8Benchmark.readUtf8Jayo              ascii    200000  thrpt    5     26752.216 ±     455.843  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1        20  thrpt    5   9552421.190 ±  442747.770  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1      2000  thrpt    5    727633.704 ±   18600.636  ops/s
BufferUtf8Benchmark.readUtf8Jayo             latin1    200000  thrpt    5      8788.470 ±     100.286  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8        20  thrpt    5   9088241.441 ± 1090829.102  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8      2000  thrpt    5    441421.565 ±    2753.285  ops/s
BufferUtf8Benchmark.readUtf8Jayo               utf8    200000  thrpt    5      5079.016 ±     289.614  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes        20  thrpt    5   9136041.549 ± 1316118.448  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes      2000  thrpt    5    860939.985 ±    8225.048  ops/s
BufferUtf8Benchmark.readUtf8Jayo             2bytes    200000  thrpt    5      6111.650 ±      75.168  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii        20  thrpt    5  47535507.824 ±  586912.115  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii      2000  thrpt    5   6794648.066 ±  454378.454  ops/s
BufferUtf8Benchmark.readUtf8Okio              ascii    200000  thrpt    5     29162.174 ±     925.027  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1        20  thrpt    5  47464701.930 ±  537044.644  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1      2000  thrpt    5    959001.553 ±    4823.090  ops/s
BufferUtf8Benchmark.readUtf8Okio             latin1    200000  thrpt    5      6229.380 ±      92.821  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8        20  thrpt    5  21772781.187 ±  115627.065  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8      2000  thrpt    5    274799.963 ±    1999.192  ops/s
BufferUtf8Benchmark.readUtf8Okio               utf8    200000  thrpt    5      2645.280 ±     239.074  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes        20  thrpt    5  20539430.205 ±  247182.139  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes      2000  thrpt    5    449259.438 ±    7930.640  ops/s
BufferUtf8Benchmark.readUtf8Okio             2bytes    200000  thrpt    5      3096.050 ±      76.908  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii        20  thrpt    5   9312023.322 ± 1497588.892  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii      2000  thrpt    5   4067493.853 ±  200675.481  ops/s
BufferUtf8Benchmark.readUtf8StringJayo        ascii    200000  thrpt    5     28621.074 ±    1565.743  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1        20  thrpt    5   9274869.197 ± 1388843.738  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1      2000  thrpt    5    996171.518 ±   22074.540  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       latin1    200000  thrpt    5      6266.043 ±      88.702  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8        20  thrpt    5   7632979.147 ±   21661.256  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8      2000  thrpt    5    269986.154 ±    4535.696  ops/s
BufferUtf8Benchmark.readUtf8StringJayo         utf8    200000  thrpt    5      2639.772 ±     119.985  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes        20  thrpt    5   7355571.829 ±   27211.234  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes      2000  thrpt    5    432242.694 ±   11184.728  ops/s
BufferUtf8Benchmark.readUtf8StringJayo       2bytes    200000  thrpt    5      3133.884 ±      38.788  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii        20  thrpt    5  10988671.325 ±   26964.185  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii      2000  thrpt    5  10796534.996 ±   36877.259  ops/s
BufferUtf8Benchmark.writeByteStringJayo       ascii    200000  thrpt    5    898216.014 ±   31351.555  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1        20  thrpt    5  10903255.902 ±  150251.768  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1      2000  thrpt    5  10838544.338 ±   23655.233  ops/s
BufferUtf8Benchmark.writeByteStringJayo      latin1    200000  thrpt    5    831442.366 ±    3369.172  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8        20  thrpt    5  10858553.351 ±  305636.211  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8      2000  thrpt    5  10814098.741 ±   56151.434  ops/s
BufferUtf8Benchmark.writeByteStringJayo        utf8    200000  thrpt    5    407713.028 ±     771.060  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes        20  thrpt    5  10965513.509 ±   38345.017  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes      2000  thrpt    5  10786212.718 ±   48006.231  ops/s
BufferUtf8Benchmark.writeByteStringJayo      2bytes    200000  thrpt    5    406496.522 ±     683.254  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii        20  thrpt    5  10507219.596 ±   50701.878  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii      2000  thrpt    5   1112511.137 ±    5749.297  ops/s
BufferUtf8Benchmark.writeUtf8Jayo             ascii    200000  thrpt    5     10367.030 ±     136.131  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1        20  thrpt    5  10485654.786 ±   64763.699  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1      2000  thrpt    5    832447.853 ±    4126.408  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            latin1    200000  thrpt    5      8148.941 ±       8.535  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8        20  thrpt    5   8438696.707 ±  202325.688  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8      2000  thrpt    5    235048.707 ±    5231.820  ops/s
BufferUtf8Benchmark.writeUtf8Jayo              utf8    200000  thrpt    5      2541.138 ±       9.390  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes        20  thrpt    5   9913264.043 ±  135237.407  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes      2000  thrpt    5    458086.307 ±    2873.127  ops/s
BufferUtf8Benchmark.writeUtf8Jayo            2bytes    200000  thrpt    5      4967.743 ±      30.536  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii        20  thrpt    5  27246535.500 ±   29886.062  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii      2000  thrpt    5   1918159.585 ±    2152.556  ops/s
BufferUtf8Benchmark.writeUtf8Okio             ascii    200000  thrpt    5     16482.056 ±     330.467  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1        20  thrpt    5  27222890.479 ±   50027.142  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1      2000  thrpt    5   1187874.112 ±    2335.475  ops/s
BufferUtf8Benchmark.writeUtf8Okio            latin1    200000  thrpt    5      8912.100 ±     302.266  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8        20  thrpt    5  16583610.999 ±  117246.568  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8      2000  thrpt    5    222530.808 ±     478.282  ops/s
BufferUtf8Benchmark.writeUtf8Okio              utf8    200000  thrpt    5      2147.915 ±       6.529  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes        20  thrpt    5   8234447.638 ± 1164052.272  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes      2000  thrpt    5    245066.176 ±     776.571  ops/s
BufferUtf8Benchmark.writeUtf8Okio            2bytes    200000  thrpt    5      1899.700 ±       8.850  ops/s

## JsonSerializationBenchmark

Benchmark                                         Mode  Cnt         Score        Error  Units
JsonSerializationBenchmark.jacksonFromStream     thrpt    5    703001.748 ±   8724.547  ops/s
JsonSerializationBenchmark.jacksonSmallToStream  thrpt    5  10885356.483 ± 203555.413  ops/s
JsonSerializationBenchmark.jacksonToStream       thrpt    5   1728686.217 ±  62829.206  ops/s
JsonSerializationBenchmark.kotlinxFromJayo       thrpt    5     89593.476 ±    237.192  ops/s
JsonSerializationBenchmark.kotlinxFromOkio       thrpt    5    258371.906 ±   1772.156  ops/s
JsonSerializationBenchmark.kotlinxFromStream     thrpt    5    896375.090 ±   4349.418  ops/s
JsonSerializationBenchmark.kotlinxSmallToJayo    thrpt    5   1554916.556 ±   4013.728  ops/s
JsonSerializationBenchmark.kotlinxSmallToOkio    thrpt    5   7822189.457 ±  77974.138  ops/s
JsonSerializationBenchmark.kotlinxSmallToStream  thrpt    5   7777374.173 ±  10925.542  ops/s
JsonSerializationBenchmark.kotlinxToJayo         thrpt    5    227003.201 ±   1035.631  ops/s
JsonSerializationBenchmark.kotlinxToOkio         thrpt    5    850441.159 ±   4632.472  ops/s
JsonSerializationBenchmark.kotlinxToStream       thrpt    5    931451.947 ±   8324.108  ops/s

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
SocketReaderBenchmark.readerJayo   jayo-io  thrpt    5  39679.173 ± 6577.373  ops/s
SocketReaderBenchmark.readerJayo  jayo-nio  thrpt    5  40115.457 ± 4511.806  ops/s
SocketReaderBenchmark.readerOkio      okio  thrpt    5  38171.214 ± 4086.878  ops/s

## TcpAndJsonSerializationBenchmark

Benchmark                                     Mode  Cnt      Score      Error  Units
TcpAndJsonSerializationBenchmark.readerJayo  thrpt    5  18453.195 ± 1109.536  ops/s
TcpAndJsonSerializationBenchmark.readerOkio  thrpt    5  20698.793 ± 1559.715  ops/s
TcpAndJsonSerializationBenchmark.senderJayo  thrpt    5   1886.880 ±   84.593  ops/s
TcpAndJsonSerializationBenchmark.senderOkio  thrpt    5   1892.732 ±  204.239  ops/s
