package cn.luyin.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public final class OfflineAnalysisActivity extends Activity {
    private static final int BG = 0xfff5f7f4, INK = 0xff193c33, MUTED = 0xff64746a;
    private static final int SEEK_STEPS = 1000;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Future<?> pending;
    private int generation, page, cursorProgress = -1;
    private long durationUs;
    private boolean spectrum = true, dragging, active;
    private AnalysisWindow.Result result;
    private TextView range, state, selection, pitchValue, stability;
    private LinearLayout charts;
    private SeekBar position, cursor;
    private Button previous, next, retry, mode, axis;
    private WaveformView waveform;
    private SpectrogramView spectrogram;
    private PitchTraceView pitch;
    private HarmonicsView harmonics;
    private SharedPreferences settings;

    static Intent intent(Context context, Recording row) {
        return new Intent(context, OfflineAnalysisActivity.class)
                .putExtra("recording_uri", row.uri)
                .putExtra(Intent.EXTRA_TITLE, row.name)
                .putExtra("duration_ms", row.duration);
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        settings = getSharedPreferences("analysis_settings", MODE_PRIVATE);
        long durationMs = getIntent().getLongExtra("duration_ms", 0);
        durationUs = durationMs > 0 && durationMs <= Long.MAX_VALUE / 1000 ? durationMs * 1000 : 0;
        if (saved != null) {
            page = saved.getInt("page");
            cursorProgress = saved.getInt("cursor", -1);
            spectrum = saved.getBoolean("spectrum");
            durationUs = saved.getLong("duration");
        }
        buildPage();
    }

    @Override protected void onStart() {
        super.onStart();
        active = true;
        if (result == null) load();
    }

    @Override protected void onStop() {
        active = false;
        cancel();
        super.onStop();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
        out.putInt("cursor", cursorProgress);
        out.putBoolean("spectrum", spectrum);
        out.putLong("duration", durationUs);
    }

    private void buildPage() {
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        LinearLayout header = row();
        Button back = button("‹ 返回", this::finish);
        back.setContentDescription("返回录音列表");
        header.addView(back);
        header.addView(heading("离线分析", 22));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = column();
        content.setPadding(dp(16), 0, dp(16), dp(24));
        scroll.addView(content);

        TextView title = heading(getIntent().getStringExtra(Intent.EXTRA_TITLE), 18);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(title);
        LinearLayout navigation = card(content);
        range = label(14, INK);
        navigation.addView(range);
        position = slider("定位录音时间窗", navigation, 0);
        position.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar seekBar) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                dragging = false;
                selectPage(seekBar.getProgress());
            }
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                if (dragging) showRange(progress);
                else selectPage(progress);
            }
        });
        LinearLayout paging = row();
        previous = button("上一窗", () -> selectPage(page - 1));
        next = button("下一窗", () -> selectPage(page + 1));
        paging.addView(previous, weighted());
        paging.addView(next, weighted());
        navigation.addView(paging);

        state = label(14, MUTED);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(state);
        retry = button("重试", this::load);
        content.addView(retry);

        charts = column();
        content.addView(charts);
        LinearLayout graphCard = card(charts);
        mode = button("", () -> {
            spectrum = !spectrum;
            updateGraph();
        });
        graphCard.addView(mode, new LinearLayout.LayoutParams(-2, -2));
        int chartHeight = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 240 : 210;
        waveform = new WaveformView(this);
        spectrogram = new SpectrogramView(this);
        darkGraph(waveform);
        darkGraph(spectrogram);
        graphCard.addView(waveform, new LinearLayout.LayoutParams(-1, dp(chartHeight)));
        graphCard.addView(spectrogram, new LinearLayout.LayoutParams(-1, dp(chartHeight)));
        updateGraph();
        selection = label(14, INK);
        selection.setPadding(0, dp(10), 0, 0);
        graphCard.addView(selection);
        cursor = slider("窗内分析游标", graphCard, SEEK_STEPS);
        cursor.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    cursorProgress = progress;
                    showCursor();
                }
            }
        });

        LinearLayout pitchCard = card(charts);
        LinearLayout pitchHeading = row();
        pitchHeading.addView(heading("音高轨迹", 16), weighted());
        axis = button("", () -> {
            settings.edit().putBoolean("pitch_note_axis", !pitch.isNoteAxis()).apply();
            updatePitchAxis();
        });
        pitchHeading.addView(axis);
        pitchCard.addView(pitchHeading);
        pitchValue = label(16, INK);
        pitchCard.addView(pitchValue);
        pitch = new PitchTraceView(this);
        pitchCard.addView(pitch, new LinearLayout.LayoutParams(-1, dp(148)));
        updatePitchAxis();

        LinearLayout harmonicCard = card(charts);
        harmonicCard.addView(heading("泛音 · 相对基频 dB", 16));
        harmonics = new HarmonicsView(this);
        harmonicCard.addView(harmonics, new LinearLayout.LayoutParams(-1, dp(110)));

        LinearLayout stabilityCard = card(charts);
        stabilityCard.addView(heading("声音稳定性 · 游标前最多 5 秒", 16));
        stability = label(14, INK);
        stability.setLineSpacing(dp(5), 1);
        stabilityCard.addView(stability);
        setContentView(root);
    }

    private void selectPage(int target) {
        if (target == page || target < 0 || target > position.getMax()) {
            showRange(page);
            return;
        }
        page = target;
        cursorProgress = -1;
        load();
    }

    private void load() {
        cancel();
        result = null;
        charts.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        state.setText("正在分析…");
        state.setVisibility(View.VISIBLE);
        updateNavigation();
        int request = generation;
        long startUs = startOf(page);
        String uri = getIntent().getStringExtra("recording_uri");
        long fallbackMs = getIntent().getLongExtra("duration_ms", 0);
        pending = worker.submit(() -> {
            try {
                AnalysisWindow.Result value = OfflineAudio.analyze(getApplicationContext(),
                        uri == null ? null : Uri.parse(uri), startUs, fallbackMs);
                runOnUiThread(() -> complete(request, value));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (SecurityException | FileNotFoundException e) {
                runOnUiThread(() -> fail(request, "无法读取录音，请检查文件是否仍在原处或重新授权。"));
            } catch (Exception e) {
                android.util.Log.w("OfflineAnalysis", "Could not analyze window: " + e.getClass().getSimpleName());
                runOnUiThread(() -> fail(request, "这个时间窗解码失败，请重试。"));
            }
        });
    }

    private void cancel() {
        generation++;
        if (pending != null) pending.cancel(true);
        pending = null;
    }

    private void complete(int request, AnalysisWindow.Result value) {
        if (!active || request != generation) return;
        pending = null;
        result = value;
        durationUs = value.durationUs;
        updateNavigation();
        state.setVisibility(View.GONE);
        waveform.setWindow(value);
        spectrogram.setWindow(value);
        pitch.setWindow(value);
        if (cursorProgress < 0) {
            long span = Math.max(1, value.endUs - value.startUs);
            double offset = value.initialCursorSeconds() * 1_000_000 - value.startUs;
            cursorProgress = (int) Math.max(0, Math.min(SEEK_STEPS, Math.round(offset * SEEK_STEPS / span)));
        }
        cursor.setProgress(cursorProgress);
        showCursor();
        charts.setVisibility(View.VISIBLE);
    }

    private void fail(int request, String message) {
        if (!active || request != generation) return;
        pending = null;
        state.setText(message);
        retry.setVisibility(View.VISIBLE);
    }

    private void updateNavigation() {
        int maxPage = durationUs > 0
                ? (int) Math.min(Integer.MAX_VALUE - 1L, (durationUs - 1) / AnalysisWindow.LENGTH_US)
                : page + (result != null ? 1 : 0);
        position.setMax(Math.max(page, maxPage));
        position.setProgress(page);
        position.setEnabled(position.getMax() > 0);
        previous.setEnabled(page > 0);
        next.setEnabled(page < position.getMax());
        showRange(page);
    }

    private static long startOf(int page) {
        return page * AnalysisWindow.LENGTH_US;
    }

    private void showRange(int index) {
        long start = startOf(index);
        long end = result != null && index == page ? result.endUs
                : durationUs > start ? Math.min(start + AnalysisWindow.LENGTH_US, durationUs) : start + AnalysisWindow.LENGTH_US;
        range.setText(durationUs > 0
                ? getString(R.string.analysis_window_range_total, time(start), time(end), time(durationUs))
                : getString(R.string.analysis_window_range, time(start), time(end)));
    }

    private static String time(long us) {
        return AnalysisWindow.time(us / 1_000_000.0);
    }

    private void updateGraph() {
        waveform.setVisibility(spectrum ? View.GONE : View.VISIBLE);
        spectrogram.setVisibility(spectrum ? View.VISIBLE : View.GONE);
        mode.setText(spectrum ? "声谱图 / 切换波形" : "波形 / 切换声谱图");
    }

    private void updatePitchAxis() {
        boolean notes = settings.getBoolean("pitch_note_axis", false);
        pitch.setNoteAxis(notes);
        axis.setText(notes ? "坐标：音名" : "坐标：Hz");
        ViewGroup.LayoutParams size = pitch.getLayoutParams();
        size.height = dp(notes ? 280 : 148);
        pitch.setLayoutParams(size);
    }

    private void showCursor() {
        long at = result.startUs + (result.endUs - result.startUs) * cursorProgress / SEEK_STEPS;
        double seconds = at / 1_000_000.0;
        selection.setText(getString(R.string.analysis_cursor_position, time(at)));
        waveform.setCursor(seconds);
        spectrogram.setCursor(seconds);
        pitch.setCursor(seconds);
        SoundFrame frame = result.frameAt(seconds);
        if (frame == null) pitchValue.setText("此位置没有分析数据");
        else if (frame.pitchHz <= 0) pitchValue.setText("未检测到有效音高");
        else pitchValue.setText(String.format(Locale.ROOT, "%.1f Hz  %s  %+.0f 音分",
                frame.pitchHz, PitchDetector.note(frame.pitchHz), PitchDetector.noteCents(frame.pitchHz)));
        harmonics.setFrame(frame);
        StabilityMetrics metrics = frame == null ? null : frame.stability;
        stability.setText(getString(R.string.analysis_stability_values,
                metric(metrics == null ? Double.NaN : metrics.pitchStdCents, "音分"),
                metric(metrics == null ? Double.NaN : metrics.levelStdDb, "dB"),
                metrics == null || metrics.frames < 8
                        ? "—" : String.format(Locale.ROOT, "%.0f%%", metrics.voicedFraction * 100)));
    }

    private static String metric(double value, String unit) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.1f %s", value, unit) : "—";
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout layout = column();
        layout.setPadding(dp(12), dp(10), dp(12), dp(10));
        layout.setBackground(surface(Color.WHITE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        parent.addView(layout, params);
        return layout;
    }
    private GradientDrawable surface(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(16));
        return background;
    }
    private void darkGraph(View graph) {
        graph.setBackground(surface(0xff102f2a));
        graph.setClipToOutline(true);
    }
    private SeekBar slider(String description, LinearLayout parent, int max) {
        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setContentDescription(description);
        parent.addView(bar, new LinearLayout.LayoutParams(-1, dp(48)));
        return bar;
    }
    private TextView heading(String text, int size) {
        TextView label = label(size, INK);
        label.setText(text);
        label.setTypeface(null, Typeface.BOLD);
        return label;
    }
    private TextView label(int size, int color) {
        TextView label = new TextView(this);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }
    private Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(view -> action.run());
        return button;
    }
}
