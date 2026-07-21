#include "DarwinFFMpeg.h"

extern "C" {
#if defined(__clang__)
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
#pragma clang diagnostic ignored "-Wnull-arithmetic"
#pragma clang diagnostic ignored "-Wundefined-internal"
#pragma clang diagnostic ignored "-Wmissing-prototypes"
#endif
#include <libavutil/frame.h>
#include <libavutil/pixfmt.h>
#include <libavcodec/avcodec.h>
#if defined(__clang__)
#pragma clang diagnostic pop
#endif
}

#import "ExtractCVPixelBuffer.h"

namespace tgcalls {

static enum AVPixelFormat getDarwinPreferredPixelFormat(__unused AVCodecContext *ctx, __unused const enum AVPixelFormat *pix_fmts) {
    return AV_PIX_FMT_VIDEOTOOLBOX;
}

void setupDarwinVideoDecoding(AVCodecContext *codecContext) {
    return;
    
#if TARGET_IPHONE_SIMULATOR
#else
    if (!codecContext) {
        return;
    }
    av_hwdevice_ctx_create(&codecContext->hw_device_ctx, AV_HWDEVICE_TYPE_VIDEOTOOLBOX, nullptr, nullptr, 0);
    codecContext->get_format = getDarwinPreferredPixelFormat;
#endif
}

webrtc::scoped_refptr<webrtc::VideoFrameBuffer> createDarwinPlatformFrameFromData(AVFrame const *frame) {
    if (!frame) {
        return nullptr;
    }
    if (frame->format == AV_PIX_FMT_VIDEOTOOLBOX && frame->data[3]) {
        return extractCVPixelBuffer((void *)frame->data[3]);
    } else {
        return nullptr;
    }
}

}
