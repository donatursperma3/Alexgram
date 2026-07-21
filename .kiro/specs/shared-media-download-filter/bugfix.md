# Bugfix Requirements Document

## Introduction

Pada halaman profile chat di aplikasi Telegram (fork NekoX/NekogramX), tab **Files** di bagian Shared Media memiliki fitur filter download yang memungkinkan user memilih antara menampilkan semua file, hanya file yang sudah diunduh, atau hanya file yang belum diunduh. Fitur filter ini diakses melalui tombol opsi (`photoVideoOptionsItem`) di action bar.

Bug yang dilaporkan: **tombol filter download tidak muncul (tidak tampil)** saat user berada di tab Files pada shared media di profile chat. Analisis kode menemukan dua bug terkait:

1. **Bug utama — referensi variabel salah di `setMediaHeaderVisible()`**: Di `ProfileActivity.java`, fungsi `setMediaHeaderVisible()` mengambil referensi opsi-item melalui `sharedMediaLayout.getSearchOptionsItem()`, yang mengembalikan `optionsSearchImageView` (ikon animasi search-to-options). Referensi ini lalu dipakai untuk mengatur visibility `VISIBLE`/`GONE` — padahal tombol filter download yang sebenarnya adalah `photoVideoOptionsItem` (objek `ImageView` terpisah). Akibatnya, saat media header visible, `optionsSearchImageView` yang diset visible, sementara `photoVideoOptionsItem` (tombol filter download) tidak pernah dipicu menjadi `VISIBLE` melalui jalur ini.

2. **Bug sekunder — potensi overlap UI**: `photoVideoOptionsItem` ditambahkan ke action bar dengan gravity `Gravity.RIGHT | Gravity.BOTTOM`, pada posisi yang sama dengan `otherItem` (three-dot kebab menu) ketika keduanya aktif secara bersamaan. Walaupun ada mekanisme hide/show bergantian, kondisi race atau transisi yang tidak tuntas bisa menyebabkan overlap atau satu tombol menutupi yang lain.

---

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN user membuka profile chat dan scroll ke bawah hingga section shared media tampil di action bar THEN sistem tidak menampilkan tombol filter download (photoVideoOptionsItem) meskipun tab aktif adalah TAB_FILES

1.2 WHEN `setMediaHeaderVisible(true)` dipanggil di ProfileActivity THEN sistem memanggil `sharedMediaLayout.getSearchOptionsItem()` yang mengembalikan `optionsSearchImageView` (bukan `photoVideoOptionsItem`), sehingga yang di-set VISIBLE adalah objek yang salah

1.3 WHEN user berada di tab Files pada shared media di profile chat THEN tombol three-dot (`otherItem`) dan `photoVideoOptionsItem` memiliki posisi yang sama di action bar sehingga berpotensi overlap jika transisi visibility tidak selesai dengan benar

1.4 WHEN `setMediaHeaderVisible(false)` dipanggil THEN sistem menyembunyikan `optionsSearchImageView` lewat `mediaOptionsItem.setVisibility(View.GONE)` tetapi `photoVideoOptionsItem` tetap dalam state visibility yang tidak konsisten

### Expected Behavior (Correct)

2.1 WHEN user membuka profile chat dan scroll ke bawah hingga media header visible THEN sistem SHALL menampilkan `photoVideoOptionsItem` (tombol filter download) dengan benar apabila tab aktif adalah TAB_FILES, TAB_PHOTOVIDEO, atau tab lain yang memenuhi kondisi `isOptionsItemVisible()`

2.2 WHEN `setMediaHeaderVisible(true)` dipanggil di ProfileActivity THEN sistem SHALL menggunakan referensi `sharedMediaLayout.photoVideoOptionsItem` secara langsung (atau melalui metode getter yang tepat) untuk mengatur visibility, bukan menggunakan `getSearchOptionsItem()` yang mengembalikan objek berbeda

2.3 WHEN media header visible dan tab aktif adalah TAB_FILES THEN sistem SHALL menampilkan tombol filter download (`photoVideoOptionsItem`) tanpa overlap dengan `otherItem` (three-dot menu) — dengan opsi menyediakan posisi tombol yang terpisah (misalnya di sebelah kiri three-dot menu) bila diperlukan

2.4 WHEN `setMediaHeaderVisible(false)` dipanggil THEN sistem SHALL menyembunyikan `photoVideoOptionsItem` secara konsisten sehingga tidak ada state visibility yang tidak sinkron antara `photoVideoOptionsItem` dan `optionsSearchImageView`

2.5 WHEN user menekan `photoVideoOptionsItem` di tab Files THEN sistem SHALL menampilkan popup menu dengan opsi "Show All Files", "Show Downloaded Files", dan "Show Not Downloaded Files" tanpa menyebabkan crash

### Unchanged Behavior (Regression Prevention)

3.1 WHEN user berada di tab Photos/Videos (TAB_PHOTOVIDEO) pada shared media THEN sistem SHALL CONTINUE TO menampilkan tombol opsi filter (photoVideoOptionsItem) dan menyediakan pilihan filter photos/videos seperti semula

3.2 WHEN user membuka shared media dan berpindah antar tab (bukan TAB_FILES dan bukan TAB_PHOTOVIDEO) seperti TAB_LINKS, TAB_AUDIO, TAB_VOICE THEN sistem SHALL CONTINUE TO menyembunyikan photoVideoOptionsItem karena `isOptionsItemVisible()` mengembalikan false untuk tab tersebut

3.3 WHEN user melakukan pencarian (search bar expand) di shared media THEN sistem SHALL CONTINUE TO menyembunyikan photoVideoOptionsItem selama mode pencarian aktif

3.4 WHEN media header tidak visible (user scroll ke posisi atas profile) THEN sistem SHALL CONTINUE TO menampilkan otherItem (three-dot kebab menu) tanpa overlap dari photoVideoOptionsItem

3.5 WHEN user berada di tab Files dan memilih filter (All / Downloaded / Not Downloaded) THEN sistem SHALL CONTINUE TO menjalankan `applyFilesFilter()` dan merefresh list file tanpa crash

3.6 WHEN SharedMediaLayout digunakan di MediaActivity (bukan ProfileActivity) THEN sistem SHALL CONTINUE TO menampilkan photoVideoOptionsItem sesuai dengan logika visibilitas yang sudah ada di MediaActivity

---

## Derivasi Bug Condition

**Bug Condition Function:**
```pascal
FUNCTION isBugCondition(context)
  INPUT: context = (hostActivity, currentTab, mediaHeaderVisible)
  OUTPUT: boolean

  RETURN hostActivity IS ProfileActivity
    AND mediaHeaderVisible = true
    AND currentTab IN { TAB_FILES, TAB_PHOTOVIDEO, TAB_STORIES, TAB_SAVED_DIALOGS, TAB_BOT_PREVIEWS, TAB_GIFTS }
END FUNCTION
```

**Property: Fix Checking**
```pascal
FOR ALL ctx WHERE isBugCondition(ctx) DO
  result ← observeVisibility(sharedMediaLayout.photoVideoOptionsItem)
  ASSERT result = VISIBLE
END FOR
```

**Property: Preservation Checking**
```pascal
FOR ALL ctx WHERE NOT isBugCondition(ctx) DO
  ASSERT behavior(F(ctx)) = behavior(F'(ctx))
END FOR
```

Di mana:
- **F** = kode sebelum fix (dengan referensi `getSearchOptionsItem()` yang salah)
- **F'** = kode setelah fix (dengan referensi yang benar ke `photoVideoOptionsItem`)
