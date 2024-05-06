package jayo.benchmarks

import jayo.Buffer
import jayo.ByteString
import jayo.Utf8ByteString
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@Timeout(time = 20)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1)
open class BufferUtf8Benchmark {
    companion object {
        private val strings = java.util.Map.of(
            "ascii",
            "Um, I'll tell you the problem with the scientific power that you're using here, "
                    + "it didn't require any discipline to attain it. You read what others had done and you "
                    + "took the next step. You didn't earn the knowledge for yourselves, so you don't take any "
                    + "responsibility for it. You stood on the shoulders of geniuses to accomplish something "
                    + "as fast as you could, and before you even knew what you had, you patented it, and "
                    + "packaged it, and slapped it on a plastic lunchbox, and now you're selling it, you wanna "
                    + "sell it.",
            "latin1",
            "Je vais vous dire le problème avec le pouvoir scientifique que vous utilisez ici, il n'a " +
                    "pas fallu de discipline pour l'atteindre. Vous avez lu ce que les autres avaient fait " +
                    "et vous avez pris la prochaine étape. Vous n'avez pas gagné les connaissances pour " +
                    "vous-mêmes, donc vous n'en assumez aucune responsabilité. Tu étais sur les épaules de " +
                    "génies pour accomplir quelque chose aussi vite que tu pouvais, et avant même " +
                    "de savoir ce que tu avais, tu l'as breveté, et l'as emballé, et tu l'as mis sur une boîte " +
                    "à lunch en plastique, et maintenant tu le vends, tu veux le vendre",
            "utf8",
            "Սｍ, I'll 𝓽𝖾ll ᶌօ𝘂 ᴛℎ℮ 𝜚𝕣०ｂl𝖾ｍ ｗі𝕥𝒽 𝘵𝘩𝐞 𝓼𝙘𝐢𝔢𝓷𝗍𝜄𝚏𝑖ｃ 𝛠𝝾ｗ𝚎𝑟 𝕥ｈ⍺𝞃 𝛄𝓸𝘂'𝒓𝗲 υ𝖘𝓲𝗇ɡ 𝕙𝚎𝑟ｅ, "
                    + "𝛊𝓽 ⅆ𝕚𝐝𝝿'𝗍 𝔯𝙚𝙦ᴜ𝜾𝒓𝘦 𝔞𝘯𝐲 ԁ𝜄𝑠𝚌ι𝘱lι𝒏ｅ 𝑡𝜎 𝕒𝚝𝖙𝓪і𝞹 𝔦𝚝. 𝒀ο𝗎 𝔯𝑒⍺𝖉 ｗ𝐡𝝰𝔱 𝞂𝞽һ𝓮𝓇ƽ հ𝖺𝖉 ⅾ𝛐𝝅ⅇ 𝝰πԁ 𝔂ᴑᴜ 𝓉ﮨ၀𝚔 "
                    + "т𝒽𝑒 𝗇𝕖ⅹ𝚝 𝔰𝒕е𝓅. 𝘠ⲟ𝖚 𝖉ⅰԁ𝝕'τ 𝙚𝚊ｒ𝞹 𝘵Ꮒ𝖾 𝝒𝐧هｗl𝑒𝖉ƍ𝙚 𝓯૦ｒ 𝔂𝞼𝒖𝕣𝑠𝕖l𝙫𝖊𝓼, 𐑈о ｙ𝘰𝒖 ⅆە𝗇'ｔ 𝜏α𝒌𝕖 𝛂𝟉ℽ "
                    + "𝐫ⅇ𝗌ⲣ๐ϖ𝖘ꙇᖯ𝓲l𝓲𝒕𝘆 𝐟𝞼𝘳 𝚤𝑡. 𝛶𝛔𝔲 ｓ𝕥σσ𝐝 ﮩ𝕟 𝒕𝗁𝔢 𝘴𝐡𝜎ᴜlⅾ𝓮𝔯𝚜 𝛐𝙛 ᶃ𝚎ᴨᎥս𝚜𝘦𝓈 𝓽𝞸 ａ𝒄𝚌𝞸ｍρl𝛊ꜱ𝐡 𝓈𝚘ｍ𝚎𝞃𝔥⍳𝞹𝔤 𝐚𝗌 𝖋ａ𝐬𝒕 "
                    + "αｓ γ𝛐𝕦 𝔠ﻫ𝛖lԁ, 𝚊π𝑑 Ь𝑒𝙛૦𝓇𝘦 𝓎٥𝖚 ⅇｖℯ𝝅 𝜅ո𝒆ｗ ｗ𝗵𝒂𝘁 ᶌ੦𝗎 ｈ𝐚𝗱, 𝜸ﮨ𝒖 𝓹𝝰𝔱𝖾𝗇𝓽𝔢ⅆ і𝕥, 𝚊𝜛𝓭 𝓹𝖺ⅽϰ𝘢ℊеᏧ 𝑖𝞃, "
                    + "𝐚𝛑ꓒ 𝙨l𝔞р𝘱𝔢𝓭 ɩ𝗍 ہ𝛑 𝕒 ｐl𝛂ѕᴛ𝗂𝐜 l𝞄ℼ𝔠𝒽𝑏ﮪ⨯, 𝔞ϖ𝒹 ｎ𝛔ｗ 𝛾𝐨𝞄'𝗿𝔢 ꜱ℮ll𝙞ｎɡ ɩ𝘁, 𝙮𝕠𝛖 ｗ𝑎ℼ𝚗𝛂 𝕤𝓮ll 𝙞𝓉.",
            "2bytes",
            "\u0080\u07ff",
            "3bytes",
            "\u0800\ud7ff\ue000\uffff",
            "4bytes",
            "\ud835\udeca",
            // high surrogate, 'a', low surrogate, and 'a'
            "bad",
            "\ud800\u0061\udc00\u0061"
        )
    }

    @Param("20", "2000", "200000")
    private var length = 0

    @Param("ascii", "latin1", "utf8", "2bytes", "3bytes", "4bytes", "bad")
    private lateinit var encoding: String

    private lateinit var jayoBuffer: Buffer
    private lateinit var jayoDecode: ByteString
    private lateinit var jayoDecodeUtf8: Utf8ByteString
    private lateinit var okioBuffer: okio.Buffer
    private lateinit var okioDecode: okio.ByteString
    private lateinit var encode: String

    @Setup
    fun setup() {
        val part = strings[encoding]

        // Make all the strings the same length for comparison
        val builder = StringBuilder(length + 1000)
        while (builder.length < length) {
            builder.append(part)
        }
        builder.setLength(length)
        encode = builder.toString()

        // Prepare a string and ByteString for encoding and decoding with Okio and Jayo
        jayoBuffer = Buffer()
        val tempJayoIo = Buffer()
        tempJayoIo.writeUtf8(encode)
        jayoDecode = tempJayoIo.snapshot()
        jayoDecodeUtf8 = tempJayoIo.readUtf8ByteString()

        okioBuffer = okio.Buffer()
        val tempOkio = okio.Buffer()
        tempOkio.writeUtf8(encode)
        okioDecode = tempOkio.snapshot()
    }

    @Benchmark
    fun writeUtf8Okio() {
        okioBuffer.writeUtf8(encode)
        okioBuffer.clear()
    }

    @Benchmark
    fun readUtf8Okio(): Int {
        okioBuffer.write(okioDecode)
        return okioBuffer.readUtf8().length
    }

    @Benchmark
    fun writeUtf8Jayo() {
        jayoBuffer.writeUtf8(encode)
        jayoBuffer.clear()
    }

    @Benchmark
    fun readUtf8Jayo(): Int {
        jayoBuffer.write(jayoDecode)
        return jayoBuffer.readUtf8().length
    }

    @Benchmark
    fun readUtf8ByteStringJayo(): Int {
        jayoBuffer.write(jayoDecode)
        // will depend on source type
        return jayoBuffer.readUtf8ByteString().length
    }
}