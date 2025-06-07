You must comment benchmarks except the one you want to execute in `build.gralde.kts`

## BufferLatin1Benchmark

Benchmark                              (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferLatin1Benchmark.readLatin1Jayo        ascii        20  thrpt    5  30369590.913 ±   96379.706  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii      2000  thrpt    5   5924459.461 ± 1202169.908  ops/s
BufferLatin1Benchmark.readLatin1Jayo        ascii    200000  thrpt    5     28969.116 ±     324.706  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1        20  thrpt    5  30357816.633 ±  202821.633  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1      2000  thrpt    5   5996139.798 ±  170965.979  ops/s
BufferLatin1Benchmark.readLatin1Jayo       latin1    200000  thrpt    5     28692.501 ±    1202.793  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii        20  thrpt    5  23783253.651 ±  102780.926  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii      2000  thrpt    5   3225915.952 ±  323169.842  ops/s
BufferLatin1Benchmark.readLatin1Okio        ascii    200000  thrpt    5     18068.318 ±     160.691  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1        20  thrpt    5  24823010.922 ±  431507.690  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1      2000  thrpt    5   3288287.303 ±   68546.196  ops/s
BufferLatin1Benchmark.readLatin1Okio       latin1    200000  thrpt    5     17477.193 ±    1278.967  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii        20  thrpt    5  38079646.549 ±  352836.550  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii      2000  thrpt    5  17713132.731 ±  852160.748  ops/s
BufferLatin1Benchmark.writeLatin1Jayo       ascii    200000  thrpt    5    199969.505 ±    3326.932  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1        20  thrpt    5  38098292.523 ±  771354.160  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1      2000  thrpt    5  18019691.304 ±  102734.354  ops/s
BufferLatin1Benchmark.writeLatin1Jayo      latin1    200000  thrpt    5    200485.069 ±     183.773  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii        20  thrpt    5  28979890.843 ± 2133688.124  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii      2000  thrpt    5   5692368.721 ±  518675.205  ops/s
BufferLatin1Benchmark.writeLatin1Okio       ascii    200000  thrpt    5     38819.751 ±     244.604  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1        20  thrpt    5  28200762.042 ± 1461243.900  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1      2000  thrpt    5   5892011.391 ±  276678.247  ops/s
BufferLatin1Benchmark.writeLatin1Okio      latin1    200000  thrpt    5     39046.463 ±     439.047  ops/s

## BufferUtf8Benchmark

Benchmark                                     (encoding)  (length)   Mode  Cnt         Score         Error  Units
BufferUtf8Benchmark.readUtf8Jayo                   ascii        20  thrpt    5  31544344.938 ±  238000.915  ops/s
BufferUtf8Benchmark.readUtf8Jayo                   ascii      2000  thrpt    5   2132363.223 ±   43158.057  ops/s
BufferUtf8Benchmark.readUtf8Jayo                   ascii    200000  thrpt    5     31981.773 ±     697.880  ops/s
BufferUtf8Benchmark.readUtf8Jayo                  latin1        20  thrpt    5  31577272.614 ±  330214.585  ops/s
BufferUtf8Benchmark.readUtf8Jayo                  latin1      2000  thrpt    5    818749.446 ±   12678.128  ops/s
BufferUtf8Benchmark.readUtf8Jayo                  latin1    200000  thrpt    5      9026.513 ±     580.601  ops/s
BufferUtf8Benchmark.readUtf8Jayo         utf8MostlyAscii        20  thrpt    5  27402807.328 ±   84334.003  ops/s
BufferUtf8Benchmark.readUtf8Jayo         utf8MostlyAscii      2000  thrpt    5    833618.800 ±   17397.047  ops/s
BufferUtf8Benchmark.readUtf8Jayo         utf8MostlyAscii    200000  thrpt    5      8921.753 ±     427.648  ops/s
BufferUtf8Benchmark.readUtf8Jayo                    utf8        20  thrpt    5  22404283.412 ± 9453934.692  ops/s
BufferUtf8Benchmark.readUtf8Jayo                    utf8      2000  thrpt    5    488323.586 ±   30064.188  ops/s
BufferUtf8Benchmark.readUtf8Jayo                    utf8    200000  thrpt    5      5556.111 ±     226.231  ops/s
BufferUtf8Benchmark.readUtf8Jayo                  2bytes        20  thrpt    5  28685066.972 ±  634228.450  ops/s
BufferUtf8Benchmark.readUtf8Jayo                  2bytes      2000  thrpt    5   1016698.356 ±   16704.516  ops/s
BufferUtf8Benchmark.readUtf8Jayo                  2bytes    200000  thrpt    5      7314.074 ±     155.900  ops/s
BufferUtf8Benchmark.readUtf8Okio                   ascii        20  thrpt    5  45728213.195 ± 1085425.971  ops/s
BufferUtf8Benchmark.readUtf8Okio                   ascii      2000  thrpt    5   6978951.769 ±  629635.272  ops/s
BufferUtf8Benchmark.readUtf8Okio                   ascii    200000  thrpt    5     29269.773 ±    3369.873  ops/s
BufferUtf8Benchmark.readUtf8Okio                  latin1        20  thrpt    5  47126892.243 ± 2028536.832  ops/s
BufferUtf8Benchmark.readUtf8Okio                  latin1      2000  thrpt    5    967666.483 ±   55001.374  ops/s
BufferUtf8Benchmark.readUtf8Okio                  latin1    200000  thrpt    5      8074.666 ±     117.373  ops/s
BufferUtf8Benchmark.readUtf8Okio         utf8MostlyAscii        20  thrpt    5  24006680.225 ±  188877.240  ops/s
BufferUtf8Benchmark.readUtf8Okio         utf8MostlyAscii      2000  thrpt    5    590079.156 ±   12913.949  ops/s
BufferUtf8Benchmark.readUtf8Okio         utf8MostlyAscii    200000  thrpt    5      5336.858 ±     350.288  ops/s
BufferUtf8Benchmark.readUtf8Okio                    utf8        20  thrpt    5  19498379.285 ±  672823.006  ops/s
BufferUtf8Benchmark.readUtf8Okio                    utf8      2000  thrpt    5    244248.095 ±    1473.863  ops/s
BufferUtf8Benchmark.readUtf8Okio                    utf8    200000  thrpt    5      2466.478 ±      32.307  ops/s
BufferUtf8Benchmark.readUtf8Okio                  2bytes        20  thrpt    5  19482218.835 ±   68633.637  ops/s
BufferUtf8Benchmark.readUtf8Okio                  2bytes      2000  thrpt    5    343308.128 ±   26068.304  ops/s
BufferUtf8Benchmark.readUtf8Okio                  2bytes    200000  thrpt    5      2764.002 ±      37.862  ops/s
BufferUtf8Benchmark.readUtf8StringJayo             ascii        20  thrpt    5  32765908.945 ±  703513.376  ops/s
BufferUtf8Benchmark.readUtf8StringJayo             ascii      2000  thrpt    5   6497849.981 ±  559874.962  ops/s
BufferUtf8Benchmark.readUtf8StringJayo             ascii    200000  thrpt    5     29165.446 ±    1640.984  ops/s
BufferUtf8Benchmark.readUtf8StringJayo            latin1        20  thrpt    5  32785091.008 ±  437943.483  ops/s
BufferUtf8Benchmark.readUtf8StringJayo            latin1      2000  thrpt    5   1009075.568 ±    6453.332  ops/s
BufferUtf8Benchmark.readUtf8StringJayo            latin1    200000  thrpt    5      7906.190 ±     246.030  ops/s
BufferUtf8Benchmark.readUtf8StringJayo   utf8MostlyAscii        20  thrpt    5  21615260.779 ±  115793.162  ops/s
BufferUtf8Benchmark.readUtf8StringJayo   utf8MostlyAscii      2000  thrpt    5    588603.489 ±    9172.817  ops/s
BufferUtf8Benchmark.readUtf8StringJayo   utf8MostlyAscii    200000  thrpt    5      6748.892 ±     110.839  ops/s
BufferUtf8Benchmark.readUtf8StringJayo              utf8        20  thrpt    5  18209225.095 ±  123923.499  ops/s
BufferUtf8Benchmark.readUtf8StringJayo              utf8      2000  thrpt    5    243910.909 ±    2468.062  ops/s
BufferUtf8Benchmark.readUtf8StringJayo              utf8    200000  thrpt    5      2484.331 ±      26.434  ops/s
BufferUtf8Benchmark.readUtf8StringJayo            2bytes        20  thrpt    5  17319538.423 ±   79331.047  ops/s
BufferUtf8Benchmark.readUtf8StringJayo            2bytes      2000  thrpt    5    345331.059 ±    5925.041  ops/s
BufferUtf8Benchmark.readUtf8StringJayo            2bytes    200000  thrpt    5      2768.745 ±      52.829  ops/s
BufferUtf8Benchmark.writeByteStringJayo            ascii        20  thrpt    5  42311752.198 ±  211603.041  ops/s
BufferUtf8Benchmark.writeByteStringJayo            ascii      2000  thrpt    5  42321227.261 ±   69493.197  ops/s
BufferUtf8Benchmark.writeByteStringJayo            ascii    200000  thrpt    5   5071934.220 ±   11960.004  ops/s
BufferUtf8Benchmark.writeByteStringJayo           latin1        20  thrpt    5  40851728.124 ±  427587.903  ops/s
BufferUtf8Benchmark.writeByteStringJayo           latin1      2000  thrpt    5  42315032.510 ±  120732.367  ops/s
BufferUtf8Benchmark.writeByteStringJayo           latin1    200000  thrpt    5   4706384.437 ±  221440.033  ops/s
BufferUtf8Benchmark.writeByteStringJayo  utf8MostlyAscii        20  thrpt    5  42311132.381 ±  238033.572  ops/s
BufferUtf8Benchmark.writeByteStringJayo  utf8MostlyAscii      2000  thrpt    5  42466496.708 ±  426206.982  ops/s
BufferUtf8Benchmark.writeByteStringJayo  utf8MostlyAscii    200000  thrpt    5   4731066.414 ±   25030.509  ops/s
BufferUtf8Benchmark.writeByteStringJayo             utf8        20  thrpt    5  42509161.631 ±  246877.141  ops/s
BufferUtf8Benchmark.writeByteStringJayo             utf8      2000  thrpt    5  42205424.855 ±  296944.927  ops/s
BufferUtf8Benchmark.writeByteStringJayo             utf8    200000  thrpt    5   2584021.880 ±   14879.243  ops/s
BufferUtf8Benchmark.writeByteStringJayo           2bytes        20  thrpt    5  42575087.955 ±  119002.647  ops/s
BufferUtf8Benchmark.writeByteStringJayo           2bytes      2000  thrpt    5  42210561.418 ±  109217.225  ops/s
BufferUtf8Benchmark.writeByteStringJayo           2bytes    200000  thrpt    5   2603723.697 ±    4663.685  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                  ascii        20  thrpt    5  33374809.292 ±  892031.288  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                  ascii      2000  thrpt    5   5362063.059 ±  107319.017  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                  ascii    200000  thrpt    5     45853.509 ±    1307.972  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                 latin1        20  thrpt    5  33920380.025 ±   93903.923  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                 latin1      2000  thrpt    5    832916.893 ±   21427.224  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                 latin1    200000  thrpt    5      8015.443 ±     110.155  ops/s
BufferUtf8Benchmark.writeUtf8Jayo        utf8MostlyAscii        20  thrpt    5  24164540.108 ±  129832.646  ops/s
BufferUtf8Benchmark.writeUtf8Jayo        utf8MostlyAscii      2000  thrpt    5    673479.964 ±    3267.423  ops/s
BufferUtf8Benchmark.writeUtf8Jayo        utf8MostlyAscii    200000  thrpt    5      6604.168 ±      68.428  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                   utf8        20  thrpt    5  19987485.085 ±   92441.531  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                   utf8      2000  thrpt    5    295710.909 ±    3309.891  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                   utf8    200000  thrpt    5      3473.024 ±     160.638  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                 2bytes        20  thrpt    5  23438100.224 ±   71932.455  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                 2bytes      2000  thrpt    5    582976.273 ±    5185.801  ops/s
BufferUtf8Benchmark.writeUtf8Jayo                 2bytes    200000  thrpt    5      3728.096 ±      29.455  ops/s
BufferUtf8Benchmark.writeUtf8Okio                  ascii        20  thrpt    5  25388189.532 ±  111280.352  ops/s
BufferUtf8Benchmark.writeUtf8Okio                  ascii      2000  thrpt    5    551690.313 ±    1779.738  ops/s
BufferUtf8Benchmark.writeUtf8Okio                  ascii    200000  thrpt    5      5021.054 ±      50.795  ops/s
BufferUtf8Benchmark.writeUtf8Okio                 latin1        20  thrpt    5  25276035.383 ±  186315.351  ops/s
BufferUtf8Benchmark.writeUtf8Okio                 latin1      2000  thrpt    5    339960.108 ±   49628.043  ops/s
BufferUtf8Benchmark.writeUtf8Okio                 latin1    200000  thrpt    5      3148.829 ±     323.899  ops/s
BufferUtf8Benchmark.writeUtf8Okio        utf8MostlyAscii        20  thrpt    5  25378896.208 ±   62495.985  ops/s
BufferUtf8Benchmark.writeUtf8Okio        utf8MostlyAscii      2000  thrpt    5   1616304.216 ±   19210.375  ops/s
BufferUtf8Benchmark.writeUtf8Okio        utf8MostlyAscii    200000  thrpt    5     15658.160 ±     245.688  ops/s
BufferUtf8Benchmark.writeUtf8Okio                   utf8        20  thrpt    5  16970886.679 ±  264436.725  ops/s
BufferUtf8Benchmark.writeUtf8Okio                   utf8      2000  thrpt    5    208014.839 ±    3590.528  ops/s
BufferUtf8Benchmark.writeUtf8Okio                   utf8    200000  thrpt    5      2055.781 ±      36.445  ops/s
BufferUtf8Benchmark.writeUtf8Okio                 2bytes        20  thrpt    5  11424505.525 ±  257782.413  ops/s
BufferUtf8Benchmark.writeUtf8Okio                 2bytes      2000  thrpt    5    244414.661 ±    1213.685  ops/s
BufferUtf8Benchmark.writeUtf8Okio                 2bytes    200000  thrpt    5      1855.809 ±      27.441  ops/s

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
SocketReaderBenchmark.readerJayo   jayo-io  thrpt    5  43424.974 ± 2887.253  ops/s
SocketReaderBenchmark.readerJayo  jayo-nio  thrpt    5  44226.808 ± 2137.315  ops/s
SocketReaderBenchmark.readerOkio      okio  thrpt    5  41227.906 ± 1017.343  ops/s

## TcpAndJsonSerializationBenchmark

Benchmark                                     Mode  Cnt      Score      Error  Units
TcpAndJsonSerializationBenchmark.readerJayo  thrpt    5  21127.543 ± 5022.590  ops/s
TcpAndJsonSerializationBenchmark.readerOkio  thrpt    5  20698.793 ± 1559.715  ops/s
TcpAndJsonSerializationBenchmark.senderJayo  thrpt    5   1886.880 ±   84.593  ops/s
TcpAndJsonSerializationBenchmark.senderOkio  thrpt    5   1892.732 ±  204.239  ops/s
