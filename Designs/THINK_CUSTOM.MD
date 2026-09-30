# SuiKernel Manager: Stealth Customization Design

## 1. Objective
Provide real-time customization of SuiKernel's Yamada drivers for the SuiKernel Manager app, **without** exposing any trackable `sysfs` or `procfs` nodes that anti-cheats (e.g., Vanguard, Tencent) or banking apps can flag.

## 2. Side-by-Side Architecture: The Ecosystem
The device will utilize two distinct managers working in harmony:

1. **The Root Provider (Official KernelSU-Next Manager):**
   - Handles the complex kernel signature verification and secret FD injection.
   - Manages the root whitelist for the entire device.
   - Remains 100% stock, allowing easy updates from upstream.

2. **The Root Consumer (SuiKernel Manager):**
   - A dedicated Kotlin app focused purely on tuning the custom Yamada kernel drivers.
   - Requests root privileges from KSUN Manager.
   - Once granted root (running as UID 0), it communicates with the kernel via the Stealth IOCTL.

## 3. Stealth Architecture: The `/dev/null` IOCTL Hijack
Currently, half of the Yamada drivers use `module_param()`, which exposes highly trackable files in `/sys/module/`.

**The proposed kernel solution:**
1. We strip all `module_param()` declarations from the drivers.
2. We inject a hidden `ioctl` listener into the Linux kernel's standard `/dev/null` driver (`drivers/char/mem.c`).
3. Inside this listener, we add a strict security check: `if (!capable(CAP_SYS_ADMIN)) return -EPERM;`. This ensures only Root apps (like SuiKernel Manager, after KSUN grants it root) can trigger it.
4. The SuiKernel Manager app opens `/dev/null` and sends specific "magic" IOCTL commands to flip booleans or set integers in kernel memory instantly.

**Why this is perfectly stealthy:**
* `/dev/null` is a universal, highly trusted file on every operating system.
* No custom files or directories are created anywhere on the filesystem.
* Anti-cheats scan for suspicious file paths and processes; they cannot detect custom `ioctl` handlers buried inside standard character devices.

## 4. Refined Tunables (What is Actually Useful)
To avoid UI clutter and prevent users from accidentally breaking their devices, we will only expose the parameters that have a tangible impact on the user experience. Deep system scan intervals will remain hardcoded to their optimal defaults.

### Toggles (Switches)
1. **Anya Melfissa Thermal (`anya_thermal_enabled`)**
   - *Why:* Users need to disable thermal throttling for heavy gaming, but re-enable it for daily battery life.
2. **Yamada Gaming Boost (`yamada_boost_enabled`)**
   - *Why:* Users may want to disable touch-boost entirely for maximum battery saving.
3. **Ochinai Inaho Audio (`inaho_enabled`)**
   - *Why:* Users might want to disable the background audio polling thread when not listening to music.
4. **Tenebrion Screen-Off Throttler (`tenebrion_enabled`)**
   - *Why:* If a user wants to run a heavy background task (like a massive game download or video render) with the screen off, they need a way to temporarily disable Tenebrion's throttling.
5. **Airani Iofifteen Cpuset (`airani_enabled`)**
   - *Why:* Allows reverting to stock Android cpuset behavior on the fly.
6. **Sandevistan Boot (`sandevistan_enabled`)**
   - *Why:* Allows users to disable the boot-time performance lock.

### Tunables (Sliders/Input)
7. **Yamada Gaming Boost Duration (`yamada_boost_duration`)**
   - *Why:* Different users want different touch-boost lengths (e.g., `50ms` for battery, `300ms` for buttery smooth scrolling).
8. **Sparxie Swappiness Target (`sparxie_swappiness_val`)**
   - *Why:* A classic kernel tunable. Allows users to slide from `0` (prefer keeping apps in RAM) to `100` (aggressively swap to ZRAM).

## 5. Implementation Plan
1. **`drivers/Yamada/yamada_stealth.h`**: Create this central header to store the Magic IOCTL hex codes and `extern` variables.
2. **`drivers/char/mem.c`**: Add `.unlocked_ioctl` to `null_fops`. Write the switch statement that catches our Magic IOCTLs, verifies root, and updates the variables.
3. **Driver Cleanup**: Go through every `.c` file in `drivers/Yamada/`, remove `module_param()`, remove `static` keywords from the target variables, and adapt hardcoded `#define`s (like `SPARXIE_SWAPPINESS`) into global `int` variables.

## 6. SuiKernel Manager App Usage (Kotlin)
Because KSUN elevates the app's privileges directly, no C/C++ JNI is required. Android's built-in `Os.ioctlInt` can be used natively:

```kotlin
import android.system.Os
import java.io.RandomAccessFile

val SUI_MAGIC_YAMADA_BOOST = 0x55000002

fun enableYamadaBoost() {
    try {
        // Must be granted Root via KSUN Manager first!
        val file = RandomAccessFile("/dev/null", "rw")
        
        // Send the stealth IOCTL directly from Kotlin
        // Arg 1 = Enable, Arg 0 = Disable
        Os.ioctlInt(file.fd, SUI_MAGIC_YAMADA_BOOST, 1)
        
        file.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
   ```
