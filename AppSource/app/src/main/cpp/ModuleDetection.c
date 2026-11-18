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
#include <dirent.h>
#include <pthread.h>

#define LOG_TAG "ModuleDetection"
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

struct module_scan_ctx {
    char *buf;
    size_t capacity;
    size_t len;
};

static void scan_modules_dir(struct module_scan_ctx *ctx) {
    elevate_thread_if_needed();

    DIR *d = opendir("/data/adb/modules");
    if (!d) return;

    struct dirent *ent;
    while ((ent = readdir(d)) != NULL) {
        // Skip hidden files/directories (., .., .rw, etc.)
        if (ent->d_name[0] == '.') {
            continue;
        }

        char prop_path[512];
        snprintf(prop_path, sizeof(prop_path), "/data/adb/modules/%s/module.prop", ent->d_name);
        int fd = open(prop_path, O_RDONLY);
        if (fd < 0) {
            continue;
        }

        char dis_path[512];
        snprintf(dis_path, sizeof(dis_path), "/data/adb/modules/%s/disable", ent->d_name);
        int is_disabled = (access(dis_path, F_OK) == 0) ? 1 : 0;

        char rem_path[512];
        snprintf(rem_path, sizeof(rem_path), "/data/adb/modules/%s/remove", ent->d_name);
        int is_remove = (access(rem_path, F_OK) == 0) ? 1 : 0;

        char prop_content[4096] = {0};
        ssize_t n = read(fd, prop_content, sizeof(prop_content) - 1);
        close(fd);
        if (n > 0) {
            prop_content[n] = '\0';
        }

        char header[512];
        int header_len = snprintf(header, sizeof(header),
            "[MODULE]\ndir=%s\ndisabled=%d\nremove=%d\n",
            ent->d_name, is_disabled, is_remove);

        size_t needed = ctx->len + header_len + strlen(prop_content) + 2;
        if (needed >= ctx->capacity) {
            ctx->capacity = needed + 16384;
            char *new_buf = (char *)realloc(ctx->buf, ctx->capacity);
            if (!new_buf) {
                break;
            }
            ctx->buf = new_buf;
        }

        strcat(ctx->buf, header);
        strcat(ctx->buf, prop_content);
        strcat(ctx->buf, "\n");
        ctx->len = strlen(ctx->buf);
    }

    closedir(d);
}

static void *module_scan_worker(void *arg) {
    scan_modules_dir((struct module_scan_ctx *)arg);
    return NULL;
}

// Reads installed module properties directly from /data/adb/modules/ using native C
JNIEXPORT jstring JNICALL
Java_kana_wesley_yamada_suikernel_manager_SuiKernelIoctl_nativeGetInstalledModules(JNIEnv *env, jobject thiz) {
    struct module_scan_ctx ctx;
    ctx.capacity = 32768;
    ctx.buf = (char *)malloc(ctx.capacity);
    if (!ctx.buf) {
        return NULL;
    }
    ctx.buf[0] = '\0';
    ctx.len = 0;

    if (gettid() != getpid()) {
        scan_modules_dir(&ctx);
    } else {
        pthread_t t;
        if (pthread_create(&t, NULL, module_scan_worker, &ctx) == 0) {
            pthread_join(t, NULL);
        }
    }

    jstring result = NULL;
    if (ctx.len > 0) {
        result = (*env)->NewStringUTF(env, ctx.buf);
    }
    free(ctx.buf);
    return result;
}
