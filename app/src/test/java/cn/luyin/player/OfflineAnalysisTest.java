package cn.luyin.player;

import android.media.AudioFormat;
import android.media.MediaFormat;
import android.os.Looper;
import android.widget.Button;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35})
public class OfflineAnalysisTest {
    @Test public void stereoPcmIsClippedToTheRequestedWindowAndPreservesFileTime() throws Exception {
        int rate = 8000;
        AnalysisWindow window = new AnalysisWindow(12_000_000, 13_500_000);
        OfflineAudio.PcmFormat pcm = new OfflineAudio.PcmFormat(MediaFormat.createAudioFormat("audio/raw", rate, 2));
        ByteBuffer interleaved = ByteBuffer.allocate(rate * 2 * 4).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 2 * rate; i++) {
            double hz = i < rate / 4 ? 220 : i < rate * 7 / 4 ? 523.25 : 660;
            double angle = 2 * Math.PI * hz * i / rate;
            short sample = (short) (16000 * Math.sin(angle) + 4000 * Math.sin(2 * angle));
            interleaved.putShort(sample).putShort((short) 0);
        }
        interleaved.flip();
        pcm.accept(window, interleaved, 11_750_000);
        AnalysisWindow.Result result = window.finish(13_500_000, false);

        assertEquals(12_000_000, result.startUs);
        assertEquals(13_500_000, result.endUs);
        assertEquals(120, result.peaks.length);
        for (float peak : result.peaks) assertTrue(peak > .20f && peak < .31f);
        assertTrue(result.frames.size() > 8);
        assertTrue(result.frames.stream().allMatch(frame -> frame.seconds >= 12 && frame.seconds <= 13.5));
        assertNull(result.frameAt(12));
        SoundFrame selected = result.frameAt(13);
        assertNotNull(selected);
        assertEquals("C5", PitchDetector.note(selected.pitchHz));
        assertEquals(523.25, selected.pitchHz, 2);
        assertEquals(-12, selected.harmonicsDb[1], 2);
        assertTrue(selected.stability.pitchStdCents < 3);
    }

    @Test public void unknownDurationUsesDecodedEofAndKeepsVeryShortWaveform() throws Exception {
        AnalysisWindow window = new AnalysisWindow(0, 0);
        MediaFormat format = MediaFormat.createAudioFormat("audio/raw", 44100, 1);
        format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT);
        ByteBuffer pcm = ByteBuffer.allocate(882 * Float.BYTES).order(ByteOrder.nativeOrder());
        while (pcm.hasRemaining()) pcm.putFloat(-.5f);
        pcm.flip();
        new OfflineAudio.PcmFormat(format).accept(window, pcm, 0);
        AnalysisWindow.Result result = window.finish(0, true);

        assertTrue(window.hasAudio());
        assertEquals(20_000, result.durationUs);
        assertEquals(20_000, result.endUs);
        assertEquals(.5f, result.peaks[0], .0001f);
        assertTrue(result.frames.isEmpty());
    }

    @Test public void pcmAnalysisRespondsToCancellation() {
        AnalysisWindow window = new AnalysisWindow(0, 0);
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> window.accept(() -> (short) 0, 1024, 1, 8000, 0));
            assertFalse(window.hasAudio());
        } finally {
            Thread.interrupted();
        }
    }

    @Test public void unavailableFileShowsRetryWithoutRequestingMicrophone() throws Exception {
        File missing = new File(RuntimeEnvironment.getApplication().getCacheDir(), "missing-analysis-test.wav");
        Recording row = new Recording(android.net.Uri.fromFile(missing).toString(), "未找到的录音", "", SourceRules.APP, 900, 0);
        try (ActivityController<OfflineAnalysisActivity> controller = Robolectric.buildActivity(OfflineAnalysisActivity.class,
                OfflineAnalysisActivity.intent(RuntimeEnvironment.getApplication(), row)).setup()) {
            OfflineAnalysisActivity activity = controller.get();
            ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
            worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Button retry = ReflectionHelpers.getField(activity, "retry");
            assertEquals(android.view.View.VISIBLE, retry.getVisibility());
            assertNull(Shadows.shadowOf(activity).getLastRequestedPermission());
        }
    }
}
