package com.example.webtts;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Netflix TTS — ban rieng chay song song app Netflix goc.
 * Bo WebView + offset (khong can: xem app goc, can bang nut noi +-1s).
 * Giu nguyen: sub thu cong, 2 giong Google/HoaiMy, nut noi 30%,
 * xoa cache, toc do, volume sub.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_SRT = 1001;
    private static final int REQ_NOTIF = 1002;
    private static final int REQ_OVERLAY = 1003;

    private List<SubtitleCue> subs = new ArrayList<>();

    private TextView tvStatus;
    private TextView tvCue;
    private TextView tvTime;
    /** Gio con lai dem nguoc nhu Netflix. */
    private TextView tvRemain;
    private android.widget.EditText etTotalLen;
    private TextView tvRate;
    private TextView tvTtsVol;
    private TextView tvVoiceBadge;
    private Button btnPlayPause;
    private Button btnFloat;
    private Button btnPrevCue;
    private Button btnNextCue;
    private Button btnSetTotal;
    private Button btnJumpRemain;
    private Button btnVoiceGoogle;
    private Button btnVoiceHoaiMy;
    private Button btnClearCache;
    private int voice = TtsPlaybackService.VOICE_GOOGLE;
    private SeekBar seekPos;
    private SeekBar seekRate;
    private SeekBar seekTtsVol;
    /** true = user dang keo thanh timeline, tam dung tu dong cap nhat. */
    private boolean seeking = false;

    private boolean playing = false;
    private double position = 0;
    private double duration = 0;
    /** Tong thoi luong phim (giay) do user nhap theo Netflix. */
    private double totalLen = 0;
    private float rate = 1.0f;
    private int ttsVol = 100;

    private Timer uiTimer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvCue = findViewById(R.id.tvCue);
        tvTime = findViewById(R.id.tvTime);
        tvRemain = findViewById(R.id.tvRemain);
        etTotalLen = findViewById(R.id.etTotalLen);
        tvRate = findViewById(R.id.tvRate);
        tvTtsVol = findViewById(R.id.tvTtsVol);
        tvVoiceBadge = findViewById(R.id.tvVoiceBadge);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnFloat = findViewById(R.id.btnFloat);
        btnPrevCue = findViewById(R.id.btnPrevCue);
        btnNextCue = findViewById(R.id.btnNextCue);
        btnSetTotal = findViewById(R.id.btnSetTotal);
        btnJumpRemain = findViewById(R.id.btnJumpRemain);
        btnVoiceGoogle = findViewById(R.id.btnVoiceGoogle);
        btnVoiceHoaiMy = findViewById(R.id.btnVoiceHoaiMy);
        btnClearCache = findViewById(R.id.btnClearCache);
        seekPos = findViewById(R.id.seekPos);
        seekRate = findViewById(R.id.seekRate);
        seekTtsVol = findViewById(R.id.seekTtsVol);

        SharedPreferences sp =
                getSharedPreferences("webtts", MODE_PRIVATE);
        rate = sp.getFloat("rate", 1.0f);
        ttsVol = sp.getInt("ttsVol", 100);
        voice = sp.getInt("voice", TtsPlaybackService.VOICE_GOOGLE);
        totalLen = sp.getFloat("totalLen", 0);
        etTotalLen.setText(totalLen > 0 ? fmtHMS(totalLen) : "");

        refreshVoiceUi();
        refreshCacheBtn();
        seekRate.setProgress(Math.round((rate - 0.5f) / 2.0f * 200));
        tvRate.setText(String.format(Locale.US, "%.1fx", rate));
        seekTtsVol.setProgress(ttsVol);
        tvTtsVol.setText(ttsVol + "%");
        refreshPlayBtn();

        findViewById(R.id.btnPickSrt).setOnClickListener(v -> pickSrt());
        btnPlayPause.setOnClickListener(v -> setPlaying(!playing));
        btnFloat.setOnClickListener(v -> ensureFloat());
        btnPrevCue.setOnClickListener(v -> jumpCueUi(-1));
        btnNextCue.setOnClickListener(v -> jumpCueUi(1));
        btnSetTotal.setOnClickListener(v -> saveTotalUi());
        btnJumpRemain.setOnClickListener(v -> jumpRemainDialog());
        btnVoiceGoogle.setOnClickListener(v -> setVoice(
                TtsPlaybackService.VOICE_GOOGLE));
        btnVoiceHoaiMy.setOnClickListener(v -> setVoice(
                TtsPlaybackService.VOICE_HOAIMY));
        btnClearCache.setOnClickListener(v -> clearCacheUi());

        seekPos.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && duration > 0) {
                    // Keo: chi xem truoc gio dich, tha tay moi nhay that.
                    seeking = true;
                    position = duration * p / 1000.0;
                    tvTime.setText(fmtMmSs(position) + " / " + fmtMmSs(duration));
                    int idx = SubtitleParser.findIndex(subs, position);
                    if (idx >= 0 && idx < subs.size()) {
                        tvCue.setText(subs.get(idx).text);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {
                seeking = true;
            }
            @Override public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                if (duration > 0) {
                    TtsPlaybackService.setPosition(position);
                }
                syncUi();
            }
        });
        seekRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                rate = 0.5f + p / 200.0f * 2.0f;
                tvRate.setText(String.format(Locale.US, "%.1fx", rate));
                TtsPlaybackService.setRate(rate);
                sp.edit().putFloat("rate", rate).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        seekTtsVol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                ttsVol = p;
                tvTtsVol.setText(p + "%");
                TtsPlaybackService.setTtsVolume(p);
                sp.edit().putInt("ttsVol", p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        uiTimer = new Timer();
        uiTimer.schedule(new TimerTask() {
            @Override public void run() {
                runOnUiThread(() -> {
                    boolean p = TtsPlaybackService.isPlayingNow();
                    if (p != playing) {
                        playing = p;
                        refreshPlayBtn();
                    }
                    String cue = TtsPlaybackService.currentCueText();
                    if (cue != null && !cue.equals(tvCue.getText().toString())) {
                        tvCue.setText(cue);
                    }
                    tvTime.setText(TtsPlaybackService.timeText());
                    tvRemain.setText(TtsPlaybackService.remainingText());
                    double sp2 = TtsPlaybackService.getPosition();
                    if (duration > 0 && Math.abs(sp2 - position) > 0.6) {
                        position = sp2;
                        syncUi();
                    }
                    // Hien loi HoaiMy (neu co) de user biet tai sao im lang.
                    if (!subs.isEmpty()
                            && TtsPlaybackService.getVoice()
                                    == TtsPlaybackService.VOICE_HOAIMY) {
                        String err = TtsPlaybackService.edgeError();
                        String prog = TtsPlaybackService.edgeProgress();
                        if (err != null && "0 cau".equals(prog)) {
                            String s = "HoaiMy loi: " + err;
                            if (s.length() > 120) s = s.substring(0, 120);
                            if (!s.equals(tvStatus.getText().toString())) {
                                tvStatus.setText(s);
                            }
                        }
                    }
                });
            }
        }, 500, 500);

        askNotif();
    }

    private void pickSrt() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/x-subrip", "text/plain"});
        try {
            startActivityForResult(i, REQ_SRT);
        } catch (Exception e) {
            toast("Khong mo duoc chon file: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SRT && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            loadSrt(data.getData());
        } else if (requestCode == REQ_OVERLAY) {
            if (TtsPlaybackService.canDrawOverlay(this)) {
                TtsPlaybackService.cmd(this, TtsPlaybackService.ACTION_SHOW_FLOAT);
                toast("Da bat nut noi — sang Netflix van dieu khien duoc.");
            }
        }
    }

    private void loadSrt(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            byte[] buf = new byte[256 * 1024];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            in.close();
            String content = sb.toString();
            if (!content.isEmpty() && content.charAt(0) == 0xFEFF) {
                content = content.substring(1);
            }
            List<SubtitleCue> parsed = SubtitleParser.parseSrt(content);
            if (parsed.isEmpty()) {
                toast("SRT khong co phu de hop le.");
                return;
            }
            subs = parsed;
            duration = 0;
            for (SubtitleCue c : subs) duration = Math.max(duration, c.end);
            position = 0;
            ensureService();
            TtsPlaybackService.setSharedSubtitles(subs);
            tvStatus.setText("Da nap " + subs.size() + " cau phu de.");
            toast("Da nap " + subs.size() + " cau. Bam Play roi sang Netflix.");
            refreshCacheBtn();
            syncUi();
        } catch (Exception e) {
            toast("Khong doc duoc SRT: " + e.getMessage());
        }
    }

    private void setPlaying(boolean p) {
        if (p && subs.isEmpty()) {
            toast("Chon file .srt truoc.");
            return;
        }
        if (p && voice == TtsPlaybackService.VOICE_HOAIMY) {
            toast("HoaiMy online: dang tai 35 cau dau, cho chut...");
        }
        playing = p;
        refreshPlayBtn();
        ensureService();
        if (p) TtsPlaybackService.cmd(this, TtsPlaybackService.ACTION_PLAY);
        else TtsPlaybackService.cmd(this, TtsPlaybackService.ACTION_PAUSE);
    }

    private void refreshPlayBtn() {
        if (playing) {
            btnPlayPause.setText("⏸  Pause");
            btnPlayPause.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFA40E26));
        } else {
            btnPlayPause.setText("▶  Play");
            btnPlayPause.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF1F6FEB));
        }
    }

    private void setVoice(int v) {
        voice = v;
        getSharedPreferences("webtts", MODE_PRIVATE)
                .edit().putInt("voice", v).apply();
        ensureService();
        TtsPlaybackService.setVoice(v);
        refreshVoiceUi();
        toast(v == TtsPlaybackService.VOICE_HOAIMY
                ? "Giong HoaiMy (online) — can mang, preload 35 cau."
                : "Giong Google (offline) — khong can mang.");
    }

    private void refreshVoiceUi() {
        boolean hoaimy = voice == TtsPlaybackService.VOICE_HOAIMY;
        tvVoiceBadge.setText(hoaimy ? "HoaiMy" : "Google");
        // Nut dang chon hien xanh, nut kia mo.
        btnVoiceGoogle.setAlpha(hoaimy ? 0.5f : 1.0f);
        btnVoiceHoaiMy.setAlpha(hoaimy ? 1.0f : 0.5f);
    }

    private void clearCacheUi() {
        ensureService();
        int removed = TtsPlaybackService.clearCache();
        refreshCacheBtn();
        toast(removed > 0 ? "Da xoa " + removed + " file cache."
                : "Cache dang trong.");
    }

    private void refreshCacheBtn() {
        if (btnClearCache == null) return;
        try {
            btnClearCache.setText("🗑  " + TtsPlaybackService.cacheText());
        } catch (Exception e) {
            btnClearCache.setText("Xoa cache");
        }
    }

    private void ensureService() {
        Intent i = new Intent(this, TtsPlaybackService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {}
    }

    private void ensureFloat() {
        ensureService();
        if (!TtsPlaybackService.canDrawOverlay(this)) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_OVERLAY);
                toast("Cap quyen 'Hien thi tren ung dung khac' roi quay lai.");
            } catch (Exception e) {
                toast("Khong mo duoc xin quyen: " + e.getMessage());
            }
            return;
        }
        TtsPlaybackService.cmd(this, TtsPlaybackService.ACTION_SHOW_FLOAT);
        toast("Da bat nut noi — sang Netflix van dieu khien duoc.");
    }

    /** Nhan nut Cau truoc/sau: nhay toi dau cue, phan hoi ngay tren UI. */
    private void jumpCueUi(int dir) {
        if (subs.isEmpty()) {
            toast("Chon file .srt truoc.");
            return;
        }
        ensureService();
        TtsPlaybackService.jumpCue(dir);
        position = TtsPlaybackService.getPosition();
        syncUi();
    }

    private static String fmtMmSs(double sec) {
        if (sec < 0) sec = 0;
        int s = (int) sec;
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    /** "1:32:00" / "92:00" de hien tong thoi luong phim da luu. */
    private static String fmtHMS(double sec) {
        if (sec <= 0) return "";
        int s = (int) sec;
        if (s >= 3600) {
            return String.format(Locale.US, "%d:%02d:%02d",
                    s / 3600, (s % 3600) / 60, s % 60);
        }
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    /**
     * Parse tong thoi luong phim: "1:32:00", "92:00", "1h32m", "92m".
     * So tran khong don vi hieu la PHUT (Netflix bao "1h 32m" -> go 92).
     * Tra ve giay, -1 neu sai.
     */
    private static double parseLen(String s) {
        if (s == null) return -1;
        s = s.trim().toLowerCase(Locale.US).replace(" ", "");
        if (s.isEmpty()) return -1;
        try {
            if (s.contains("h") || (s.contains("m") && !s.contains(":"))) {
                double total = 0;
                Matcher mh = Pattern.compile("(\\d+(?:\\.\\d+)?)h").matcher(s);
                if (mh.find()) total += Double.parseDouble(mh.group(1)) * 3600;
                Matcher mm = Pattern.compile("(\\d+(?:\\.\\d+)?)m").matcher(s);
                if (mm.find()) total += Double.parseDouble(mm.group(1)) * 60;
                Matcher ms = Pattern.compile("(\\d+(?:\\.\\d+)?)s").matcher(s);
                if (ms.find()) total += Double.parseDouble(ms.group(1));
                return total > 0 ? total : -1;
            }
            if (s.contains(":")) {
                double total = 0;
                for (String p : s.split(":")) total = total * 60 + Double.parseDouble(p);
                return total;
            }
            return Double.parseDouble(s) * 60;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Parse gio con lai thay tren Netflix: "25:30", "1:05:00", "-25:30". */
    private static double parseRemain(String s) {
        if (s == null) return -1;
        s = s.trim().replace(" ", "");
        if (s.startsWith("-") || s.startsWith("+")) s = s.substring(1);
        if (s.isEmpty()) return -1;
        try {
            if (!s.contains(":")) return Double.parseDouble(s);
            double total = 0;
            for (String p : s.split(":")) total = total * 60 + Double.parseDouble(p);
            return total;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Luu tong thoi luong phim (nhap 1 lan cho moi phim). */
    private void saveTotalUi() {
        double v = parseLen(etTotalLen.getText().toString());
        if (v <= 0) {
            toast("Sai dinh dang. Vd: 1:32:00 hoac 92m");
            return;
        }
        totalLen = v;
        getSharedPreferences("webtts", MODE_PRIVATE)
                .edit().putFloat("totalLen", (float) v).apply();
        ensureService();
        TtsPlaybackService.setTotalLen(v);
        etTotalLen.setText(fmtHMS(v));
        syncUi();
        toast("Da luu tong " + fmtHMS(v)
                + ". Gio so gio CON LAI voi Netflix.");
    }

    /** Nhap con so dem nguoc tren Netflix -> nhay sub toi dung cho. */
    private void jumpRemainDialog() {
        if (subs.isEmpty()) {
            toast("Chon file .srt truoc.");
            return;
        }
        ensureService();
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("vd: 25:30");
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("Gio con lai tren Netflix")
                .setMessage("Nhin dong ho dem nguoc cua Netflix roi go vao, vd 25:30")
                .setView(et)
                .setPositiveButton("Nhay", (d, w) -> {
                    double rem = parseRemain(et.getText().toString());
                    if (rem < 0) {
                        toast("Sai dinh dang gio.");
                        return;
                    }
                    TtsPlaybackService.jumpToRemaining(rem);
                    position = TtsPlaybackService.getPosition();
                    syncUi();
                    toast("Da nhay toi " + fmtMmSs(position)
                            + ". Tinh chinh bang -1s/+1s.");
                })
                .setNegativeButton("Huy", null)
                .show();
    }

    private void syncUi() {
        if (duration > 0 && !seeking) {
            seekPos.setProgress((int) (position / duration * 1000));
        }
        tvTime.setText(TtsPlaybackService.timeText());
        tvRemain.setText(TtsPlaybackService.remainingText());
        int idx = SubtitleParser.findIndex(subs, position);
        if (idx >= 0 && idx < subs.size()) {
            tvCue.setText(subs.get(idx).text);
        }
    }

    private void askNotif() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIF);
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        playing = TtsPlaybackService.isPlayingNow();
        voice = TtsPlaybackService.getVoice();
        position = TtsPlaybackService.getPosition();
        totalLen = getSharedPreferences("webtts", MODE_PRIVATE)
                .getFloat("totalLen", 0);
        if (etTotalLen != null) {
            etTotalLen.setText(totalLen > 0 ? fmtHMS(totalLen) : "");
        }
        refreshPlayBtn();
        refreshVoiceUi();
        refreshCacheBtn();
        syncUi();
    }

    @Override
    protected void onDestroy() {
        if (uiTimer != null) uiTimer.cancel();
        super.onDestroy();
    }
}
