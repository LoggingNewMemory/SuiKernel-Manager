package kanagawa.yamada.suikernel.manager

data class SuiKernelConfig(
    val anyaThermal: Boolean = true,
    val yamadaBoost: Boolean = true,    
    val performanceMode: String = "Balanced",
    val inahoAudio: Boolean = true,
    val tenebrion: Boolean = true,
    val airaniCpuset: Boolean = true,
    val sandevistan: Boolean = false,
    val moonaZram: Boolean = true,
    val sparxieEnabled: Boolean = true,
    val sparxieSwap: Float = 30f
)

object SuiKernelIoctl {
    init {
        try {
            System.loadLibrary("suikernel_ioctl")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    external fun sendIoctl(cmd: Long, arg: Long): Int

    var cachedConfig: SuiKernelConfig = SuiKernelConfig()
    var isLoaded: Boolean = false

    const val CMD_ANYA_THERMAL = 0x392C8989L
    const val CMD_YAMADA_TOUCH_BOOST_DISABLE = 0x7A1F2C3BL
    const val CMD_YAMADA_TOUCH_BOOST_BALANCED = 0x5C3E8A1DL
    const val CMD_YAMADA_TOUCH_BOOST_GAMING = 0x3B9C4E2FL
    const val CMD_INAHO_AUDIO = 0x1281817AL
    const val CMD_TENEBRION = 0x11EAF5CBL
    const val CMD_SPARXIE_SWAP = 0x1B3FB033L
    const val CMD_AIRANI_CPUSET = 0x2FC6501BL
    const val CMD_SANDEVISTAN = 0x27B55B18L
    const val CMD_MOONA_ZRAM = 0x3E8B1A4CL

    fun readStorage(): SuiKernelConfig {
        val map = mutableMapOf<String, String>()
        try {
            val cmd = "for f in anya_thermal touch_boost inaho tenebrion airani sandevistan moona_zram sparxie swappiness; do if [ -f /data/SuiKernel/\$f ]; then echo \"\$f=\$(cat /data/SuiKernel/\$f)\"; fi; done"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        map[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val touchBoost = map["touch_boost"]
        val yamadaBoost = touchBoost != "0"
        val performanceMode = if (touchBoost == "2") "Gaming" else "Balanced"

        val config = SuiKernelConfig(
            anyaThermal = map["anya_thermal"] != "0",
            yamadaBoost = yamadaBoost,
            performanceMode = performanceMode,
            inahoAudio = map["inaho"] != "0",
            tenebrion = map["tenebrion"] != "0",
            airaniCpuset = map["airani"] != "0",
            sandevistan = map["sandevistan"] == "1",
            moonaZram = map["moona_zram"] != "0",
            sparxieEnabled = map["sparxie"] != "0",
            sparxieSwap = map["swappiness"]?.toFloatOrNull()?.coerceIn(10f, 100f) ?: 30f
        )
        cachedConfig = config
        isLoaded = true
        return config
    }

    fun syncStorage(config: SuiKernelConfig) {
        cachedConfig = config
        Thread {
            try {
                val anyaVal = if (config.anyaThermal) "1" else "0"
                val touchBoostVal = if (!config.yamadaBoost) "0" else if (config.performanceMode == "Gaming") "2" else "1"
                val inahoVal = if (config.inahoAudio) "1" else "0"
                val tenebrionVal = if (config.tenebrion) "1" else "0"
                val airaniVal = if (config.airaniCpuset) "1" else "0"
                val sandevistanVal = if (config.sandevistan) "1" else "0"
                val moonaVal = if (config.moonaZram) "1" else "0"
                val sparxieVal = if (config.sparxieEnabled) "1" else "0"
                val swappinessVal = if (config.sparxieEnabled) config.sparxieSwap.toInt() else 30

                val cmd = "mkdir -p /data/SuiKernel && " +
                        "echo -n $airaniVal > /data/SuiKernel/airani && " +
                        "echo -n $sandevistanVal > /data/SuiKernel/sandevistan && " +
                        "echo -n $moonaVal > /data/SuiKernel/moona_zram && " +
                        "echo -n $sparxieVal > /data/SuiKernel/sparxie && " +
                        "echo -n $swappinessVal > /data/SuiKernel/swappiness && " +
                        "echo -n $inahoVal > /data/SuiKernel/inaho && " +
                        "echo -n $touchBoostVal > /data/SuiKernel/touch_boost && " +
                        "echo -n $tenebrionVal > /data/SuiKernel/tenebrion && " +
                        "echo -n $anyaVal > /data/SuiKernel/anya_thermal && " +
                        "chmod 755 /data/SuiKernel && chmod 644 /data/SuiKernel/*"

                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun applyAllSettings(context: android.content.Context? = null) {
        Thread {
            val config = readStorage()
            sendIoctl(CMD_ANYA_THERMAL, if (config.anyaThermal) 1L else 0L)
            if (config.yamadaBoost) {
                if (config.performanceMode == "Gaming") {
                    sendIoctl(CMD_YAMADA_TOUCH_BOOST_GAMING, 1L)
                } else {
                    sendIoctl(CMD_YAMADA_TOUCH_BOOST_BALANCED, 1L)
                }
            } else {
                sendIoctl(CMD_YAMADA_TOUCH_BOOST_DISABLE, 1L)
            }
            sendIoctl(CMD_INAHO_AUDIO, if (config.inahoAudio) 1L else 0L)
            sendIoctl(CMD_TENEBRION, if (config.tenebrion) 1L else 0L)
            sendIoctl(CMD_AIRANI_CPUSET, if (config.airaniCpuset) 1L else 0L)
            sendIoctl(CMD_SANDEVISTAN, if (config.sandevistan) 1L else 0L)
            sendIoctl(CMD_MOONA_ZRAM, if (config.moonaZram) 1L else 0L)
            if (config.sparxieEnabled) {
                sendIoctl(CMD_SPARXIE_SWAP, config.sparxieSwap.toLong())
            } else {
                sendIoctl(CMD_SPARXIE_SWAP, 30L)
            }
        }.start()
    }
}
