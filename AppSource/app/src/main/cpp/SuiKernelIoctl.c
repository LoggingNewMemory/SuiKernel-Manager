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

