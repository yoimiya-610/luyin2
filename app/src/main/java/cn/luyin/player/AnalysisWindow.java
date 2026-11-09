package cn.luyin.player;

import java.util.List;
import java.util.Locale;

/** Collects one bounded window of decoded audio, in file-relative time. */
final class AnalysisWindow {
    static final long LENGTH_US = 12_000_000;
    private static final int PEAKS = 120;
    private final AcousticAnalyzer analyzer = new AcousticAnalyzer();
    private final short[] mono = new short[1024];
    private final float[] peaks = new float[PEAKS];
    private final long startUs, limitUs;
    private long endUs, segment;
    private boolean hasAudio;

    AnalysisWindow(long startUs, long durationUs) {
        this.startUs = startUs;
        endUs = startUs;
        limitUs = durationUs > startUs ? Math.min(startUs + LENGTH_US, durationUs) : startUs + LENGTH_US;
    }

    interface Samples {
        short read();
    }

    /** The source supplies interleaved channels and the timestamp of the first sample frame. */
    void accept(Samples samples, int frames, int channels, int rate, long timeUs) throws InterruptedException {
        if (rate < 4000 || channels < 1) throw new IllegalArgumentException("Invalid audio format");
        int first = sampleIndex(startUs - timeUs, rate, frames);
        int last = sampleIndex(limitUs - timeUs, rate, frames);
        if (last <= first) return;
        long firstUs = timeUs + first * 1_000_000L / rate;
        if (hasAudio && Math.abs(firstUs - endUs) > 5_000) segment++;
        for (int i = 0; i < first * channels; i++) samples.read();
        for (int from = first; from < last; ) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            int count = Math.min(mono.length, last - from);
            for (int i = 0; i < count; i++) {
                int sum = 0;
                for (int channel = 0; channel < channels; channel++) sum += samples.read();
                mono[i] = (short) (sum / channels);
                long atUs = timeUs + (from + i) * 1_000_000L / rate;
                int bin = (int) Math.max(0, Math.min(PEAKS - 1,
                        (atUs - startUs) * PEAKS / (limitUs - startUs)));
                peaks[bin] = Math.max(peaks[bin], Math.abs(mono[i]) / 32768f);
            }
            from += count;
            endUs = timeUs + from * 1_000_000L / rate;
            analyzer.accept(mono, count, rate, endUs / 1_000_000.0, segment, true);
            hasAudio = true;
        }
    }

    private static int sampleIndex(long deltaUs, int rate, int frames) {
        if (deltaUs <= 0) return 0;
        if (deltaUs >= (frames * 1_000_000L + rate - 1) / rate) return frames;
        return (int) ((deltaUs * rate + 999_999) / 1_000_000);
    }

    boolean hasAudio() { return hasAudio; }
    long limitUs() { return limitUs; }

    Result finish(long durationUs, boolean eof) {
        long end = eof ? Math.min(limitUs, endUs) : limitUs;
        long duration = eof ? end : durationUs;
        // At EOF an audio track can end before its container's declared duration.
        // Keep the same axis used for the waveform bins.
        if (eof && end > startUs && end < limitUs) {
            int bins = Math.max(1, (int) Math.ceil((end - startUs) * PEAKS / (double) (limitUs - startUs)));
            return new Result(startUs, end, duration, java.util.Arrays.copyOf(peaks, bins), analyzer.history());
        }
        return new Result(startUs, end, duration, peaks, analyzer.history());
    }

    static String time(double seconds) {
        long total = Math.max(0, Math.round(seconds * 10));
        long s = total / 10;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d.%d", s / 3600, s / 60 % 60, s % 60, total % 10)
                : String.format(Locale.ROOT, "%02d:%02d.%d", s / 60, s % 60, total % 10);
    }

    static final class Result {
        final long startUs, endUs, durationUs;
        final float[] peaks;
        final List<SoundFrame> frames;

        Result(long startUs, long endUs, long durationUs, float[] peaks, List<SoundFrame> frames) {
            this.startUs = startUs;
            this.endUs = endUs;
            this.durationUs = durationUs;
            this.peaks = peaks;
            this.frames = frames;
        }

        SoundFrame frameAt(double seconds) {
            SoundFrame best = null;
            double distance = .10;
            for (SoundFrame frame : frames) {
                double delta = Math.abs(frame.seconds - seconds);
                if (delta <= distance) {
                    best = frame;
                    distance = delta;
                }
            }
            return best;
        }
    }
}
