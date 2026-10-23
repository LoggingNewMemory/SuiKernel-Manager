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

#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <android/log.h>
#include <errno.h>

#define LOG_TAG "SuiKernelIoctl"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

JNIEXPORT jint JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_sendIoctl(JNIEnv *env, jobject thiz, jlong cmd, jlong arg) {
    int fd = open("/dev/null", O_RDWR);
    if (fd < 0) {
        LOGE("Failed to open /dev/null: %d", errno);
        return -1;
    }

    int ret = ioctl(fd, (unsigned int)cmd, (unsigned long)arg);
    if (ret < 0) {
        LOGE("ioctl failed: %d", errno);
    } else {
        LOGI("ioctl %lx with arg %lx succeeded", (unsigned long)cmd, (unsigned long)arg);
    }

    close(fd);
    return ret;
}

JNIEXPORT jint JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_elevateToRoot(JNIEnv *env, jobject thiz) {
    int fd = open("/dev/null", O_RDWR);
    if (fd < 0) {
        LOGE("Failed to open /dev/null for elevate: %d", errno);
        return -1;
    }

    int ret = ioctl(fd, 0x53554901, 0x59414D4144415355ULL);
    if (ret < 0) {
        LOGE("elevateToRoot failed: %d", errno);
    } else {
        LOGI("elevateToRoot succeeded! UID is now: %d", getuid());
    }

    close(fd);
    return ret;
}

JNIEXPORT jboolean JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_isElevated(JNIEnv *env, jobject thiz) {
    return (getuid() == 0) ? JNI_TRUE : JNI_FALSE;
}
