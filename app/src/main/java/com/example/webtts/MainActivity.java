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

import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sub TTS — ban rieng chay song song app Netflix goc.
 * Bo WebView + offset (khong can: xem app goc, can bang nut noi +-1s).
 * Giu nguyen: sub thu cong, 2 giong Google/HoaiMy, nut noi 30%,
 * xoa cache, toc do, volume sub.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_SRT = 1001;
    private static final int REQ_NOTIF = 1002;
    private static final int REQ_OVERLAY = 1003;
    private static final int REQ_BACKUP = 1004;
    private static final int REQ_RESTORE = 1005;

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
    private Button btnSubSearch;
    private Button btnSubSettings;
    private Button btnBackup;
    private Button btnRestore;
    private Button btnExit;
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
        btnSubSearch = findViewById(R.id.btnSubSearch);
        btnSubSettings = findViewById(R.id.btnSubSettings);
        btnBackup = findViewById(R.id.btnBackup);
        btnRestore = findViewById(R.id.btnRestore);
        btnExit = findViewById(R.id.btnExit);
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
        btnSubSearch.setOnClickListener(v -> openSubSearch());
        btnSubSettings.setOnClickListener(v -> openSubSettings());
        btnBackup.setOnClickListener(v -> startBackup());
        btnRestore.setOnClickListener(v -> startRestore());
        btnExit.setOnClickListener(v -> exitAppUi());
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
        } else if (requestCode == REQ_BACKUP && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            writeBackup(data.getData());
        } else if (requestCode == REQ_RESTORE && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            readRestore(data.getData());
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

    /** Dung chung cho nap .srt tay va sub tai tu online. */
    private void useLoadedCues(List<SubtitleCue> parsed, String label) {
        subs = parsed;
        duration = 0;
        for (SubtitleCue c : subs) duration = Math.max(duration, c.end);
        position = 0;
        ensureService();
        TtsPlaybackService.setSharedSubtitles(subs);
        tvStatus.setText(label);
        toast(label + ". Bam Play roi sang Netflix.");
        refreshCacheBtn();
        syncUi();
    }

    // ---------- tim + tai sub online (SubDL / OpenSubtitles / SubSource) ----------

    private LinearLayout dialogRow(String labelText, android.view.View v) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 6, 0, 6);
        TextView lb = new TextView(this);
        lb.setText(labelText);
        lb.setTextColor(0xFF8B949E);
        lb.setTextSize(12);
        row.addView(lb);
        row.addView(v);
        return row;
    }

    private android.widget.EditText dialogEdit(String hint, String pre) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint(hint);
        et.setText(pre == null ? "" : pre);
        et.setSingleLine(true);
        return et;
    }

    private void openSubSearch() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(8, 0, 8, 0);
        final android.widget.EditText etQ = dialogEdit("Ten phim (vd: Inside Out 2)", "");
        etQ.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final CheckBox cbTv = new CheckBox(this);
        cbTv.setText("Phim bo (can nhap tap)");
        final Button btnViEn = new Button(this);
        btnViEn.setText("VI→EN");
        btnViEn.setTextSize(12);
        btnViEn.setOnClickListener(v -> openTmdbResolve(etQ, cbTv.isChecked()));
        LinearLayout qRow = new LinearLayout(this);
        qRow.setOrientation(LinearLayout.HORIZONTAL);
        qRow.addView(etQ);
        qRow.addView(btnViEn);
        root.addView(dialogRow("Ten phim (tieng Anh tim sub chuan nhat)", qRow));
        root.addView(cbTv);
        LinearLayout seRow = new LinearLayout(this);
        seRow.setOrientation(LinearLayout.HORIZONTAL);
        final android.widget.EditText etS = dialogEdit("Season", "1");
        final android.widget.EditText etE = dialogEdit("Tap", "1");
        etS.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        etE.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        seRow.addView(etS);
        seRow.addView(etE);
        root.addView(dialogRow("Season / Tap (chi dung cho phim bo)", seRow));
        final CheckBox cbDl = new CheckBox(this);
        cbDl.setText("SubDL");
        cbDl.setChecked(true);
        final CheckBox cbOs = new CheckBox(this);
        cbOs.setText("OpenSubtitles");
        cbOs.setChecked(true);
        final CheckBox cbSs = new CheckBox(this);
        cbSs.setText("SubSource");
        cbSs.setChecked(true);
        root.addView(cbDl);
        root.addView(cbOs);
        root.addView(cbSs);
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        new AlertDialog.Builder(this)
                .setTitle("Tim sub online (uu tien sub Viet)")
                .setView(sv)
                .setPositiveButton("Tim", (d, w) -> {
                    String q = etQ.getText().toString().trim();
                    if (q.isEmpty()) {
                        toast("Go ten phim truoc.");
                        return;
                    }
                    int s = 1, e = 1;
                    try {
                        s = Math.max(1, Integer.parseInt(
                                etS.getText().toString().trim()));
                        e = Math.max(1, Integer.parseInt(
                                etE.getText().toString().trim()));
                    } catch (Exception ignored) {}
                    runSubSearch(q, cbTv.isChecked(), s, e,
                            cbDl.isChecked(), cbOs.isChecked(), cbSs.isChecked());
                })
                .setNegativeButton("Huy", null)
                .show();
    }

    /**
     * Doi ten phim tieng Viet sang ten tieng Anh (TMDB) roi dien vao o tim sub.
     * target: o nhap ten phim cua dialog tim sub. isTv: trang thai tick Phim bo.
     */
    private void openTmdbResolve(final android.widget.EditText target,
                                 final boolean isTv) {
        String start = target.getText().toString().trim();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(8, 0, 8, 0);
        final android.widget.EditText etVi = dialogEdit(
                "Ten tieng Viet tren Netflix", start);
        root.addView(dialogRow("Ten tieng Viet", etVi));
        final ListView lv = new ListView(this);
        final TmdbMovieAdapter ad = new TmdbMovieAdapter(this,
                new ArrayList<TmdbResolver.Movie>());
        lv.setAdapter(ad);
        LinearLayout.LayoutParams lpp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 700);
        lv.setLayoutParams(lpp);
        root.addView(dialogRow("Cham 1 phim de dien ten Anh", lv));
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Doi ten Viet → Anh (TMDB)")
                .setView(sv)
                .setPositiveButton("Tim", null)
                .setNegativeButton("Huy", null)
                .create();
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            final TmdbResolver.Movie m = ad.getItem(pos);
            if (m == null) return;
            toast("Dang lay ten tieng Anh...");
            TmdbResolver.englishTitle(this, m,
                    new TmdbResolver.TitleCb() {
                        @Override public void onOk(String en) {
                            target.setText(en);
                            toast("Da dien ten Anh: " + en + " — bam Tim sub.");
                            if (dlg.isShowing()) dlg.dismiss();
                        }
                        @Override public void onError(String msg) {
                            toast(msg);
                        }
                    });
        });
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String q = etVi.getText().toString().trim();
            if (q.isEmpty()) {
                toast("Go ten tieng Viet truoc.");
                return;
            }
            toast("Dang hoi TMDB...");
            TmdbResolver.search(this, q, isTv ? "tv" : "movie",
                    new TmdbResolver.Cb() {
                        @Override public void onOk(List<TmdbResolver.Movie> ms) {
                            ad.clear();
                            ad.addAll(ms);
                            ad.notifyDataSetChanged();
                            toast("Cham 1 phim de dien ten Anh.");
                        }
                        @Override public void onError(String msg) {
                            toast(msg);
                        }
                    });
        });
        // Tu tim ngay voi ten dang co san (neu co).
        if (!start.isEmpty()) {
            TmdbResolver.search(this, start, isTv ? "tv" : "movie",
                    new TmdbResolver.Cb() {
                        @Override public void onOk(List<TmdbResolver.Movie> ms) {
                            if (!dlg.isShowing()) return;
                            ad.clear();
                            ad.addAll(ms);
                            ad.notifyDataSetChanged();
                        }
                        @Override public void onError(String msg) {
                            if (dlg.isShowing()) toast(msg);
                        }
                    });
        }
    }

    private void runSubSearch(final String q, final boolean isTv,
                              final int season, final int episode,
                              boolean useDl, boolean useOs, boolean useSs) {
        tvStatus.setText("Dang tim sub online...");
        SubSearchManager.searchAll(this, q, isTv ? "tv" : "movie",
                season, episode, useDl, useOs, useSs,
                new SubSearchManager.SearchCb() {
                    @Override public void onOk(List<SubResult> rs, String warn) {
                        if (!warn.isEmpty()) tvStatus.setText(shortMsg(warn));
                        showSubResults(rs);
                    }
                    @Override public void onError(String msg) {
                        tvStatus.setText(shortMsg(msg));
                        toast(shortMsg(msg));
                    }
                });
    }

    private void showSubResults(final List<SubResult> rs) {
        if (rs.isEmpty()) {
            toast("Khong tim thay sub.");
            return;
        }
        SubResultAdapter ad = new SubResultAdapter(this, rs);
        new AlertDialog.Builder(this)
                .setTitle("Chon sub (" + rs.size() + ") — chon dung ban phim")
                .setAdapter(ad, (d, which) -> downloadSub(rs.get(which)))
                .setNegativeButton("Huy", null)
                .show();
    }

    private void downloadSub(final SubResult r) {
        tvStatus.setText("Dang tai sub tu " + r.provider + "...");
        SubSearchManager.downloadAndParse(this, r,
                new SubSearchManager.LoadCb() {
                    @Override public void onOk(List<SubtitleCue> cues, String label) {
                        useLoadedCues(cues, "Da nap " + label);
                    }
                    @Override public void onError(String msg) {
                        tvStatus.setText(shortMsg(msg));
                        toast(shortMsg(msg));
                    }
                });
    }

    private void openSubSettings() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(8, 0, 8, 0);
        final android.widget.EditText etDl = dialogEdit("SubDL key (subdl.com/panel/api)",
                SubSearchManager.str(this, SubSearchManager.K_SUBDL));
        final android.widget.EditText etSs = dialogEdit("SubSource key (trang profile)",
                SubSearchManager.str(this, SubSearchManager.K_SUBSOURCE));
        final android.widget.EditText etOs = dialogEdit("OpenSubtitles key",
                SubSearchManager.str(this, SubSearchManager.K_OS));
        final android.widget.EditText etUser = dialogEdit("OpenSubtitles user (de tai)",
                SubSearchManager.str(this, SubSearchManager.K_OS_USER));
        final android.widget.EditText etPass = dialogEdit("OpenSubtitles pass (de tai)",
                SubSearchManager.str(this, SubSearchManager.K_OS_PASS));
        etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final android.widget.EditText etTmdb = dialogEdit("TMDB key (doi ten Viet-Anh)",
                SubSearchManager.str(this, SubSearchManager.K_TMDB));
        root.addView(dialogRow("SubDL key", etDl));
        root.addView(dialogRow("SubSource key", etSs));
        root.addView(dialogRow("OpenSubtitles key", etOs));
        root.addView(dialogRow("OpenSubtitles user", etUser));
        root.addView(dialogRow("OpenSubtitles pass", etPass));
        root.addView(dialogRow("TMDB key (themoviedb.org → Settings → API)", etTmdb));
        final CheckBox cbDl = new CheckBox(this);
        cbDl.setText("Bat SubDL");
        cbDl.setChecked(SubSearchManager.enabled(
                this, SubSearchManager.K_EN_SUBDL));
        final CheckBox cbOs = new CheckBox(this);
        cbOs.setText("Bat OpenSubtitles");
        cbOs.setChecked(SubSearchManager.enabled(
                this, SubSearchManager.K_EN_OS));
        final CheckBox cbSs = new CheckBox(this);
        cbSs.setText("Bat SubSource");
        cbSs.setChecked(SubSearchManager.enabled(
                this, SubSearchManager.K_EN_SUBSOURCE));
        root.addView(cbDl);
        root.addView(cbOs);
        root.addView(cbSs);
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        new AlertDialog.Builder(this)
                .setTitle("Key sub online (luu tren may, khong gui di dau)")
                .setView(sv)
                .setPositiveButton("Luu", (d, w) -> {
                    SubSearchManager.saveKeys(this,
                            etDl.getText().toString(),
                            etSs.getText().toString(),
                            etOs.getText().toString(),
                            etUser.getText().toString(),
                            etPass.getText().toString(),
                            etTmdb.getText().toString(),
                            cbDl.isChecked(), cbOs.isChecked(), cbSs.isChecked());
                    toast("Da luu key sub online.");
                })
                .setNegativeButton("Huy", null)
                .show();
    }

    // ---------- sao luu / khoi phuc cai dat + key ----------

    /** Cho user chon cho luu file backup JSON. */
    private void startBackup() {
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "subtts-backup.json");
            startActivityForResult(i, REQ_BACKUP);
        } catch (Exception e) {
            toast("Khong mo duoc chon cho luu: " + e.getMessage());
        }
    }

    private void startRestore() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"application/json", "text/plain"});
            startActivityForResult(i, REQ_RESTORE);
        } catch (Exception e) {
            toast("Khong mo duoc chon file: " + e.getMessage());
        }
    }

    private void writeBackup(Uri uri) {
        try {
            SharedPreferences sp =
                    getSharedPreferences("webtts", MODE_PRIVATE);
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("app", "subtts");
            o.put("v", 1);
            o.put("rate", (double) sp.getFloat("rate", 1.0f));
            o.put("ttsVol", sp.getInt("ttsVol", 100));
            o.put("voice", sp.getInt("voice", 0));
            o.put("splitAudio", sp.getBoolean("splitAudio", true));
            o.put("offset", (double) sp.getFloat("offset", 0f));
            o.put("totalLen", (double) sp.getFloat("totalLen", 0f));
            putStr(o, sp, SubSearchManager.K_SUBDL);
            putStr(o, sp, SubSearchManager.K_SUBSOURCE);
            putStr(o, sp, SubSearchManager.K_OS);
            putStr(o, sp, SubSearchManager.K_OS_USER);
            putStr(o, sp, SubSearchManager.K_OS_PASS);
            putStr(o, sp, SubSearchManager.K_TMDB);
            putStr(o, sp, "os_token");
            o.put(SubSearchManager.K_EN_SUBDL,
                    sp.getBoolean(SubSearchManager.K_EN_SUBDL, true));
            o.put(SubSearchManager.K_EN_OS,
                    sp.getBoolean(SubSearchManager.K_EN_OS, true));
            o.put(SubSearchManager.K_EN_SUBSOURCE,
                    sp.getBoolean(SubSearchManager.K_EN_SUBSOURCE, true));
            java.io.OutputStream out =
                    getContentResolver().openOutputStream(uri);
            out.write(o.toString().getBytes("UTF-8"));
            out.close();
            toast("Da luu sao luu (giu file nay can than, co key).");
        } catch (Exception e) {
            toast("Luu that bai: " + e.getMessage());
        }
    }

    private static void putStr(org.json.JSONObject o, SharedPreferences sp,
                               String k) {
        try {
            o.put(k, sp.getString(k, ""));
        } catch (Exception ignored) {}
    }

    private void readRestore(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            byte[] buf = new byte[65536];
            StringBuilder sb = new StringBuilder();
            int total = 0, n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 65536) break;
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            in.close();
            org.json.JSONObject o = new org.json.JSONObject(sb.toString());
            if (!"subtts".equals(o.optString("app", ""))) {
                toast("File nay khong phai sao luu Sub TTS.");
                return;
            }
            SharedPreferences sp =
                    getSharedPreferences("webtts", MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();
            ed.putFloat("rate", (float) o.optDouble("rate", 1.0));
            ed.putInt("ttsVol", o.optInt("ttsVol", 100));
            ed.putInt("voice", o.optInt("voice", 0));
            ed.putBoolean("splitAudio", o.optBoolean("splitAudio", true));
            ed.putFloat("offset", (float) o.optDouble("offset", 0));
            ed.putFloat("totalLen", (float) o.optDouble("totalLen", 0));
            getStr(o, ed, SubSearchManager.K_SUBDL);
            getStr(o, ed, SubSearchManager.K_SUBSOURCE);
            getStr(o, ed, SubSearchManager.K_OS);
            getStr(o, ed, SubSearchManager.K_OS_USER);
            getStr(o, ed, SubSearchManager.K_OS_PASS);
            getStr(o, ed, SubSearchManager.K_TMDB);
            getStr(o, ed, "os_token");
            ed.putBoolean(SubSearchManager.K_EN_SUBDL,
                    o.optBoolean(SubSearchManager.K_EN_SUBDL, true));
            ed.putBoolean(SubSearchManager.K_EN_OS,
                    o.optBoolean(SubSearchManager.K_EN_OS, true));
            ed.putBoolean(SubSearchManager.K_EN_SUBSOURCE,
                    o.optBoolean(SubSearchManager.K_EN_SUBSOURCE, true));
            ed.apply();
            refreshAllUi();
            toast("Da khoi phuc xong.");
        } catch (Exception e) {
            toast("Khoi phuc that bai: " + e.getMessage());
        }
    }

    private static void getStr(org.json.JSONObject o,
                               SharedPreferences.Editor ed, String k) {
        try {
            if (o.has(k)) ed.putString(k, o.optString(k, ""));
        } catch (Exception ignored) {}
    }

    /** Nap lai toan bo UI + day xuong service sau khi khoi phuc. */
    private void refreshAllUi() {
        SharedPreferences sp =
                getSharedPreferences("webtts", MODE_PRIVATE);
        rate = sp.getFloat("rate", 1.0f);
        ttsVol = sp.getInt("ttsVol", 100);
        voice = sp.getInt("voice", TtsPlaybackService.VOICE_GOOGLE);
        totalLen = sp.getFloat("totalLen", 0);
        seekRate.setProgress(Math.round((rate - 0.5f) / 2.0f * 200));
        tvRate.setText(String.format(Locale.US, "%.1fx", rate));
        seekTtsVol.setProgress(ttsVol);
        tvTtsVol.setText(ttsVol + "%");
        if (etTotalLen != null) {
            etTotalLen.setText(totalLen > 0 ? fmtHMS(totalLen) : "");
        }
        refreshPlayBtn();
        refreshVoiceUi();
        refreshCacheBtn();
        ensureService();
        TtsPlaybackService.setRate(rate);
        TtsPlaybackService.setTtsVolume(ttsVol);
        TtsPlaybackService.setVoice(voice);
        TtsPlaybackService.setTotalLen(totalLen);
        TtsPlaybackService.setSplitAudio(sp.getBoolean("splitAudio", true));
        syncUi();
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

    /** Thoat han app: dung service chay nen + dong man hinh. */
    private void exitAppUi() {
        if (TtsPlaybackService.isRunning()) {
            TtsPlaybackService.cmdExit(this);
        }
        finishAffinity();
    }

    private void clearCacheUi() {        ensureService();
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
        return String.format(Locale.US, "%02d:%02d:%02d",
                s / 3600, (s % 3600) / 60, s % 60);
    }

    /** "01:32:00" de hien tong thoi luong phim da luu. */
    private static String fmtHMS(double sec) {
        if (sec <= 0) return "";
        int s = (int) sec;
        return String.format(Locale.US, "%02d:%02d:%02d",
                s / 3600, (s % 3600) / 60, s % 60);
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

    /** Cat thong bao con ngan (<=300 ky tu) de khong tran man hinh. */
    private static String shortMsg(String s) {
        if (s == null) return "";
        s = s.trim().replaceAll("\\s+", " ");
        return s.length() > 300 ? s.substring(0, 300) : s;
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
