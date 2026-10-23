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
#include <sys/wait.h>
#include <sys/stat.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <errno.h>

#define LOG_TAG "SuiKernelIoctl"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define CMD_SUI_ELEVATE_ROOT 0x53554901
#define SUI_ELEVATE_MAGIC 0x59414D4144415355ULL

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

// Tests whether the kernel supports native root elevation by testing in a child process
JNIEXPORT jboolean JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_hasNativeRoot(JNIEnv *env, jobject thiz) {
    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed in hasNativeRoot: %d", errno);
        return JNI_FALSE;
    }

    if (pid == 0) {
        // Child process: test escalation
        int fd = open("/dev/null", O_RDWR);
        if (fd < 0) {
            _exit(1);
        }
        int ret = ioctl(fd, CMD_SUI_ELEVATE_ROOT, SUI_ELEVATE_MAGIC);
        close(fd);
        if (ret == 0 && (getuid() == 0 || geteuid() == 0)) {
            _exit(0);
        }
        _exit(2);
    }

    int status = 0;
    waitpid(pid, &status, 0);
    return (WIFEXITED(status) && WEXITSTATUS(status) == 0) ? JNI_TRUE : JNI_FALSE;
}

// Executes a shell command with native root in a child process
JNIEXPORT jint JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_nativeRunCommand(JNIEnv *env, jobject thiz, jstring cmd_jstr) {
    if (!cmd_jstr) return -1;
    const char *cmd = (*env)->GetStringUTFChars(env, cmd_jstr, NULL);
    if (!cmd) return -1;

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed in nativeRunCommand: %d", errno);
        (*env)->ReleaseStringUTFChars(env, cmd_jstr, cmd);
        return -1;
    }

    if (pid == 0) {
        // Child process
        int fd = open("/dev/null", O_RDWR);
        if (fd >= 0) {
            ioctl(fd, CMD_SUI_ELEVATE_ROOT, SUI_ELEVATE_MAGIC);
            close(fd);
        }
        execl("/system/bin/sh", "sh", "-c", cmd, (char *)NULL);
        _exit(127);
    }

    (*env)->ReleaseStringUTFChars(env, cmd_jstr, cmd);
    int status = 0;
    waitpid(pid, &status, 0);
    return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

// Executes a shell command with native root in a child process and captures stdout/stderr
JNIEXPORT jstring JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_nativeRunCommandWithOutput(JNIEnv *env, jobject thiz, jstring cmd_jstr) {
    if (!cmd_jstr) return NULL;
    const char *cmd = (*env)->GetStringUTFChars(env, cmd_jstr, NULL);
    if (!cmd) return NULL;

    int pipefd[2];
    if (pipe(pipefd) < 0) {
        LOGE("pipe failed: %d", errno);
        (*env)->ReleaseStringUTFChars(env, cmd_jstr, cmd);
        return NULL;
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %d", errno);
        close(pipefd[0]);
        close(pipefd[1]);
        (*env)->ReleaseStringUTFChars(env, cmd_jstr, cmd);
        return NULL;
    }

    if (pid == 0) {
        // Child process
        close(pipefd[0]); // close read end
        dup2(pipefd[1], STDOUT_FILENO);
        dup2(pipefd[1], STDERR_FILENO);
        close(pipefd[1]);

        int fd = open("/dev/null", O_RDWR);
        if (fd >= 0) {
            ioctl(fd, CMD_SUI_ELEVATE_ROOT, SUI_ELEVATE_MAGIC);
            close(fd);
        }

        execl("/system/bin/sh", "sh", "-c", cmd, (char *)NULL);
        _exit(127);
    }

    // Parent process
    close(pipefd[1]); // close write end
    (*env)->ReleaseStringUTFChars(env, cmd_jstr, cmd);

    char buffer[4096];
    size_t capacity = 4096;
    size_t size = 0;
    char *output = (char *)malloc(capacity);
    if (!output) {
        close(pipefd[0]);
        waitpid(pid, NULL, 0);
        return NULL;
    }

    ssize_t bytes_read;
    while ((bytes_read = read(pipefd[0], buffer, sizeof(buffer))) > 0) {
        if (size + bytes_read + 1 > capacity) {
            capacity = (size + bytes_read + 1) * 2;
            char *new_output = (char *)realloc(output, capacity);
            if (!new_output) {
                break;
            }
            output = new_output;
        }
        memcpy(output + size, buffer, bytes_read);
        size += bytes_read;
    }
    output[size] = '\0';
    close(pipefd[0]);

    int status = 0;
    waitpid(pid, &status, 0);

    jstring result = (*env)->NewStringUTF(env, output);
    free(output);
    return result;
}

// Writes a single configuration file directly to /data/SuiKernel/<feature>
JNIEXPORT jboolean JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_nativeWriteFeature(
    JNIEnv *env, jobject thiz, jstring name_jstr, jstring val_jstr) {
    if (!name_jstr || !val_jstr) return JNI_FALSE;
    const char *name = (*env)->GetStringUTFChars(env, name_jstr, NULL);
    const char *val = (*env)->GetStringUTFChars(env, val_jstr, NULL);
    if (!name || !val) return JNI_FALSE;

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed in nativeWriteFeature: %d", errno);
        (*env)->ReleaseStringUTFChars(env, name_jstr, name);
        (*env)->ReleaseStringUTFChars(env, val_jstr, val);
        return JNI_FALSE;
    }

    if (pid == 0) {
        // Child process: elevate to root
        int fd = open("/dev/null", O_RDWR);
        if (fd >= 0) {
            int ret = ioctl(fd, CMD_SUI_ELEVATE_ROOT, SUI_ELEVATE_MAGIC);
            if (ret != 0) {
                LOGE("CMD_SUI_ELEVATE_ROOT failed: ret=%d errno=%d uid=%d", ret, errno, getuid());
            } else {
                LOGI("Elevated to root successfully (uid=%d euid=%d)", getuid(), geteuid());
            }
            close(fd);
        }

        mkdir("/data/SuiKernel", 0755);
        chmod("/data/SuiKernel", 0755);

        char path[128];
        snprintf(path, sizeof(path), "/data/SuiKernel/%s", name);
        int out_fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (out_fd >= 0) {
            write(out_fd, val, strlen(val));
            fdatasync(out_fd);
            close(out_fd);
            chmod(path, 0644);

            int dir_fd = open("/data/SuiKernel", O_RDONLY | O_DIRECTORY);
            if (dir_fd >= 0) {
                fsync(dir_fd);
                close(dir_fd);
            }
            LOGI("Successfully wrote /data/SuiKernel/%s = %s", name, val);
            _exit(0);
        }
        LOGE("Failed to open %s for write: %d (uid=%d euid=%d)", path, errno, getuid(), geteuid());
        _exit(1);
    }

    (*env)->ReleaseStringUTFChars(env, name_jstr, name);
    (*env)->ReleaseStringUTFChars(env, val_jstr, val);

    int status = 0;
    waitpid(pid, &status, 0);
    return (WIFEXITED(status) && WEXITSTATUS(status) == 0) ? JNI_TRUE : JNI_FALSE;
}

// Directly writes configuration files to /data/SuiKernel/ without spawning /system/bin/sh or toybox
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

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed in nativeWriteStorage: %d", errno);
        if (anya) (*env)->ReleaseStringUTFChars(env, anya, c_anya);
        if (touch_boost) (*env)->ReleaseStringUTFChars(env, touch_boost, c_touch);
        if (inaho) (*env)->ReleaseStringUTFChars(env, inaho, c_inaho);
        if (tenebrion) (*env)->ReleaseStringUTFChars(env, tenebrion, c_tenebrion);
        if (airani) (*env)->ReleaseStringUTFChars(env, airani, c_airani);
        if (sandevistan) (*env)->ReleaseStringUTFChars(env, sandevistan, c_sandevistan);
        if (moona) (*env)->ReleaseStringUTFChars(env, moona, c_moona);
        if (sparxie) (*env)->ReleaseStringUTFChars(env, sparxie, c_sparxie);
        if (swappiness) (*env)->ReleaseStringUTFChars(env, swappiness, c_swappiness);
        return JNI_FALSE;
    }

    if (pid == 0) {
        // Child process: elevate to root
        int fd = open("/dev/null", O_RDWR);
        if (fd >= 0) {
            int ret = ioctl(fd, CMD_SUI_ELEVATE_ROOT, SUI_ELEVATE_MAGIC);
            if (ret != 0) {
                LOGE("nativeWriteStorage elevation failed: ret=%d errno=%d uid=%d", ret, errno, getuid());
            }
            close(fd);
        }

        mkdir("/data/SuiKernel", 0755);
        chmod("/data/SuiKernel", 0755);

        const char *names[] = {
            "anya_thermal", "touch_boost", "inaho", "tenebrion",
            "airani", "sandevistan", "moona_zram", "sparxie", "swappiness"
        };
        const char *values[] = {
            c_anya, c_touch, c_inaho, c_tenebrion,
            c_airani, c_sandevistan, c_moona, c_sparxie, c_swappiness
        };

        for (int i = 0; i < 9; i++) {
            char path[128];
            snprintf(path, sizeof(path), "/data/SuiKernel/%s", names[i]);
            int out_fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
            if (out_fd >= 0) {
                if (values[i]) {
                    write(out_fd, values[i], strlen(values[i]));
                }
                fdatasync(out_fd);
                close(out_fd);
                chmod(path, 0644);
            } else {
                LOGE("Failed to open %s for write: %d", path, errno);
            }
        }

        // Sync directory to commit new directory entries to disk immediately
        int dir_fd = open("/data/SuiKernel", O_RDONLY | O_DIRECTORY);
        if (dir_fd >= 0) {
            fsync(dir_fd);
            close(dir_fd);
        }
        _exit(0);
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

    int status = 0;
    waitpid(pid, &status, 0);
    return (WIFEXITED(status) && WEXITSTATUS(status) == 0) ? JNI_TRUE : JNI_FALSE;
}

// Directly reads configuration files from /data/SuiKernel/ without spawning /system/bin/sh or toybox
JNIEXPORT jstring JNICALL
Java_kanagawa_yamada_suikernel_manager_SuiKernelIoctl_nativeReadStorage(JNIEnv *env, jobject thiz) {
    int pipefd[2];
    if (pipe(pipefd) < 0) {
        LOGE("pipe failed in nativeReadStorage: %d", errno);
        return NULL;
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed in nativeReadStorage: %d", errno);
        close(pipefd[0]);
        close(pipefd[1]);
        return NULL;
    }

    if (pid == 0) {
        // Child process: elevate and directly read files
        close(pipefd[0]); // close read end
        int fd = open("/dev/null", O_RDWR);
        if (fd >= 0) {
            int ret = ioctl(fd, CMD_SUI_ELEVATE_ROOT, SUI_ELEVATE_MAGIC);
            if (ret != 0) {
                LOGE("nativeReadStorage elevation failed: ret=%d errno=%d uid=%d", ret, errno, getuid());
            }
            close(fd);
        }

        const char *names[] = {
            "anya_thermal", "touch_boost", "inaho", "tenebrion",
            "airani", "sandevistan", "moona_zram", "sparxie", "swappiness"
        };

        for (int i = 0; i < 9; i++) {
            char path[128];
            snprintf(path, sizeof(path), "/data/SuiKernel/%s", names[i]);
            int in_fd = open(path, O_RDONLY);
            if (in_fd >= 0) {
                char val[32] = {0};
                ssize_t n = read(in_fd, val, sizeof(val) - 1);
                close(in_fd);
                if (n > 0) {
                    val[n] = '\0';
                    while (n > 0 && (val[n - 1] == '\n' || val[n - 1] == '\r' || val[n - 1] == ' ' || val[n - 1] == '\t')) {
                        val[--n] = '\0';
                    }
                    char line[64];
                    int len = snprintf(line, sizeof(line), "%s=%s\n", names[i], val);
                    write(pipefd[1], line, len);
                }
            }
        }
        close(pipefd[1]);
        _exit(0);
    }

    // Parent process
    close(pipefd[1]); // close write end

    char buffer[1024];
    size_t capacity = 1024;
    size_t size = 0;
    char *output = (char *)malloc(capacity);
    if (!output) {
        close(pipefd[0]);
        waitpid(pid, NULL, 0);
        return NULL;
    }

    ssize_t bytes_read;
    while ((bytes_read = read(pipefd[0], buffer, sizeof(buffer))) > 0) {
        if (size + bytes_read + 1 > capacity) {
            capacity = (size + bytes_read + 1) * 2;
            char *new_output = (char *)realloc(output, capacity);
            if (!new_output) {
                break;
            }
            output = new_output;
        }
        memcpy(output + size, buffer, bytes_read);
        size += bytes_read;
    }
    output[size] = '\0';
    close(pipefd[0]);

    int status = 0;
    waitpid(pid, &status, 0);

    jstring result = (*env)->NewStringUTF(env, output);
    free(output);
    return result;
}


