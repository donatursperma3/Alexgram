# Shared Media Download Filter — Bugfix Design

## Overview

Terdapat dua bug terkait yang menyebabkan tombol filter download (`photoVideoOptionsItem`) tidak muncul saat user berada di tab **Files** pada shared media di `ProfileActivity`.

**Bug utama**: Di `setMediaHeaderVisible()` (`ProfileActivity.java`), variabel lokal `mediaOptionsItem` di-assign dari `sharedMediaLayout.getSearchOptionsItem()`, yang mengembalikan `optionsSearchImageView` (sebuah `RLottieImageView` berisi animasi search-to-options). Objek ini digunakan untuk mengatur `setVisibility(VISIBLE/GONE)` — padahal `photoVideoOptionsItem` (tombol filter download yang sebenarnya) adalah `ImageView` terpisah yang tidak terpengaruh oleh jalur ini. Akibatnya, saat media header visible, `photoVideoOptionsItem` tidak pernah di-set `VISIBLE` melalui `mediaOptionsItem`.

Perhatian: setelah membaca kode lebih teliti, ternyata `setMediaHeaderVisible()` sudah mengandung baris yang secara langsung memanipulasi `sharedMediaLayout.photoVideoOptionsItem` di blok `else` (visible=true). Namun `mediaOptionsItem` (yang salah referensinya) juga di-set `VISIBLE` di blok yang sama, menyebabkan `optionsSearchImageView` tampil di atas `photoVideoOptionsItem` dan berpotensi menutupinya. Saat `visible=false`, hanya `mediaOptionsItem` (yaitu `optionsSearchImageView`) yang di-set `GONE`, sedangkan `photoVideoOptionsItem` dibiarkan dalam state sebelumnya hingga animasi selesai — membuka celah state yang tidak konsisten.

**Bug sekunder**: `photoVideoOptionsItem` dan `optionsSearchImageView` keduanya di-add ke `actionBar` dengan `Gravity.RIGHT | Gravity.BOTTOM` di posisi yang sama, sehingga saat keduanya visible secara bersamaan (seperti yang terjadi akibat bug utama), mereka overlap. Selain itu, `otherItem` (three-dot menu) yang juga berada di kanan action bar berpotensi overlap dengan `photoVideoOptionsItem` bila transisi visibility tidak selesai dengan benar.

**Strategi fix**: Hapus penggunaan `mediaOptionsItem` yang merujuk ke `getSearchOptionsItem()` dari logika visibility di `setMediaHeaderVisible()`, karena visibilitas `optionsSearchImageView` sudah dikelola sepenuhnya oleh `SharedMediaLayout` sendiri melalui `animateSearchToOptions()`. Pindahkan `photoVideoOptionsItem` ke posisi yang tidak overlap dengan `otherItem` (misalnya dengan margin kanan 48dp).

---

## Glossary

- **Bug_Condition (C)**: Kondisi yang memicu bug — ketika `setMediaHeaderVisible(true)` dipanggil di `ProfileActivity` dengan tab aktif yang memenuhi `isOptionsItemVisible()`, tetapi `photoVideoOptionsItem` tidak menjadi `VISIBLE` karena logika yang salah.
- **Property (P)**: Perilaku yang diharapkan setelah fix — `photoVideoOptionsItem` SHALL `VISIBLE` untuk semua context di mana bug condition terpenuhi.
- **Preservation**: Perilaku yang tidak boleh berubah setelah fix — semua interaksi non-keyboard, semua tab lain, logika di `MediaActivity`, dan fungsionalitas three-dot menu.
- **`photoVideoOptionsItem`**: `ImageView` di `SharedMediaLayout` (`Components/SharedMediaLayout.java`) yang berfungsi sebagai tombol opsi/filter untuk tab Photos/Videos/Files/Stories/dll. Ditambahkan ke `actionBar` dengan `Gravity.RIGHT | Gravity.BOTTOM`.
- **`optionsSearchImageView`**: `RLottieImageView` di `SharedMediaLayout` yang menampilkan animasi transisi antara ikon search dan ikon options. Ditambahkan di posisi yang sama dengan `photoVideoOptionsItem`.
- **`getSearchOptionsItem()`**: Method publik di `SharedMediaLayout` yang mengembalikan `optionsSearchImageView` — **bukan** `photoVideoOptionsItem`.
- **`mediaOptionsItem`**: Variabel lokal di `setMediaHeaderVisible()` (`ProfileActivity`) yang di-assign dari `getSearchOptionsItem()`. Saat ini salah digunakan untuk mengontrol `photoVideoOptionsItem`.
- **`otherItem`**: `ActionBarMenuItem` (three-dot kebab menu) di `ProfileActivity` yang berada di sisi kanan action bar. Disembunyikan saat media header visible dan ditampilkan kembali setelahnya.
- **`isOptionsItemVisible()`**: Method di `SharedMediaLayout` yang mengembalikan `true` jika tab aktif adalah `TAB_PHOTOVIDEO`, `TAB_FILES`, atau tab stories/saved/gifts tertentu.
- **`setMediaHeaderVisible(boolean)`**: Method private di `ProfileActivity` yang mengatur transisi antara tampilan header profile (dengan `otherItem`) dan tampilan media header (dengan search + options items).

---

## Bug Details

### Bug Condition

Bug muncul ketika `setMediaHeaderVisible(true)` dipanggil di `ProfileActivity` saat user scroll ke bawah hingga shared media muncul di action bar, dan tab aktif adalah salah satu tab yang memenuhi `isOptionsItemVisible()` (termasuk `TAB_FILES`).

Dalam kondisi ini, variabel `mediaOptionsItem` (yang seharusnya mewakili tombol opsi) malah merujuk ke `optionsSearchImageView` — bukan `photoVideoOptionsItem`. Akibatnya:
1. `optionsSearchImageView` di-set `VISIBLE` secara eksplisit (tidak perlu, karena `SharedMediaLayout` sudah mengelolanya sendiri).
2. `optionsSearchImageView` yang tampil di atas `photoVideoOptionsItem` pada posisi yang sama berpotensi menutupinya secara visual.
3. Saat `visible=false`, hanya `optionsSearchImageView` yang di-set `GONE` lewat `mediaOptionsItem.setVisibility(GONE)`, sementara state `photoVideoOptionsItem` bergantung pada penyelesaian animasi.

**Formal Specification:**
```
FUNCTION isBugCondition(context)
  INPUT: context = { hostActivity, currentTab, mediaHeaderVisible }
  OUTPUT: boolean

  RETURN hostActivity IS ProfileActivity
    AND mediaHeaderVisible = true
    AND isOptionsItemVisible(currentTab) = true
    -- di mana isOptionsItemVisible() = true untuk:
    -- TAB_PHOTOVIDEO, TAB_FILES, TAB_STORIES, TAB_SAVED_DIALOGS,
    -- TAB_BOT_PREVIEWS, TAB_GIFTS (dengan syarat tertentu)
END FUNCTION
```

### Contoh Konkret

- **Tab Files, media header visible → tombol filter tidak muncul**: User membuka profile chat, scroll ke bawah hingga tab bar shared media muncul di action bar, pindah ke tab Files. `setMediaHeaderVisible(true)` dipanggil. `mediaOptionsItem` (= `optionsSearchImageView`) di-set `VISIBLE`, tetapi `optionsSearchImageView` berada di layer yang sama dengan `photoVideoOptionsItem`. Jika `optionsSearchImageView` tidak transparan, tombol filter download (`photoVideoOptionsItem`) tertutup atau tidak terlihat dengan benar.

- **Tab Files, media header hidden kembali → state inconsistent**: User scroll ke atas, `setMediaHeaderVisible(false)` dipanggil. `mediaOptionsItem.setVisibility(GONE)` men-set `optionsSearchImageView` menjadi `GONE`. `photoVideoOptionsItem` tidak disentuh di blok `!mediaHeaderVisible`, state-nya bergantung pada animasi sebelumnya — bisa tetap `VISIBLE` sehingga overlap dengan `otherItem`.

- **Tab PhotoVideo, perilaku sama**: Bug juga terjadi di tab PhotoVideo, meski efeknya mungkin lebih ringan karena `optionsSearchImageView` biasanya transparan di state default.

- **Edge case — animasi dibatalkan**: Jika `headerAnimatorSet.cancel()` dipanggil di tengah animasi, `photoVideoOptionsItem` bisa terjebak di alpha/translationY yang tidak sesuai karena manipulasi visibility-nya tidak dikembalikan ke state yang benar.

---

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Klik mouse/touch pada `photoVideoOptionsItem` harus tetap menampilkan popup menu opsi (All Files / Downloaded / Not Downloaded untuk tab Files, filter photos/videos untuk tab PhotoVideo).
- `otherItem` (three-dot kebab menu) harus tetap muncul sepenuhnya saat media header tidak visible, tanpa tertimpa `photoVideoOptionsItem`.
- Perilaku tab lain (`TAB_LINKS`, `TAB_AUDIO`, `TAB_VOICE`, dll.) — `photoVideoOptionsItem` tidak boleh muncul untuk tab-tab ini.
- Mode pencarian (search bar expand) harus tetap menyembunyikan `photoVideoOptionsItem`.
- `SharedMediaLayout` saat digunakan di `MediaActivity` tidak boleh terpengaruh oleh perubahan di `ProfileActivity`.
- Animasi transisi (alpha + translationY) pada `photoVideoOptionsItem` harus tetap berjalan dengan benar.
- `optionsSearchImageView` (animasi search-to-options) tetap dikelola oleh `SharedMediaLayout` sendiri, tanpa interferensi dari `ProfileActivity`.

**Scope:**
Semua input yang tidak memenuhi `isBugCondition()` (yaitu: semua tab tanpa `isOptionsItemVisible() = true`, semua interaksi saat media header tidak visible, semua interaksi di `MediaActivity`) harus tidak terpengaruh oleh fix ini.

**Catatan:** Perilaku yang diharapkan setelah fix (expected correct behavior) untuk input yang memenuhi bug condition didefinisikan di bagian Correctness Properties (Property 1).

---

## Hypothesized Root Cause

Berdasarkan analisis kode:

1. **Penggunaan getter yang salah**: `getSearchOptionsItem()` mengembalikan `optionsSearchImageView`, bukan `photoVideoOptionsItem`. Developer yang menulis `setMediaHeaderVisible()` mungkin berasumsi bahwa `getSearchOptionsItem()` mengembalikan tombol opsi filter, padahal method tersebut mengembalikan view animasi search-to-options. Tidak ada method `getPhotoVideoOptionsItem()` yang tersedia, sehingga akses langsung ke field publik `sharedMediaLayout.photoVideoOptionsItem` digunakan di bagian lain kode — tetapi tidak konsisten diterapkan di seluruh method.

2. **Duplikasi logika visibility**: `setMediaHeaderVisible()` mengatur visibility `mediaOptionsItem` (yang salah) DAN `sharedMediaLayout.photoVideoOptionsItem` secara langsung. Ini menyebabkan dua view dimanipulasi, salah satunya tidak relevan (karena `optionsSearchImageView` sudah dikelola oleh `SharedMediaLayout` sendiri). Solusi yang bersih adalah menghapus penggunaan `mediaOptionsItem` sepenuhnya dari logika visibility dan membiarkan `SharedMediaLayout` mengelola `optionsSearchImageView` sendiri.

3. **Posisi UI yang sama (overlap)**: `photoVideoOptionsItem` dan `optionsSearchImageView` keduanya di-add ke `actionBar` dengan `Gravity.RIGHT | Gravity.BOTTOM` tanpa offset horizontal. Saat bug utama menyebabkan `optionsSearchImageView` di-set `VISIBLE` secara eksplisit bersamaan dengan `photoVideoOptionsItem`, keduanya overlap di posisi yang sama. Fix bug utama akan menghilangkan interferensi ini, namun posisi `photoVideoOptionsItem` relatif terhadap `otherItem` masih perlu disesuaikan.

4. **Posisi `photoVideoOptionsItem` vs `otherItem`**: `otherItem` (three-dot menu) juga berada di paling kanan action bar. Saat transisi dari media header visible ke tidak visible (atau sebaliknya), ada jendela singkat di mana keduanya bisa visible bersamaan karena animasi. Memperbaiki margin `photoVideoOptionsItem` agar ada di sebelah kiri `otherItem` (tambah `rightMargin = 48dp`) akan mencegah overlap visual.

---

## Correctness Properties

Property 1: Bug Condition — Download Filter Button Visible di ProfileActivity

_For any_ context di mana `isBugCondition(context)` bernilai true (yaitu: `ProfileActivity`, `mediaHeaderVisible = true`, dan `isOptionsItemVisible(currentTab) = true`), fungsi `setMediaHeaderVisible` yang telah diperbaiki SHALL memastikan `sharedMediaLayout.photoVideoOptionsItem` memiliki visibility `View.VISIBLE` dan tidak tertutup secara visual oleh view lain pada posisi yang sama di action bar.

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation — Non-Buggy Context Tidak Berubah

_For any_ context di mana `isBugCondition(context)` bernilai false (yaitu: `mediaHeaderVisible = false`, atau tab yang tidak memenuhi `isOptionsItemVisible()`, atau konteks bukan `ProfileActivity`), kode yang telah diperbaiki SHALL menghasilkan perilaku yang identik dengan kode asli — termasuk visibility `otherItem`, `photoVideoOptionsItem`, `optionsSearchImageView`, dan fungsionalitas popup menu filter.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

---

## Fix Implementation

### Perubahan yang Diperlukan

Dengan asumsi root cause analysis di atas benar:

**File**: `TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java`

**Method**: `setMediaHeaderVisible(boolean visible)`

**Perubahan Spesifik**:

1. **Hapus variabel `mediaOptionsItem` yang salah referensinya**:
   - Hapus baris: `ImageView mediaOptionsItem = sharedMediaLayout.getSearchOptionsItem();`
   - `optionsSearchImageView` tidak perlu dimanipulasi dari `ProfileActivity` — `SharedMediaLayout` sudah mengelolanya via `animateSearchToOptions()` yang sudah dipanggil di bawahnya.

2. **Hapus blok `if (mediaOptionsItem != null)` dari branch `!mediaHeaderVisible`**:
   - Hapus:
     ```java
     if (mediaOptionsItem != null) {
         mediaOptionsItem.setVisibility(View.GONE);
     }
     ```
   - Alasannya: `optionsSearchImageView` akan otomatis di-reset ke state yang benar oleh `animateSearchToOptions(false, false)` yang sudah ada di blok `!mediaHeaderVisible` (lewat `onAnimationEnd`).

3. **Hapus blok `if (mediaOptionsItem != null)` dari branch `mediaHeaderVisible`**:
   - Hapus:
     ```java
     if (mediaOptionsItem != null) {
         mediaOptionsItem.setVisibility(View.VISIBLE);
     }
     ```
   - Alasannya: Baris ini men-set `optionsSearchImageView` menjadi `VISIBLE` secara tidak perlu dan interferensi dengan logika yang sudah ada.

4. **Tambahkan getter `getPhotoVideoOptionsItem()` di `SharedMediaLayout`** (opsional, untuk konsistensi dan encapsulation):
   - Di `SharedMediaLayout.java`, tambahkan method:
     ```java
     public ImageView getPhotoVideoOptionsItem() {
         return photoVideoOptionsItem;
     }
     ```
   - Ini opsional karena `photoVideoOptionsItem` sudah `public`, namun getter lebih idiomatik dan memudahkan refactor ke depan.

**File**: `TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java`

**Method**: Constructor / `addView` untuk `photoVideoOptionsItem`

5. **Perbaiki posisi `photoVideoOptionsItem` agar tidak overlap dengan `otherItem`**:
   - Ubah gravity dan margin saat `addView`:
     ```java
     // Sebelum:
     actionBar.addView(photoVideoOptionsItem, LayoutHelper.createFrame(48, 56, Gravity.RIGHT | Gravity.BOTTOM));
     
     // Sesudah:
     actionBar.addView(photoVideoOptionsItem, LayoutHelper.createFrame(48, 56, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 48, 0));
     ```
   - `rightMargin = 48dp` menempatkan `photoVideoOptionsItem` tepat di sebelah kiri `otherItem` (yang berukuran 48dp).
   - Dengan ini, kedua tombol tidak pernah overlap meskipun ada di action bar secara bersamaan selama animasi transisi.

### Perubahan TIDAK Diperlukan

- Tidak perlu mengubah blok visibility `sharedMediaLayout.photoVideoOptionsItem` di branch `visible=true` (baris `sharedMediaLayout.photoVideoOptionsItem.setVisibility(View.VISIBLE)` dan `View.INVISIBLE`) — logika ini sudah benar.
- Tidak perlu mengubah `animateSearchToOptions()` — sudah benar.
- Tidak perlu mengubah `isOptionsItemVisible()` — sudah meng-cover `TAB_FILES`.
- Tidak perlu mengubah `MediaActivity` — bug hanya di `ProfileActivity`.

---

## Testing Strategy

### Validation Approach

Strategi testing mengikuti dua fase: pertama, surfacing counterexample yang mendemonstrasikan bug pada kode yang belum diperbaiki; kemudian verifikasi bahwa fix benar dan tidak merusak perilaku yang ada.

### Exploratory Bug Condition Checking

**Goal**: Membuktikan bug sebelum implement fix. Konfirmasi atau bantah root cause analysis. Jika terbantah, perlu re-hypothesize.

**Test Plan**: Buat unit test yang mock `ProfileActivity` dan `SharedMediaLayout` dengan tab aktif `TAB_FILES`, panggil `setMediaHeaderVisible(true)`, dan periksa visibility `photoVideoOptionsItem`. Jalankan pada kode **sebelum** diperbaiki untuk melihat failure.

**Test Cases**:
1. **Tab Files, media header visible**: Panggil `setMediaHeaderVisible(true)` dengan `isOptionsItemVisible()` = true. Assert `photoVideoOptionsItem.getVisibility() == View.VISIBLE`. (Akan fail pada kode unfixed jika `optionsSearchImageView` menutupinya, atau jika ada race condition).
2. **Tab Files, media header hidden lagi**: Setelah `setMediaHeaderVisible(true)`, panggil `setMediaHeaderVisible(false)`. Assert `photoVideoOptionsItem.getVisibility() != View.VISIBLE` (tidak lagi visible). Assert `otherItem.getVisibility() == View.VISIBLE`.
3. **Tab PhotoVideo, media header visible**: Sama seperti test 1 untuk `TAB_PHOTOVIDEO`.
4. **Animasi dibatalkan di tengah jalan**: Panggil `setMediaHeaderVisible(true)` lalu segera `setMediaHeaderVisible(false)` tanpa animasi selesai. Periksa state akhir konsisten.

**Expected Counterexamples**:
- `photoVideoOptionsItem` tidak `VISIBLE` meskipun `isOptionsItemVisible()` = true dan media header sudah visible.
- `optionsSearchImageView` berada di atas `photoVideoOptionsItem` (posisi overlap).

### Fix Checking

**Goal**: Verifikasi bahwa untuk semua input di mana `isBugCondition(input)` = true, fungsi yang telah diperbaiki menghasilkan perilaku yang benar.

**Pseudocode:**
```
FOR ALL ctx WHERE isBugCondition(ctx) DO
  result ← setMediaHeaderVisible_fixed(true, ctx)
  ASSERT photoVideoOptionsItem.getVisibility() = View.VISIBLE
  ASSERT posisi(photoVideoOptionsItem) TIDAK overlap dengan posisi(otherItem)
  ASSERT posisi(photoVideoOptionsItem) TIDAK overlap dengan posisi(optionsSearchImageView)
END FOR
```

### Preservation Checking

**Goal**: Verifikasi bahwa untuk semua input di mana `isBugCondition(input)` = false, kode yang diperbaiki menghasilkan hasil yang sama dengan kode asli.

**Pseudocode:**
```
FOR ALL ctx WHERE NOT isBugCondition(ctx) DO
  ASSERT behavior(setMediaHeaderVisible_original(ctx)) 
       = behavior(setMediaHeaderVisible_fixed(ctx))
END FOR
```

**Testing Approach**: Property-based testing direkomendasikan untuk preservation checking karena:
- Menghasilkan banyak kombinasi tab × mediaHeaderVisible secara otomatis.
- Menangkap edge case yang mungkin terlewat oleh unit test manual.
- Memberikan jaminan kuat bahwa perilaku tidak berubah untuk semua input non-buggy.

**Test Plan**: Observasi perilaku pada kode unfixed untuk tab non-options dan interaksi non-filter, kemudian tulis property-based tests yang memvalidasi behavior tersebut tetap sama setelah fix.

**Test Cases**:
1. **Tab LINKS/AUDIO/VOICE preservation**: `setMediaHeaderVisible(true)` dengan tab yang tidak memenuhi `isOptionsItemVisible()` — `photoVideoOptionsItem` harus tetap `INVISIBLE`.
2. **`otherItem` visibility preservation**: Setelah `setMediaHeaderVisible(false)`, `otherItem` harus `VISIBLE`.
3. **Popup menu filter preservation**: Click pada `photoVideoOptionsItem` saat tab Files harus tetap menampilkan popup dengan 3 opsi (Show All / Show Downloaded / Show Not Downloaded).
4. **`MediaActivity` unaffected**: `SharedMediaLayout` di `MediaActivity` harus berperilaku identik sebelum dan sesudah fix.
5. **Mode pencarian preservation**: Saat search bar expand, `photoVideoOptionsItem` harus tetap `GONE`.

### Unit Tests

- Test `setMediaHeaderVisible(true)` untuk setiap tab yang memenuhi `isOptionsItemVisible()` → `photoVideoOptionsItem` harus `VISIBLE`.
- Test `setMediaHeaderVisible(false)` → `photoVideoOptionsItem` harus `INVISIBLE`, `otherItem` harus `VISIBLE`.
- Test bahwa `mediaOptionsItem`/`getSearchOptionsItem()` tidak lagi dimanipulasi visibility-nya dari `ProfileActivity`.
- Test edge case: `setMediaHeaderVisible()` dipanggil berulang dengan nilai yang sama (early return check).
- Test bahwa `photoVideoOptionsItem` di-add ke `actionBar` dengan right margin 48dp (tidak overlap dengan `otherItem`).

### Property-Based Tests

- Generate kombinasi tab acak × `mediaHeaderVisible` acak — untuk semua kombinasi di mana `isOptionsItemVisible(tab) = false`, `photoVideoOptionsItem` tidak boleh `VISIBLE` saat media header visible.
- Generate click event acak pada `photoVideoOptionsItem` saat berbagai tab aktif — tidak boleh crash (NullPointerException, IndexOutOfBoundsException).
- Generate scroll position acak yang memicu `setMediaHeaderVisible()` berulang kali — state akhir harus konsisten (tidak ada zombie view yang tertinggal visible).

### Integration Tests

- Test full flow: buka profile chat → scroll ke bawah → pindah ke tab Files → tombol filter muncul → klik filter → popup muncul → pilih "Downloaded" → list file difilter.
- Test switching antara tab Files dan tab Photos/Videos — tombol opsi harus muncul di kedua tab, dengan konten popup yang berbeda.
- Test transisi cepat: scroll ke bawah (media header visible) → langsung scroll ke atas → tombol filter harus hilang tanpa overlap dengan `otherItem`.
- Test bahwa tidak ada crash saat `setMediaHeaderVisible()` dipanggil saat layout sedang dalam proses measure/layout.
