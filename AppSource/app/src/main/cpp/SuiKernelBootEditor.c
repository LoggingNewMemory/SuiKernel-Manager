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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <dirent.h>
#include <stdarg.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/system_properties.h>
#include <linux/fs.h>
#include <limits.h>
#include <android/log.h>

#define LOG_TAG "SuiKernelBootEditor"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define CMD_SUI_ELEVATE_ROOT 0x53554901
#define SUI_ELEVATE_MAGIC 0x59414D4144415355ULL

#ifndef BLKGETSIZE64
#define BLKGETSIZE64 _IOR(0x12, 114, size_t)
#endif

#define CHUNK_SIZE (4 * 1024 * 1024) // 4MB buffer for fast I/O

static int elevate_thread_if_needed(void) {
    if (getuid() == 0) return 0;
    if (gettid() == getpid()) return -1; // protect UI thread

    int fd = open("/dev/null", O_RDWR);
    if (fd < 0) return -1;
    int ret = ioctl(fd, CMD_SUI_ELEVATE_ROOT, SUI_ELEVATE_MAGIC);
    close(fd);
    return ret;
}

static void send_log(JNIEnv *env, jobject callback, jmethodID mid, const char *fmt, ...) {
    if (!callback || !mid) return;
    char buf[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    jstring jstr = (*env)->NewStringUTF(env, buf);
    if (jstr) {
        (*env)->CallVoidMethod(env, callback, mid, jstr);
        (*env)->DeleteLocalRef(env, jstr);
    }
}

static int scan_dev_block(const char *dir_path, const char *target_name, char *out_path, size_t max_len) {
    DIR *d = opendir(dir_path);
    if (!d) return -1;

    struct dirent *ent;
    while ((ent = readdir(d)) != NULL) {
        if (strcmp(ent->d_name, ".") == 0 || strcmp(ent->d_name, "..") == 0) continue;

        char full[512];
        snprintf(full, sizeof(full), "%s/%s", dir_path, ent->d_name);

        if (strcmp(ent->d_name, target_name) == 0) {
            char resolved[PATH_MAX] = {0};
            if (realpath(full, resolved) != NULL) {
                strncpy(out_path, resolved, max_len - 1);
            } else {
                strncpy(out_path, full, max_len - 1);
            }
            out_path[max_len - 1] = '\0';
            closedir(d);
            return 0;
        }

        if (ent->d_type == DT_DIR) {
            if (scan_dev_block(full, target_name, out_path, max_len) == 0) {
                closedir(d);
                return 0;
            }
        }
    }
    closedir(d);
    return -1;
}

static int find_boot_partition(char *out_path, size_t max_len) {
    char slot[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.boot.slot_suffix", slot);

    char candidates[8][256];
    int num_candidates = 0;

    if (slot[0] != '\0') {
        snprintf(candidates[num_candidates++], sizeof(candidates[0]), "/dev/block/by-name/boot%s", slot);
        snprintf(candidates[num_candidates++], sizeof(candidates[0]), "/dev/block/bootdevice/by-name/boot%s", slot);
        snprintf(candidates[num_candidates++], sizeof(candidates[0]), "/dev/block/mapper/boot%s", slot);
    }
    snprintf(candidates[num_candidates++], sizeof(candidates[0]), "/dev/block/by-name/boot");
    snprintf(candidates[num_candidates++], sizeof(candidates[0]), "/dev/block/bootdevice/by-name/boot");

    for (int i = 0; i < num_candidates; i++) {
        if (access(candidates[i], F_OK) == 0) {
            char resolved[PATH_MAX] = {0};
            if (realpath(candidates[i], resolved) != NULL) {
                strncpy(out_path, resolved, max_len - 1);
            } else {
                strncpy(out_path, candidates[i], max_len - 1);
            }
            out_path[max_len - 1] = '\0';
            return 0;
        }
    }

    // Fallback: recursive scan in /dev/block
    char target_name[64];
    if (slot[0] != '\0') {
        snprintf(target_name, sizeof(target_name), "boot%s", slot);
    } else {
        strncpy(target_name, "boot", sizeof(target_name));
    }

    if (scan_dev_block("/dev/block", target_name, out_path, max_len) == 0) {
        return 0;
    }

    return -1;
}

JNIEXPORT jstring JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelBootEditor_nativeGetActiveSlot(
    JNIEnv *env, jobject thiz) {
    char slot[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.boot.slot_suffix", slot);
    return (*env)->NewStringUTF(env, slot);
}

JNIEXPORT jstring JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelBootEditor_nativeFindBootPartition(
    JNIEnv *env, jobject thiz) {
    elevate_thread_if_needed();

    char boot_dev[512] = {0};
    if (find_boot_partition(boot_dev, sizeof(boot_dev)) == 0) {
        return (*env)->NewStringUTF(env, boot_dev);
    }
    return NULL;
}

JNIEXPORT jint JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelBootEditor_nativePullBoot(
    JNIEnv *env, jobject thiz, jstring target_path_jstr, jobject callback) {

    jclass cb_class = callback ? (*env)->GetObjectClass(env, callback) : NULL;
    jmethodID mid = cb_class ? (*env)->GetMethodID(env, cb_class, "onLog", "(Ljava/lang/String;)V") : NULL;

    send_log(env, callback, mid, "=== SuiKernel Native Boot Puller ===");

    elevate_thread_if_needed();

    char slot[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.boot.slot_suffix", slot);
    send_log(env, callback, mid, "[*] Active slot suffix: %s", (slot[0] != '\0') ? slot : "none");

    char boot_dev[512] = {0};
    if (find_boot_partition(boot_dev, sizeof(boot_dev)) != 0) {
        send_log(env, callback, mid, "[!] Error: Boot partition could not be found!");
        return -1;
    }

    send_log(env, callback, mid, "[*] Found boot partition: %s", boot_dev);

    const char *target_path = (*env)->GetStringUTFChars(env, target_path_jstr, NULL);
    if (!target_path) {
        send_log(env, callback, mid, "[!] Error: Invalid destination path!");
        return -2;
    }

    send_log(env, callback, mid, "[*] Destination: %s", target_path);

    // Open source partition
    int src_fd = open(boot_dev, O_RDONLY | O_CLOEXEC);
    if (src_fd < 0) {
        send_log(env, callback, mid, "[!] Error opening %s: %s", boot_dev, strerror(errno));
        (*env)->ReleaseStringUTFChars(env, target_path_jstr, target_path);
        return -3;
    }

    uint64_t total_size = 0;
    if (ioctl(src_fd, BLKGETSIZE64, &total_size) < 0 || total_size == 0) {
        off64_t end = lseek64(src_fd, 0, SEEK_END);
        if (end > 0) total_size = (uint64_t)end;
        lseek64(src_fd, 0, SEEK_SET);
    }

    if (total_size > 0) {
        send_log(env, callback, mid, "[*] Boot partition size: %.2f MB", (double)total_size / (1024.0 * 1024.0));
    }

    // Open target file
    int dst_fd = open(target_path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0664);
    if (dst_fd < 0) {
        send_log(env, callback, mid, "[!] Error opening %s: %s", target_path, strerror(errno));
        close(src_fd);
        (*env)->ReleaseStringUTFChars(env, target_path_jstr, target_path);
        return -4;
    }

    uint8_t *buffer = (uint8_t *)malloc(CHUNK_SIZE);
    if (!buffer) {
        send_log(env, callback, mid, "[!] Error: Out of memory for transfer buffer!");
        close(dst_fd);
        close(src_fd);
        (*env)->ReleaseStringUTFChars(env, target_path_jstr, target_path);
        return -5;
    }

    send_log(env, callback, mid, "[*] Copying partition data in 4MB blocks...");

    uint64_t copied = 0;
    int error_occurred = 0;

    while (1) {
        size_t to_read = CHUNK_SIZE;
        if (total_size > 0 && (copied + to_read > total_size)) {
            to_read = (size_t)(total_size - copied);
        }
        if (to_read == 0) break;

        ssize_t n_read = read(src_fd, buffer, to_read);
        if (n_read <= 0) {
            if (n_read < 0) {
                send_log(env, callback, mid, "[!] Read error: %s", strerror(errno));
                error_occurred = 1;
            }
            break;
        }

        ssize_t n_written = 0;
        while (n_written < n_read) {
            ssize_t w = write(dst_fd, buffer + n_written, n_read - n_written);
            if (w <= 0) {
                send_log(env, callback, mid, "[!] Write error: %s", strerror(errno));
                error_occurred = 1;
                break;
            }
            n_written += w;
        }

        if (error_occurred) break;

        copied += n_written;
        if (total_size > 0) {
            double percent = ((double)copied / (double)total_size) * 100.0;
            send_log(env, callback, mid, "[*] Pulled: %.1f MB / %.1f MB (%.0f%%)",
                     (double)copied / (1024.0 * 1024.0),
                     (double)total_size / (1024.0 * 1024.0),
                     percent);
        }
    }

    fsync(dst_fd);
    close(dst_fd);
    close(src_fd);
    free(buffer);

    chmod(target_path, 0664);
    chown(target_path, 1000, 1000); // Standard Android media uid/gid

    if (!error_occurred) {
        send_log(env, callback, mid, "[+] Successfully pulled boot partition (%.2f MB)!", (double)copied / (1024.0 * 1024.0));
        send_log(env, callback, mid, "[+] Saved location: %s", target_path);
    }

    (*env)->ReleaseStringUTFChars(env, target_path_jstr, target_path);
    return error_occurred ? -6 : 0;
}

JNIEXPORT jint JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelBootEditor_nativeFlashBoot(
    JNIEnv *env, jobject thiz, jstring source_path_jstr, jobject callback) {

    jclass cb_class = callback ? (*env)->GetObjectClass(env, callback) : NULL;
    jmethodID mid = cb_class ? (*env)->GetMethodID(env, cb_class, "onLog", "(Ljava/lang/String;)V") : NULL;

    send_log(env, callback, mid, "=== SuiKernel Native Boot Flasher ===");

    elevate_thread_if_needed();

    char slot[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.boot.slot_suffix", slot);
    send_log(env, callback, mid, "[*] Active slot suffix: %s", (slot[0] != '\0') ? slot : "none");

    char boot_dev[512] = {0};
    if (find_boot_partition(boot_dev, sizeof(boot_dev)) != 0) {
        send_log(env, callback, mid, "[!] Error: Boot partition could not be found!");
        return -1;
    }

    send_log(env, callback, mid, "[*] Target boot block: %s", boot_dev);

    const char *source_path = (*env)->GetStringUTFChars(env, source_path_jstr, NULL);
    if (!source_path) {
        send_log(env, callback, mid, "[!] Error: Invalid source image path!");
        return -2;
    }

    // Open source file
    int src_fd = open(source_path, O_RDONLY | O_CLOEXEC);
    if (src_fd < 0) {
        send_log(env, callback, mid, "[!] Error opening %s: %s", source_path, strerror(errno));
        (*env)->ReleaseStringUTFChars(env, source_path_jstr, source_path);
        return -3;
    }

    struct stat st;
    if (fstat(src_fd, &st) < 0 || st.st_size <= 0) {
        send_log(env, callback, mid, "[!] Error: Source file is empty or invalid!");
        close(src_fd);
        (*env)->ReleaseStringUTFChars(env, source_path_jstr, source_path);
        return -4;
    }

    uint64_t total_size = (uint64_t)st.st_size;
    send_log(env, callback, mid, "[*] Image size to flash: %.2f MB", (double)total_size / (1024.0 * 1024.0));

    // Open destination block device
    int dst_fd = open(boot_dev, O_WRONLY | O_CLOEXEC);
    if (dst_fd < 0) {
        send_log(env, callback, mid, "[!] Error opening %s: %s", boot_dev, strerror(errno));
        close(src_fd);
        (*env)->ReleaseStringUTFChars(env, source_path_jstr, source_path);
        return -5;
    }

    uint8_t *buffer = (uint8_t *)malloc(CHUNK_SIZE);
    if (!buffer) {
        send_log(env, callback, mid, "[!] Error: Out of memory for transfer buffer!");
        close(dst_fd);
        close(src_fd);
        (*env)->ReleaseStringUTFChars(env, source_path_jstr, source_path);
        return -6;
    }

    send_log(env, callback, mid, "[*] Flashing image to partition block...");

    uint64_t copied = 0;
    int error_occurred = 0;

    while (copied < total_size) {
        size_t to_read = CHUNK_SIZE;
        if (copied + to_read > total_size) {
            to_read = (size_t)(total_size - copied);
        }

        ssize_t n_read = read(src_fd, buffer, to_read);
        if (n_read <= 0) {
            if (n_read < 0) {
                send_log(env, callback, mid, "[!] Read error: %s", strerror(errno));
                error_occurred = 1;
            }
            break;
        }

        ssize_t n_written = 0;
        while (n_written < n_read) {
            ssize_t w = write(dst_fd, buffer + n_written, n_read - n_written);
            if (w <= 0) {
                send_log(env, callback, mid, "[!] Write error: %s", strerror(errno));
                error_occurred = 1;
                break;
            }
            n_written += w;
        }

        if (error_occurred) break;

        copied += n_written;
        double percent = ((double)copied / (double)total_size) * 100.0;
        send_log(env, callback, mid, "[*] Flashed: %.1f MB / %.1f MB (%.0f%%)",
                 (double)copied / (1024.0 * 1024.0),
                 (double)total_size / (1024.0 * 1024.0),
                 percent);
    }

    fsync(dst_fd);
    close(dst_fd);
    close(src_fd);
    free(buffer);

    if (!error_occurred) {
        send_log(env, callback, mid, "[+] Boot partition flashed successfully (%.2f MB)!", (double)copied / (1024.0 * 1024.0));
    }

    (*env)->ReleaseStringUTFChars(env, source_path_jstr, source_path);
    return error_occurred ? -7 : 0;
}
