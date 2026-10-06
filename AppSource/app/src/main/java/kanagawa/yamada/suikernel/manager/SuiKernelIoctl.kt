package kanagawa.yamada.suikernel.manager

object SuiKernelIoctl {
    init {
        try {
            System.loadLibrary("suikernel_ioctl")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    external fun sendIoctl(cmd: Long, arg: Long): Int

    const val CMD_ANYA_THERMAL = 0x392C8989L
    const val CMD_YAMADA_TOUCH_BOOST_DISABLE = 0x7A1F2C3BL
    const val CMD_YAMADA_TOUCH_BOOST_BALANCED = 0x5C3E8A1DL
    const val CMD_YAMADA_TOUCH_BOOST_GAMING = 0x3B9C4E2FL
    const val CMD_INAHO_AUDIO = 0x1281817AL
    const val CMD_TENEBRION = 0x11EAF5CBL
    const val CMD_SPARXIE_SWAP = 0x1B3FB033L
    const val CMD_AIRANI_CPUSET = 0x2FC6501BL
    const val CMD_SANDEVISTAN = 0x27B55B18L

    fun applyAllSettings(context: android.content.Context) {
        val sharedPrefs = context.getSharedPreferences("SuiKernelPrefs", android.content.Context.MODE_PRIVATE)

        val anyaThermal = sharedPrefs.getBoolean("anyaThermal", true)
        sendIoctl(CMD_ANYA_THERMAL, if (anyaThermal) 1L else 0L)

        val yamadaBoost = sharedPrefs.getBoolean("yamadaBoost", true)
        if (yamadaBoost) {
            val performanceMode = sharedPrefs.getString("performanceMode", "Balanced")
            if (performanceMode == "Gaming") {
                sendIoctl(CMD_YAMADA_TOUCH_BOOST_GAMING, 1L)
            } else {
                sendIoctl(CMD_YAMADA_TOUCH_BOOST_BALANCED, 1L)
            }
        } else {
            sendIoctl(CMD_YAMADA_TOUCH_BOOST_DISABLE, 1L)
        }

        val inahoAudio = sharedPrefs.getBoolean("inahoAudio", true)
        sendIoctl(CMD_INAHO_AUDIO, if (inahoAudio) 1L else 0L)

        val tenebrion = sharedPrefs.getBoolean("tenebrion", true)
        sendIoctl(CMD_TENEBRION, if (tenebrion) 1L else 0L)

        val airaniCpuset = sharedPrefs.getBoolean("airaniCpuset", true)
        sendIoctl(CMD_AIRANI_CPUSET, if (airaniCpuset) 1L else 0L)

        val sandevistan = sharedPrefs.getBoolean("sandevistan", false)
        sendIoctl(CMD_SANDEVISTAN, if (sandevistan) 1L else 0L)

        val sparxieEnabled = sharedPrefs.getBoolean("sparxieEnabled", true)
        if (sparxieEnabled) {
            val sparxieSwap = sharedPrefs.getFloat("sparxieSwap", 60f)
            sendIoctl(CMD_SPARXIE_SWAP, sparxieSwap.toLong())
        } else {
            sendIoctl(CMD_SPARXIE_SWAP, 100L)
        }
    }
}
