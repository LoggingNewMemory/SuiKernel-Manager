/*
 * SuiKernel Manager
 * Copyright (C) 2026 Kana Wesley Yamada
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

#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <errno.h>

#define LOG_TAG "FindGovernor"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define CMD_SUI_ELEVATE_ROOT 0x53554901
#define SUI_ELEVATE_MAGIC 0x4B414E4153550000ULL

static int elevate_thread_if_needed(void) {
    if (getuid() == 0) return 0;
    if (gettid() == getpid()) return -1; // protect UI thread

    int fd = open("/dev/null", O_RDWR);
    if (fd < 0) return -1;
    int ret = ioctl(fd, CMD_SUI_ELEVATE_ROOT, SUI_ELEVATE_MAGIC);
    close(fd);
    return ret;
}

// Reads scaling_available_governors directly from sysfs policy0
JNIEXPORT jstring JNICALL
Java_kana_wesley_yamada_suikernel_manager_SuiKernelIoctl_nativeGetAvailableGovernors(JNIEnv *env, jobject thiz) {
    elevate_thread_if_needed();

    char buf[512] = {0};
    int fd = open("/sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors", O_RDONLY);
    if (fd < 0) return NULL;

    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);

    if (n > 0) {
        buf[n] = '\0';
        return (*env)->NewStringUTF(env, buf);
    }
    return NULL;
}

// Reads current scaling_governor directly from sysfs policy0
JNIEXPORT jstring JNICALL
Java_kana_wesley_yamada_suikernel_manager_SuiKernelIoctl_nativeGetCurrentGovernor(JNIEnv *env, jobject thiz) {
    elevate_thread_if_needed();

    char buf[64] = {0};
    int fd = open("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor", O_RDONLY);
    if (fd < 0) return NULL;

    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);

    if (n > 0) {
        while (n > 0 && (buf[n - 1] == '\n' || buf[n - 1] == '\r' || buf[n - 1] == ' ')) {
            buf[--n] = '\0';
        }
        return (*env)->NewStringUTF(env, buf);
    }
    return NULL;
}

// Sets scaling_governor directly across policy nodes in sysfs
JNIEXPORT jboolean JNICALL
Java_kana_wesley_yamada_suikernel_manager_SuiKernelIoctl_nativeSetGovernor(JNIEnv *env, jobject thiz, jstring gov_jstr) {
    if (!gov_jstr) return JNI_FALSE;
    const char *gov = (*env)->GetStringUTFChars(env, gov_jstr, NULL);
    if (!gov) return JNI_FALSE;

    elevate_thread_if_needed();

    char write_buf[64];
    snprintf(write_buf, sizeof(write_buf), "%s\n", gov);
    size_t write_len = strlen(write_buf);

    int success = 0;
    char path[128];
    for (int i = 0; i < 16; i++) {
        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpufreq/policy%d/scaling_governor", i);
        int fd = open(path, O_WRONLY);
        if (fd >= 0) {
            if (write(fd, write_buf, write_len) > 0) {
                success = 1;
            }
            close(fd);
        }
    }

    (*env)->ReleaseStringUTFChars(env, gov_jstr, gov);
    return success ? JNI_TRUE : JNI_FALSE;
}
