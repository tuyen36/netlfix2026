# Netflix TTS — ban rieng chay song song app Netflix goc

Bo WebView + offset. UI hien dai: the cau dang doc + nut Play to.

## Cach dung

1. Chon file `.srt` → Play.
2. Thoat sang **app Netflix goc** xem phim.
3. Dieu khien nhanh bang **nut noi mo 30%** (Play/Pause, -1s/+1s,
   gio chay `mm:ss`, volume sub, chon giong, tach/gop loa).
4. Muon nhay toi doan khac: keo **thanh timeline** (trong app hoac tren
   nut noi) roi tha tay — sub phat tiep tu moc do. Hoac bam
   **⏮ Cau truoc / Cau sau ⏭** de nhay theo cau phu de.
5. **Can gio dem nguoc voi Netflix** (Netflix chi bao gio con lai):
   nhap **tong thoi luong phim** 1 lan (vd `1:32:00`) → app hien
   **gio con lai** (`-25:30`) giong het Netflix de so truc tiep.
   Muon nhay nhanh: bam **Nhap gio con lai** → go con so dang thay
   tren Netflix (vd `25:30`) → sub nhay toi dung cho, tinh chinh
   them bang `-1s/+1s`.

## Giong doc

- **Google (offline)**: khong can mang.
- **HoaiMy (online)**: can mang, preload 35 cau, cache mp3.
  Nut **Xoa cache (N MB)** de don.

## Build

Nhap repo `tuyen36/web-tts-netflix` → Actions xanh → Artifacts `app-debug`.
Cai song song duoc voi ban Web TTS (khac applicationId).
