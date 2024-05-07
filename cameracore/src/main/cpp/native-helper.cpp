#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/hardware_buffer_jni.h>

#define LOG_TAG "CAMERA_NATIVE_HELPER"
#define LOG(severity, ...) ((void)__android_log_print(ANDROID_LOG_##severity, LOG_TAG, __VA_ARGS__))
#define LOGE(...) LOG(ERROR, __VA_ARGS__)

extern "C"
JNIEXPORT void JNICALL
Java_com_sll_cameracore_NativeHelper_formatNV21Data(JNIEnv *env, jobject thiz, jobject y_buffer,
                                                    jobject u_buffer, jobject v_buffer, jint width,
                                                    jint height, jint y_stride,
                                                    jint uv_pixel_stride, jbyteArray dst) {

    auto *yBuffer = static_cast<unsigned char *>(env->GetDirectBufferAddress(y_buffer));
    auto *uBuffer = static_cast<unsigned char *>(env->GetDirectBufferAddress(u_buffer));
    auto *vBuffer = static_cast<unsigned char *>(env->GetDirectBufferAddress(v_buffer));
    jlong uLength = env->GetDirectBufferCapacity(u_buffer);

    auto *p_input = (unsigned char *) env->GetByteArrayElements(dst, nullptr);
    auto *p_dst = p_input;
    unsigned char *p_gy = yBuffer;
    for (int i = 0; i < height; ++i) {
        memcpy(p_dst, p_gy, width);
        p_dst += width;
        p_gy += y_stride;
    }

    int index = 0;
    auto *vu = p_input + width * height;
    for (int row = 0; row < height / 2; row++) {
        for (int col = 0; col < width / 2; col++) {
            int vuPos = col * uv_pixel_stride + row * y_stride;
            if (vuPos >= uLength) break;
            vu[index++] = vBuffer[vuPos];
            vu[index++] = uBuffer[vuPos];
        }
    }
    env->ReleaseByteArrayElements(dst, (jbyte *) p_input, 0);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_sll_cameracore_NativeHelper_nativeGetBytesFromHardwareBuffer(JNIEnv *env, jobject thiz,
                                                                      jobject buffer,
                                                                      jbyteArray bytes) {

    auto hardwareBuffer = AHardwareBuffer_fromHardwareBuffer(env, buffer);
    unsigned char *ptrRead;
    int rc = AHardwareBuffer_lock(hardwareBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1, nullptr,
                                  reinterpret_cast<void **>(&ptrRead));

    if (rc < 0 || ptrRead == nullptr) {
        LOGE("nativeGetBytesFromHardwareBuffer failed: %s (%d)", strerror(-rc), -rc);
        AHardwareBuffer_unlock(hardwareBuffer, nullptr);
        return;
    }

    auto *output = reinterpret_cast<unsigned char *>(env->GetByteArrayElements(bytes, nullptr));

    AHardwareBuffer_Desc bufferDesc;
    AHardwareBuffer_describe(hardwareBuffer, &bufferDesc);

    auto ySize = bufferDesc.width * bufferDesc.height;
    auto uvSize = (bufferDesc.width * bufferDesc.height) >> 1;

    unsigned char *ptrUV;
    // copy y and remove stride
    auto inYPlan = ptrRead;
    auto outYPlan = output;
    for (auto i = 0; i < bufferDesc.height; ++i) {
        memcpy(outYPlan, inYPlan, bufferDesc.width);
        outYPlan += bufferDesc.width;
        inYPlan += bufferDesc.stride;
    }

    // hardware buffer, use 32-bit alignment, maybe 64-bit alignment in the future
    auto remainder = bufferDesc.height % 32;
    auto padding = 0;
    if (remainder != 0) {
        padding = (32 - remainder) * bufferDesc.stride;
    }
    auto inUVPlan = inYPlan + padding;
    auto outUVPlan = outYPlan;

    // copy uv and remove stride
    for (auto i = 0; i < (bufferDesc.height >> 1); ++i) {
        memcpy(outUVPlan, inUVPlan, bufferDesc.width);
        outUVPlan += bufferDesc.width;
        inUVPlan += bufferDesc.stride;
    }
    ptrUV = output + ySize;
    // nv12 -> nv21
    unsigned char tmp;
    for (int i = 0; i < uvSize; i += 2) {
        tmp = *(ptrUV + i);
        *(ptrUV + i) = *(ptrUV + i + 1);
        *(ptrUV + i + 1) = tmp;
    }

    AHardwareBuffer_unlock(hardwareBuffer, nullptr);
    env->ReleaseByteArrayElements(bytes, reinterpret_cast<jbyte *>(output), 0);
}