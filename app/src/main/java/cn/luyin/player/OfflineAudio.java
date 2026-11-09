package cn.luyin.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

/** Reads an existing local audio track. No player or microphone is involved. */
final class OfflineAudio {
    static AnalysisWindow.Result analyze(Context context, Uri uri, long startUs, long fallbackMs)
            throws IOException, InterruptedException {
        if (uri == null || !("file".equals(uri.getScheme()) || "content".equals(uri.getScheme()))) {
            throw new IOException("Not a local recording");
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            try (AssetFileDescriptor source = context.getContentResolver().openAssetFileDescriptor(uri, "r")) {
                if (source == null) throw new IOException("Recording unavailable");
                extractor.setDataSource(source);
            }
            MediaFormat track = selectAudio(extractor);
            long origin = extractor.getSampleTime();
            if (origin < 0) throw new IOException("Empty audio track");
            long durationUs = track.getLong(MediaFormat.KEY_DURATION, 0);
            if (durationUs <= 0 && fallbackMs > 0 && fallbackMs <= Long.MAX_VALUE / 1000) {
                durationUs = fallbackMs * 1000;
            }
            if (startUs > 0) {
                // Leave a short decoder preroll for codecs that use preceding frames.
                extractor.seekTo(origin + Math.max(0, startUs - 250_000), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
            AnalysisWindow window = new AnalysisWindow(startUs, durationUs);
            String mime = track.getString(MediaFormat.KEY_MIME);
            boolean eof = MediaFormat.MIMETYPE_AUDIO_RAW.equals(mime)
                    ? readRaw(extractor, window, track, origin)
                    : decode(extractor, window, track, mime, origin);
            if (!window.hasAudio()) throw new IOException("No audio in requested window");
            return window.finish(durationUs, eof);
        } finally {
            extractor.release();
        }
    }

    private static MediaFormat selectAudio(MediaExtractor extractor) throws IOException {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat track = extractor.getTrackFormat(i);
            String mime = track.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                extractor.selectTrack(i);
                return track;
            }
        }
        throw new IOException("No supported audio track");
    }

    private static boolean readRaw(MediaExtractor extractor, AnalysisWindow window, MediaFormat track, long origin)
            throws IOException, InterruptedException {
        ByteBuffer data = ByteBuffer.allocateDirect(64 * 1024);
        PcmFormat format = new PcmFormat(track);
        while (true) {
            checkInterrupted();
            long timestamp = extractor.getSampleTime();
            if (timestamp < 0) return true;
            if (timestamp - origin >= window.limitUs()) return false;
            long size = extractor.getSampleSize();
            if (size > 1024 * 1024) throw new IOException("PCM packet too large");
            if (size > data.capacity()) data = ByteBuffer.allocateDirect((int) size);
            data.clear();
            int read = extractor.readSampleData(data, 0);
            if (read < 0) return true;
            data.position(0).limit(read);
            format.accept(window, data, timestamp - origin);
            if (!extractor.advance()) return true;
        }
    }

    private static boolean decode(MediaExtractor extractor, AnalysisWindow window, MediaFormat track, String mime, long origin)
            throws IOException, InterruptedException {
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        try {
            codec.configure(track, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            PcmFormat pcm = null;
            boolean inputEnded = false;
            long lastProgress = System.nanoTime();
            while (true) {
                checkInterrupted();
                if (!inputEnded) {
                    int input = codec.dequeueInputBuffer(0);
                    if (input >= 0) {
                        inputEnded = feed(extractor, codec, input, origin);
                        lastProgress = System.nanoTime();
                    }
                }
                int output = codec.dequeueOutputBuffer(info, 10_000);
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    pcm = new PcmFormat(codec.getOutputFormat());
                    lastProgress = System.nanoTime();
                } else if (output >= 0) {
                    try {
                        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (info.presentationTimeUs >= window.limitUs()) return false;
                            if (pcm == null) pcm = new PcmFormat(codec.getOutputFormat(output));
                            ByteBuffer buffer = codec.getOutputBuffer(output);
                            if (buffer == null) throw new IOException("Missing decoded PCM");
                            buffer.limit(info.offset + info.size).position(info.offset);
                            pcm.accept(window, buffer, info.presentationTimeUs);
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return true;
                    } finally {
                        codec.releaseOutputBuffer(output, false);
                    }
                    lastProgress = System.nanoTime();
                } else if (System.nanoTime() - lastProgress > TimeUnit.SECONDS.toNanos(5)) {
                    throw new IOException("Audio decoder stalled");
                }
            }
        } finally {
            codec.release();
        }
    }

    private static boolean feed(MediaExtractor extractor, MediaCodec codec, int index, long origin) throws IOException {
        long timestamp = extractor.getSampleTime();
        if (timestamp < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return true;
        }
        ByteBuffer input = codec.getInputBuffer(index);
        int flags = extractor.getSampleFlags();
        if (input == null || (flags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0
                || extractor.getSampleSize() > input.capacity()) {
            throw new IOException("Unsupported audio packet");
        }
        input.clear();
        int size = extractor.readSampleData(input, 0);
        if (size < 0) throw new IOException("Could not read audio packet");
        int codecFlags = (flags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) == 0 ? 0 : MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
        codec.queueInputBuffer(index, 0, size, timestamp - origin, codecFlags);
        extractor.advance();
        return false;
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    static final class PcmFormat {
        private final int rate, channels, encoding, bytesPerSample;

        PcmFormat(MediaFormat format) throws IOException {
            rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            bytesPerSample = switch (encoding) {
                case AudioFormat.ENCODING_PCM_8BIT -> 1;
                case AudioFormat.ENCODING_PCM_16BIT -> 2;
                case AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3;
                case AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4;
                default -> throw new IOException("Unsupported PCM encoding");
            };
            if (rate < 4000 || channels < 1 || channels > 32) throw new IOException("Unsupported PCM format");
        }

        void accept(AnalysisWindow window, ByteBuffer buffer, long timeUs) throws InterruptedException {
            buffer.order(ByteOrder.nativeOrder());
            AnalysisWindow.Samples samples = switch (encoding) {
                case AudioFormat.ENCODING_PCM_8BIT -> () -> (short) (((buffer.get() & 255) - 128) << 8);
                case AudioFormat.ENCODING_PCM_16BIT -> buffer::getShort;
                case AudioFormat.ENCODING_PCM_24BIT_PACKED -> () -> {
                    buffer.get(); // Drop the least significant byte.
                    int low = buffer.get() & 255;
                    return (short) (low | (buffer.get() << 8));
                };
                case AudioFormat.ENCODING_PCM_32BIT -> () -> (short) (buffer.getInt() >> 16);
                default -> () -> (short) Math.max(-32768, Math.min(32767, Math.round(buffer.getFloat() * 32768)));
            };
            window.accept(samples, buffer.remaining() / (channels * bytesPerSample), channels, rate, timeUs);
        }
    }
}
