You must comment benchmarks except the one you want to execute in `build.gralde.kts`

## BufferLatin1Benchmark

Benchmark                              (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferLatin1Benchmark.readLatin1Jayo        ascii        20  thrpt    5  19021055.579 ±  179829.722  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii      2000  thrpt    5   4768680.360 ±   54385.019  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii    200000  thrpt    5     23210.220 ±    9014.508  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1        20  thrpt    5  15067170.810 ±  528958.113  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1      2000  thrpt    5   4860494.442 ±  407589.680  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1    200000  thrpt    5     24877.862 ±     471.339  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii        20  thrpt    5  21226344.637 ± 1086131.850  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii      2000  thrpt    5   2690955.391 ±   62711.033  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii    200000  thrpt    5     15145.274 ±     227.211  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1        20  thrpt    5  21139323.657 ± 1448203.566  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1      2000  thrpt    5   2701949.504 ±   82499.767  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1    200000  thrpt    5     15053.025 ±     502.021  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii        20  thrpt    5  31182302.346 ±  222887.744  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii      2000  thrpt    5  20941811.329 ±   58759.472  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii    200000  thrpt    5    108548.859 ±     536.080  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1        20  thrpt    5  31171121.240 ±  328503.036  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1      2000  thrpt    5  21786956.342 ±   86201.481  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1    200000  thrpt    5    108544.415 ±     358.400  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii        20  thrpt    5  24423151.032 ±  188207.362  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii      2000  thrpt    5   5162096.745 ±   93331.507  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii    200000  thrpt    5     32280.676 ±     458.953  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1        20  thrpt    5  23500767.245 ±   57907.137  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1      2000  thrpt    5   4974946.054 ±  163669.669  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1    200000  thrpt    5     32101.329 ±     406.762  ops/s

## BufferUtf8Benchmark

Benchmark                          (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferUtf8Benchmark.readUtf8Jayo        ascii        20  thrpt    5  33948744.487 ± 1066978.033  ops/s
BufferUtf8Benchmark.readUtf8Jayo        ascii      2000  thrpt    5   4678539.069 ± 1126255.340  ops/s
BufferUtf8Benchmark.readUtf8Jayo        ascii    200000  thrpt    5     32470.247 ±    2005.102  ops/s
BufferUtf8Benchmark.readUtf8Jayo         utf8        20  thrpt    5  15675941.072 ±  500807.374  ops/s
BufferUtf8Benchmark.readUtf8Jayo         utf8      2000  thrpt    5    268149.076 ±    4277.028  ops/s
BufferUtf8Benchmark.readUtf8Jayo         utf8    200000  thrpt    5      2729.661 ±     120.210  ops/s
BufferUtf8Benchmark.readUtf8Jayo       sparse        20  thrpt    5  34327300.184 ±  138484.793  ops/s
BufferUtf8Benchmark.readUtf8Jayo       sparse      2000  thrpt    5    939912.111 ±   32461.548  ops/s
BufferUtf8Benchmark.readUtf8Jayo       sparse    200000  thrpt    5      7133.078 ±      44.037  ops/s
BufferUtf8Benchmark.readUtf8Jayo       2bytes        20  thrpt    5  16192255.643 ±  431079.186  ops/s
BufferUtf8Benchmark.readUtf8Jayo       2bytes      2000  thrpt    5    437455.122 ±   10831.007  ops/s
BufferUtf8Benchmark.readUtf8Jayo       2bytes    200000  thrpt    5      3586.642 ±     120.361  ops/s
BufferUtf8Benchmark.readUtf8Okio        ascii        20  thrpt    5  43579457.891 ±  953570.008  ops/s
BufferUtf8Benchmark.readUtf8Okio        ascii      2000  thrpt    5   5364135.397 ±  120979.986  ops/s
BufferUtf8Benchmark.readUtf8Okio        ascii    200000  thrpt    5     32421.961 ±    1477.221  ops/s
BufferUtf8Benchmark.readUtf8Okio         utf8        20  thrpt    5  17700049.925 ±  456136.104  ops/s
BufferUtf8Benchmark.readUtf8Okio         utf8      2000  thrpt    5    269962.043 ±    9766.361  ops/s
BufferUtf8Benchmark.readUtf8Okio         utf8    200000  thrpt    5      2737.498 ±     131.253  ops/s
BufferUtf8Benchmark.readUtf8Okio       sparse        20  thrpt    5  42848592.229 ± 1884270.614  ops/s
BufferUtf8Benchmark.readUtf8Okio       sparse      2000  thrpt    5    956147.277 ±   62186.643  ops/s
BufferUtf8Benchmark.readUtf8Okio       sparse    200000  thrpt    5      7169.869 ±     176.797  ops/s
BufferUtf8Benchmark.readUtf8Okio       2bytes        20  thrpt    5  17713043.947 ±  132282.980  ops/s
BufferUtf8Benchmark.readUtf8Okio       2bytes      2000  thrpt    5    437817.161 ±   11236.217  ops/s
BufferUtf8Benchmark.readUtf8Okio       2bytes    200000  thrpt    5      3582.286 ±      48.411  ops/s
BufferUtf8Benchmark.writeUtf8Jayo       ascii        20  thrpt    5  27120487.374 ±  171423.004  ops/s
BufferUtf8Benchmark.writeUtf8Jayo       ascii      2000  thrpt    5   1888930.438 ±   10179.150  ops/s
BufferUtf8Benchmark.writeUtf8Jayo       ascii    200000  thrpt    5     17420.826 ±     181.112  ops/s
BufferUtf8Benchmark.writeUtf8Jayo        utf8        20  thrpt    5  13468260.051 ± 1021782.420  ops/s
BufferUtf8Benchmark.writeUtf8Jayo        utf8      2000  thrpt    5    169868.469 ±    1924.827  ops/s
BufferUtf8Benchmark.writeUtf8Jayo        utf8    200000  thrpt    5      1688.991 ±     127.269  ops/s
BufferUtf8Benchmark.writeUtf8Jayo      sparse        20  thrpt    5  28750436.068 ±  406391.797  ops/s
BufferUtf8Benchmark.writeUtf8Jayo      sparse      2000  thrpt    5   1059183.324 ±    3170.804  ops/s
BufferUtf8Benchmark.writeUtf8Jayo      sparse    200000  thrpt    5      6450.132 ±      76.707  ops/s
BufferUtf8Benchmark.writeUtf8Jayo      2bytes        20  thrpt    5  11532129.737 ±   69165.417  ops/s
BufferUtf8Benchmark.writeUtf8Jayo      2bytes      2000  thrpt    5    177229.157 ±    1424.845  ops/s
BufferUtf8Benchmark.writeUtf8Jayo      2bytes    200000  thrpt    5      1767.368 ±      14.146  ops/s
BufferUtf8Benchmark.writeUtf8Okio       ascii        20  thrpt    5  30001624.435 ± 2711593.912  ops/s
BufferUtf8Benchmark.writeUtf8Okio       ascii      2000  thrpt    5   1884331.199 ±   16508.539  ops/s
BufferUtf8Benchmark.writeUtf8Okio       ascii    200000  thrpt    5     14851.617 ±     127.090  ops/s
BufferUtf8Benchmark.writeUtf8Okio        utf8        20  thrpt    5  17967810.448 ±  264939.601  ops/s
BufferUtf8Benchmark.writeUtf8Okio        utf8      2000  thrpt    5    219054.385 ±   13556.977  ops/s
BufferUtf8Benchmark.writeUtf8Okio        utf8    200000  thrpt    5      1939.766 ±     104.606  ops/s
BufferUtf8Benchmark.writeUtf8Okio      sparse        20  thrpt    5  32420730.677 ±  905809.780  ops/s
BufferUtf8Benchmark.writeUtf8Okio      sparse      2000  thrpt    5   1146102.910 ±   52240.731  ops/s
BufferUtf8Benchmark.writeUtf8Okio      sparse    200000  thrpt    5      8158.747 ±     269.859  ops/s
BufferUtf8Benchmark.writeUtf8Okio      2bytes        20  thrpt    5  11100526.639 ±  368803.592  ops/s
BufferUtf8Benchmark.writeUtf8Okio      2bytes      2000  thrpt    5    240384.840 ±   12606.030  ops/s
BufferUtf8Benchmark.writeUtf8Okio      2bytes    200000  thrpt    5      1504.018 ±      64.593  ops/s

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
