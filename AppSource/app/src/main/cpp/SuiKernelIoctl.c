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
#include <sys/stat.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <errno.h>
#include <dirent.h>
#include <signal.h>

#define LOG_TAG "SuiKernelIoctl"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define CMD_SUI_ELEVATE_ROOT 0x53554901
#define SUI_ELEVATE_MAGIC 0x59414D4144415355ULL

// Terminates any stale/orphaned instances of the manager from previous installations/runs
// Reads /proc/<pid>/comm (never cmdline) to avoid acquiring mm->mmap_sem and deadlocking on hung tasks
static void kill_stale_manager_instances(void) {
    pid_t my_pid = getpid();
    DIR *proc = opendir("/proc");
    if (!proc) return;
    struct dirent *entry;
    while ((entry = readdir(proc)) != NULL) {
        if (entry->d_name[0] >= '0' && entry->d_name[0] <= '9') {
            pid_t pid = atoi(entry->d_name);
            if (pid > 0 && pid != my_pid) {
                char comm_path[64];
                snprintf(comm_path, sizeof(comm_path), "/proc/%d/comm", pid);
                int fd = open(comm_path, O_RDONLY);
                if (fd >= 0) {
                    char comm[32] = {0};
                    ssize_t n = read(fd, comm, sizeof(comm) - 1);
                    close(fd);
                    if (n > 0 && (strstr(comm, "yamada") || strstr(comm, "uikernel") || strstr(comm, "SuiKernel"))) {
                        LOGI("Terminating stale root manager PID %d (comm=%s)", pid, comm);
                        kill(pid, SIGKILL);
                    }
                }
            }
        }
    }
    closedir(proc);
}

// Probe whether the kernel supports Yamada SU without altering thread credentials
static int probe_kernel_has_sui(void) {
    int fd = open("/dev/null", O_RDWR);
    if (fd < 0) return 0;
    errno = 0;
    int ret = ioctl(fd, CMD_SUI_ELEVATE_ROOT, 0ULL);
    int err = errno;
    close(fd);
    return (ret == -EINVAL || err == EINVAL) ? 1 : 0;
}

// Tries to elevate the calling thread to true root via Yamada SU kernel ioctl
static int elevate_thread_if_needed(void) {
    if (getuid() == 0) return 0;
    // Protect the UI thread from having its UID changed.
    // Changing the UI thread's UID breaks Android's InputMethodManager (keyboard),
    // WindowManager, and ViewRootImpl due to UID mismatch with system_server.
    if (gettid() == getpid()) return -1;

    int fd = open("/dev/null", O_RDWR);
    if (fd < 0) return -1;
    int ret = ioctl(fd, CMD_SUI_ELEVATE_ROOT, SUI_ELEVATE_MAGIC);
    close(fd);
    return ret;
}

JNIEXPORT jboolean JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_nativeElevateThread(JNIEnv *env, jobject thiz) {
    int ret = elevate_thread_if_needed();
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_nativeKillStaleInstances(JNIEnv *env, jobject thiz) {
    elevate_thread_if_needed();
    kill_stale_manager_instances();
}

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
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_nativeSendIoctl(JNIEnv *env, jobject thiz, jlong cmd, jlong arg) {
    return Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_sendIoctl(env, thiz, cmd, arg);
}

// Tests whether the kernel supports native root elevation without using fork
JNIEXPORT jboolean JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_hasNativeRoot(JNIEnv *env, jobject thiz) {
    if (getuid() == 0) return JNI_TRUE;
    if (probe_kernel_has_sui()) return JNI_TRUE;
    int ret = elevate_thread_if_needed();
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

// Direct reading of /proc/version
JNIEXPORT jstring JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_nativeReadProcVersion(JNIEnv *env, jobject thiz) {
    int fd = open("/proc/version", O_RDONLY);
    if (fd < 0) {
        elevate_thread_if_needed();
        fd = open("/proc/version", O_RDONLY);
    }
    if (fd >= 0) {
        char buf[512] = {0};
        ssize_t n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (n > 0) {
            buf[n] = '\0';
            return (*env)->NewStringUTF(env, buf);
        }
    }
    return NULL;
}

// Direct reading of /proc/suikernel_manager_version
JNIEXPORT jstring JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_nativeReadManagerVersion(JNIEnv *env, jobject thiz) {
    int fd = open("/proc/suikernel_manager_version", O_RDONLY);
    if (fd < 0) {
        elevate_thread_if_needed();
        fd = open("/proc/suikernel_manager_version", O_RDONLY);
    }
    if (fd >= 0) {
        char buf[64] = {0};
        ssize_t n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (n > 0) {
            buf[n] = '\0';
            return (*env)->NewStringUTF(env, buf);
        }
    }
    return NULL;
}

// Writes a single configuration file directly to /data/SuiKernel/<feature>
JNIEXPORT jboolean JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_nativeWriteFeature(
    JNIEnv *env, jobject thiz, jstring name_jstr, jstring val_jstr) {
    if (!name_jstr || !val_jstr) return JNI_FALSE;
    const char *name = (*env)->GetStringUTFChars(env, name_jstr, NULL);
    const char *val = (*env)->GetStringUTFChars(env, val_jstr, NULL);
    if (!name || !val) {
        if (name) (*env)->ReleaseStringUTFChars(env, name_jstr, name);
        if (val) (*env)->ReleaseStringUTFChars(env, val_jstr, val);
        return JNI_FALSE;
    }

    elevate_thread_if_needed();
    mkdir("/data/SuiKernel", 0755);
    chmod("/data/SuiKernel", 0755);

    char filepath[256];
    snprintf(filepath, sizeof(filepath), "/data/SuiKernel/%s", name);
    int fd = open(filepath, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    jboolean success = JNI_FALSE;
    if (fd >= 0) {
        write(fd, val, strlen(val));
        close(fd);
        chmod(filepath, 0644);
        success = JNI_TRUE;
    }

    (*env)->ReleaseStringUTFChars(env, name_jstr, name);
    (*env)->ReleaseStringUTFChars(env, val_jstr, val);
    return success;
}

// Writes all storage files directly to /data/SuiKernel/
JNIEXPORT jboolean JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_nativeWriteStorage(
    JNIEnv *env, jobject thiz,
    jstring anya, jstring touch_boost, jstring inaho, jstring tenebrion,
    jstring airani, jstring sandevistan, jstring moona, jstring sparxie, jstring swappiness) {

    const char *c_anya = anya ? (*env)->GetStringUTFChars(env, anya, NULL) : "1";
    const char *c_touch = touch_boost ? (*env)->GetStringUTFChars(env, touch_boost, NULL) : "1";
    const char *c_inaho = inaho ? (*env)->GetStringUTFChars(env, inaho, NULL) : "1";
    const char *c_tenebrion = tenebrion ? (*env)->GetStringUTFChars(env, tenebrion, NULL) : "1";
    const char *c_airani = airani ? (*env)->GetStringUTFChars(env, airani, NULL) : "1";
    const char *c_sandevistan = sandevistan ? (*env)->GetStringUTFChars(env, sandevistan, NULL) : "0";
    const char *c_moona = moona ? (*env)->GetStringUTFChars(env, moona, NULL) : "1";
    const char *c_sparxie = sparxie ? (*env)->GetStringUTFChars(env, sparxie, NULL) : "1";
    const char *c_swappiness = swappiness ? (*env)->GetStringUTFChars(env, swappiness, NULL) : "30";

    elevate_thread_if_needed();
    mkdir("/data/SuiKernel", 0755);
    chmod("/data/SuiKernel", 0755);

    const char *files[] = {"anya_thermal", "touch_boost", "inaho", "tenebrion", "airani", "sandevistan", "moona_zram", "sparxie", "swappiness"};
    const char *vals[]  = {c_anya, c_touch, c_inaho, c_tenebrion, c_airani, c_sandevistan, c_moona, c_sparxie, c_swappiness};

    int direct_ok = 1;
    for (int i = 0; i < 9; i++) {
        char path[256];
        snprintf(path, sizeof(path), "/data/SuiKernel/%s", files[i]);
        int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (fd >= 0) {
            write(fd, vals[i], strlen(vals[i]));
            close(fd);
            chmod(path, 0644);
        } else {
            direct_ok = 0;
            break;
        }
    }

    if (anya) (*env)->ReleaseStringUTFChars(env, anya, c_anya);
    if (touch_boost) (*env)->ReleaseStringUTFChars(env, touch_boost, c_touch);
    if (inaho) (*env)->ReleaseStringUTFChars(env, inaho, c_inaho);
    if (tenebrion) (*env)->ReleaseStringUTFChars(env, tenebrion, c_tenebrion);
    if (airani) (*env)->ReleaseStringUTFChars(env, airani, c_airani);
    if (sandevistan) (*env)->ReleaseStringUTFChars(env, sandevistan, c_sandevistan);
    if (moona) (*env)->ReleaseStringUTFChars(env, moona, c_moona);
    if (sparxie) (*env)->ReleaseStringUTFChars(env, sparxie, c_sparxie);
    if (swappiness) (*env)->ReleaseStringUTFChars(env, swappiness, c_swappiness);

    return direct_ok ? JNI_TRUE : JNI_FALSE;
}

// Reads configuration files directly from /data/SuiKernel/
JNIEXPORT jstring JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_nativeReadStorage(JNIEnv *env, jobject thiz) {
    elevate_thread_if_needed();
    const char *files[] = {"anya_thermal", "touch_boost", "inaho", "tenebrion", "airani", "sandevistan", "moona_zram", "sparxie", "swappiness"};
    char buf[2048] = {0};
    int found_any = 0;

    for (int i = 0; i < 9; i++) {
        char path[256];
        snprintf(path, sizeof(path), "/data/SuiKernel/%s", files[i]);
        int fd = open(path, O_RDONLY);
        if (fd >= 0) {
            char val[64] = {0};
            ssize_t n = read(fd, val, sizeof(val) - 1);
            close(fd);
            if (n > 0) {
                val[n] = '\0';
                char line[128];
                snprintf(line, sizeof(line), "%s=%s\n", files[i], val);
                strlcat(buf, line, sizeof(buf));
                found_any = 1;
            }
        }
    }

    if (found_any) {
        return (*env)->NewStringUTF(env, buf);
    }
    return NULL;
}
