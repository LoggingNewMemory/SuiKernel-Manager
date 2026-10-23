/*
 * SuiKernel Manager
 * Copyright (C) 2026 Kanagawa Yamada
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
    external fun hasNativeRoot(): Boolean
    external fun nativeRunCommand(cmd: String): Int
    external fun nativeRunCommandWithOutput(cmd: String): String?
    external fun nativeWriteFeature(name: String, value: String): Boolean
    external fun nativeWriteStorage(
        anya: String,
        touchBoost: String,
        inaho: String,
        tenebrion: String,
        airani: String,
        sandevistan: String,
        moona: String,
        sparxie: String,
        swappiness: String
    ): Boolean
    external fun nativeReadStorage(): String?

    var cachedConfig: SuiKernelConfig = SuiKernelConfig()
    var isLoaded: Boolean = false

    @Volatile
    private var nativeRootAvailable: Boolean? = null

    fun isNativeRootSupported(): Boolean {
        nativeRootAvailable?.let { return it }
        return synchronized(this) {
            nativeRootAvailable ?: run {
                val supported = try {
                    hasNativeRoot()
                } catch (e: Exception) {
                    false
                }
                nativeRootAvailable = supported
                supported
            }
        }
    }

    private val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

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
    const val CMD_SUI_ELEVATE_ROOT = 0x53554901L
    const val SUI_ELEVATE_MAGIC = 0x59414D4144415355L

    fun runCommand(cmd: String): Boolean {
        // 1. Try kernel native root escalation in a child process
        try {
            if (isNativeRootSupported()) {
                val ret = nativeRunCommand(cmd)
                if (ret == 0) return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback to su
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun runCommandWithOutput(cmd: String): List<String> {
        // 1. Try kernel native root escalation in a child process
        try {
            if (isNativeRootSupported()) {
                val output = nativeRunCommandWithOutput(cmd)
                if (output != null) {
                    return output.lines().filter { it.isNotEmpty() }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback to su
        val lines = mutableListOf<String>()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.inputStream.bufferedReader().useLines { it.forEach { line -> lines.add(line) } }
            process.waitFor()
        } catch (e: Exception) {
            // Ignore
        }
        return lines
    }

    private fun updateCache(name: String, value: String) {
        cachedConfig = when (name) {
            "anya_thermal" -> cachedConfig.copy(anyaThermal = value != "0")
            "touch_boost" -> {
                val mode = when (value) {
                    "2" -> "Gaming"
                    "1" -> "Balanced"
                    else -> cachedConfig.performanceMode
                }
                cachedConfig.copy(yamadaBoost = value != "0", performanceMode = mode)
            }
            "inaho" -> cachedConfig.copy(inahoAudio = value != "0")
            "tenebrion" -> cachedConfig.copy(tenebrion = value != "0")
            "airani" -> cachedConfig.copy(airaniCpuset = value != "0")
            "sandevistan" -> cachedConfig.copy(sandevistan = value == "1")
            "moona_zram" -> cachedConfig.copy(moonaZram = value != "0")
            "sparxie" -> cachedConfig.copy(sparxieEnabled = value != "0")
            "swappiness" -> cachedConfig.copy(sparxieSwap = value.toFloatOrNull()?.coerceIn(10f, 100f) ?: 30f)
            else -> cachedConfig
        }
    }

    // Immediately writes a single feature file to /data/SuiKernel/<feature>
    fun writeFeature(name: String, value: String) {
        updateCache(name, value)
        ioExecutor.execute {
            var written = false
            if (isNativeRootSupported()) {
                written = nativeWriteFeature(name, value)
            }
            if (!written) {
                runCommand("mkdir -p /data/SuiKernel && echo -n $value > /data/SuiKernel/$name && chmod 755 /data/SuiKernel && chmod 644 /data/SuiKernel/$name")
            }
        }
    }

    private fun doWriteStorage(config: SuiKernelConfig) {
        val anyaVal = if (config.anyaThermal) "1" else "0"
        val touchBoostVal = if (!config.yamadaBoost) "0" else if (config.performanceMode == "Gaming") "2" else "1"
        val inahoVal = if (config.inahoAudio) "1" else "0"
        val tenebrionVal = if (config.tenebrion) "1" else "0"
        val airaniVal = if (config.airaniCpuset) "1" else "0"
        val sandevistanVal = if (config.sandevistan) "1" else "0"
        val moonaVal = if (config.moonaZram) "1" else "0"
        val sparxieVal = if (config.sparxieEnabled) "1" else "0"
        val swappinessVal = if (config.sparxieEnabled) config.sparxieSwap.toInt().toString() else "30"

        var written = false
        if (isNativeRootSupported()) {
            written = nativeWriteStorage(
                anya = anyaVal,
                touchBoost = touchBoostVal,
                inaho = inahoVal,
                tenebrion = tenebrionVal,
                airani = airaniVal,
                sandevistan = sandevistanVal,
                moona = moonaVal,
                sparxie = sparxieVal,
                swappiness = swappinessVal
            )
        }

        // Fallback to shell command if direct C write was not used or failed
        if (!written) {
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

            runCommand(cmd)
        }
    }

    private fun doReadStorage(): SuiKernelConfig {
        val map = mutableMapOf<String, String>()

        // 1. First priority: direct C filesystem read from /data/SuiKernel/ (no sh / toybox)
        try {
            if (isNativeRootSupported()) {
                val output = nativeReadStorage()
                if (!output.isNullOrEmpty()) {
                    output.lines().forEach { line ->
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2) {
                            map[parts[0].trim()] = parts[1].trim()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback to shell command if direct C read returned empty (e.g. su fallback)
        if (map.isEmpty()) {
            try {
                val cmd = "for f in anya_thermal touch_boost inaho tenebrion airani sandevistan moona_zram sparxie swappiness; do if [ -f /data/SuiKernel/\$f ]; then echo \"\$f=\$(cat /data/SuiKernel/\$f)\"; fi; done"
                val lines = runCommandWithOutput(cmd)
                lines.forEach { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        map[parts[0].trim()] = parts[1].trim()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // If files exist in /data/SuiKernel/, update state accurately from disk
        if (map.isNotEmpty()) {
            val touchBoost = map["touch_boost"]
            val yamadaBoost = if (touchBoost != null) touchBoost != "0" else cachedConfig.yamadaBoost
            val performanceMode = if (touchBoost == "2") "Gaming" else if (touchBoost == "1") "Balanced" else cachedConfig.performanceMode

            val config = SuiKernelConfig(
                anyaThermal = if (map.containsKey("anya_thermal")) map["anya_thermal"] != "0" else cachedConfig.anyaThermal,
                yamadaBoost = yamadaBoost,
                performanceMode = performanceMode,
                inahoAudio = if (map.containsKey("inaho")) map["inaho"] != "0" else cachedConfig.inahoAudio,
                tenebrion = if (map.containsKey("tenebrion")) map["tenebrion"] != "0" else cachedConfig.tenebrion,
                airaniCpuset = if (map.containsKey("airani")) map["airani"] != "0" else cachedConfig.airaniCpuset,
                sandevistan = if (map.containsKey("sandevistan")) map["sandevistan"] == "1" else cachedConfig.sandevistan,
                moonaZram = if (map.containsKey("moona_zram")) map["moona_zram"] != "0" else cachedConfig.moonaZram,
                sparxieEnabled = if (map.containsKey("sparxie")) map["sparxie"] != "0" else cachedConfig.sparxieEnabled,
                sparxieSwap = map["swappiness"]?.toFloatOrNull()?.coerceIn(10f, 100f) ?: cachedConfig.sparxieSwap
            )
            cachedConfig = config
            isLoaded = true
            return config
        }

        // If map is empty and we already have a loaded config in memory, do NOT revert to default ON!
        if (isLoaded) {
            return cachedConfig
        }

        val config = SuiKernelConfig()
        cachedConfig = config
        isLoaded = true
        doWriteStorage(config)
        return config
    }

    fun readStorage(): SuiKernelConfig {
        return try {
            ioExecutor.submit(java.util.concurrent.Callable {
                doReadStorage()
            }).get(3, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            cachedConfig
        }
    }

    fun syncStorage(config: SuiKernelConfig) {
        cachedConfig = config
        ioExecutor.execute {
            try {
                doWriteStorage(config)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
