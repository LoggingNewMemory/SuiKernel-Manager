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
