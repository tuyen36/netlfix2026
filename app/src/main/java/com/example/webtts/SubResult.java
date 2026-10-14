package com.example.webtts;

/**
 * 1 ket qua sub tim duoc tu SubDL / OpenSubtitles / SubSource.
 * ref: tham chieu tai tuy nguon (url | file_id | subtitleId).
 */
public class SubResult {
    public final String provider;
    public final String film;
    public final String language;
    public final String release;
    public final String sizeText;
    public final String ref;

    public SubResult(String provider, String film, String language,
                     String release, String sizeText, String ref) {
        this.provider = provider == null ? "" : provider;
        this.film = film == null ? "" : film;
        this.language = language == null ? "" : language;
        this.release = release == null ? "" : release;
        this.sizeText = sizeText == null ? "" : sizeText;
        this.ref = ref == null ? "" : ref;
    }

    /** Dong hien trong list chon (uu tien doc release de chon dung ban). */
    public String label() {
        String r = release.isEmpty() ? "(khong ro ban)" : release;
        if (!sizeText.isEmpty()) r += " [" + sizeText + "]";
        return "[" + provider + "] " + r;
    }

    public boolean isVietnamese() {
        String l = language.toLowerCase(java.util.Locale.US);
        return l.startsWith("vi");
    }
}
