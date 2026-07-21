# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - photoVideoOptionsItem Tidak Visible di ProfileActivity
  - **CRITICAL**: Test ini HARUS FAIL pada kode yang belum diperbaiki — kegagalan mengkonfirmasi bug ada
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: Test ini mengkodekan perilaku yang diharapkan — akan memvalidasi fix ketika PASS setelah implementasi
  - **GOAL**: Surfacing counterexample yang mendemonstrasikan bug
  - **Scoped PBT Approach**: Scope property ke kasus konkret yang gagal: tab `TAB_FILES` dengan `mediaHeaderVisible = true` di `ProfileActivity`
  - **Bug Condition (isBugCondition)**: `hostActivity IS ProfileActivity AND mediaHeaderVisible = true AND isOptionsItemVisible(currentTab) = true`
  - Buat test yang memanggil `setMediaHeaderVisible(true)` pada `ProfileActivity` dengan tab aktif `TAB_FILES`
  - Assert bahwa `sharedMediaLayout.photoVideoOptionsItem.getVisibility() == View.VISIBLE`
  - Jalankan test pada kode **SEBELUM** diperbaiki
  - **EXPECTED OUTCOME**: Test FAIL (ini membuktikan bug ada — `optionsSearchImageView` yang di-set VISIBLE, bukan `photoVideoOptionsItem`)
  - Dokumentasi counterexample yang ditemukan: misalnya `"setMediaHeaderVisible(true) dengan TAB_FILES → photoVideoOptionsItem.visibility != VISIBLE karena getSearchOptionsItem() mengembalikan optionsSearchImageView"`
  - Tandai task selesai ketika test sudah ditulis, dijalankan, dan kegagalan didokumentasikan
  - _Requirements: 1.1, 1.2_

- [ ] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-Buggy Context Tidak Berubah
  - **IMPORTANT**: Ikuti metodologi observation-first
  - **Preservation Condition (¬isBugCondition)**: `mediaHeaderVisible = false`, ATAU tab yang tidak memenuhi `isOptionsItemVisible()` (`TAB_LINKS`, `TAB_AUDIO`, `TAB_VOICE`, dll.), ATAU konteks bukan `ProfileActivity`
  - Observasi pada kode **UNFIXED**: jalankan `setMediaHeaderVisible(true)` dengan tab `TAB_LINKS` → catat bahwa `photoVideoOptionsItem` tetap `INVISIBLE`
  - Observasi pada kode **UNFIXED**: jalankan `setMediaHeaderVisible(false)` → catat bahwa `otherItem` menjadi `VISIBLE`
  - Observasi pada kode **UNFIXED**: `SharedMediaLayout` di `MediaActivity` tidak dipengaruhi pemanggilan `setMediaHeaderVisible()` dari `ProfileActivity`
  - Tulis property-based test: untuk semua tab di mana `isOptionsItemVisible(tab) = false`, `photoVideoOptionsItem` harus tetap `INVISIBLE`/`GONE` saat `setMediaHeaderVisible(true)` dipanggil
  - Tulis property-based test: `setMediaHeaderVisible(false)` selalu menghasilkan `otherItem.getVisibility() == View.VISIBLE` tanpa overlap dari `photoVideoOptionsItem`
  - Verifikasi semua test PASS pada kode unfixed
  - **EXPECTED OUTCOME**: Tests PASS (mengkonfirmasi baseline behavior yang harus dipreservasi)
  - Tandai task selesai ketika test sudah ditulis, dijalankan, dan passing pada kode unfixed
  - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [ ] 3. Fix untuk dua bug: referensi getSearchOptionsItem() yang salah dan overlap UI photoVideoOptionsItem

  - [ ] 3.1 Hapus penggunaan `mediaOptionsItem` dari `setMediaHeaderVisible()` di `ProfileActivity.java`
    - Hapus baris: `ImageView mediaOptionsItem = sharedMediaLayout.getSearchOptionsItem();`
    - Hapus blok `if (mediaOptionsItem != null) { mediaOptionsItem.setVisibility(View.GONE); }` dari branch `!mediaHeaderVisible`
    - Hapus blok `if (mediaOptionsItem != null) { mediaOptionsItem.setVisibility(View.VISIBLE); }` dari branch `mediaHeaderVisible`
    - Jangan mengubah baris lain di `setMediaHeaderVisible()` — termasuk `sharedMediaLayout.photoVideoOptionsItem.setVisibility(...)`, `otherItem`, dan `animateSearchToOptions()`
    - Pastikan `optionsSearchImageView` masih dikelola sepenuhnya oleh `SharedMediaLayout` melalui `animateSearchToOptions()` yang sudah ada
    - _Bug_Condition: isBugCondition(ctx) WHERE ctx.hostActivity IS ProfileActivity AND ctx.mediaHeaderVisible = true AND isOptionsItemVisible(ctx.currentTab) = true_
    - _Expected_Behavior: sharedMediaLayout.photoVideoOptionsItem.getVisibility() == View.VISIBLE untuk semua ctx yang memenuhi isBugCondition_
    - _Preservation: Tidak mengubah logika visibility untuk tab non-options, otherItem, optionsSearchImageView, dan MediaActivity_
    - _Requirements: 2.1, 2.2, 2.4_

  - [ ] 3.2 Perbaiki posisi `photoVideoOptionsItem` agar tidak overlap dengan `otherItem` di `SharedMediaLayout.java`
    - Temukan baris `addView` untuk `photoVideoOptionsItem` di constructor `SharedMediaLayout`
    - Ubah dari: `actionBar.addView(photoVideoOptionsItem, LayoutHelper.createFrame(48, 56, Gravity.RIGHT | Gravity.BOTTOM))`
    - Menjadi: `actionBar.addView(photoVideoOptionsItem, LayoutHelper.createFrame(48, 56, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 48, 0))`
    - `rightMargin = 48dp` menempatkan `photoVideoOptionsItem` tepat di sebelah kiri `otherItem` (yang berukuran 48dp)
    - Jangan mengubah parameter lain (ukuran 48×56, gravity, atau layout lainnya)
    - _Bug_Condition: photoVideoOptionsItem dan otherItem overlap di action bar karena posisi yang sama_
    - _Expected_Behavior: posisi(photoVideoOptionsItem) TIDAK overlap dengan posisi(otherItem) meskipun keduanya visible bersamaan selama animasi transisi_
    - _Preservation: Ukuran dan gravity photoVideoOptionsItem tetap sama; optionsSearchImageView tidak terpengaruh_
    - _Requirements: 2.3_

  - [ ] 3.3 Verifikasi bug condition exploration test sekarang PASS
    - **Property 1: Expected Behavior** - photoVideoOptionsItem Visible di ProfileActivity Setelah Fix
    - **IMPORTANT**: Jalankan ulang test YANG SAMA dari task 1 — JANGAN tulis test baru
    - Test dari task 1 mengkodekan expected behavior
    - Ketika test ini PASS, itu mengkonfirmasi expected behavior terpenuhi
    - Jalankan bug condition exploration test dari step 1
    - **EXPECTED OUTCOME**: Test PASS (mengkonfirmasi bug telah diperbaiki)
    - _Requirements: 2.1, 2.2, 2.3_

  - [ ] 3.4 Verifikasi preservation tests masih PASS
    - **Property 2: Preservation** - Non-Buggy Context Tidak Berubah Setelah Fix
    - **IMPORTANT**: Jalankan ulang test YANG SAMA dari task 2 — JANGAN tulis test baru
    - Jalankan preservation property tests dari step 2
    - **EXPECTED OUTCOME**: Tests PASS (mengkonfirmasi tidak ada regresi)
    - Konfirmasi semua test masih pass setelah fix (tidak ada zombie view, tidak ada state visibility yang tidak konsisten)

- [ ] 4. Checkpoint — Pastikan semua test pass
  - Jalankan seluruh test suite yang relevan (unit test + property-based test dari task 1 dan 2)
  - Konfirmasi `photoVideoOptionsItem` visible di tab FILES dan PHOTOVIDEO ketika media header visible
  - Konfirmasi `photoVideoOptionsItem` tidak overlap dengan `otherItem`
  - Konfirmasi `optionsSearchImageView` tetap dikelola oleh `SharedMediaLayout` (tidak ada interferensi dari `ProfileActivity`)
  - Konfirmasi tab non-options (LINKS, AUDIO, VOICE) tidak terpengaruh
  - Pastikan semua test pass; tanyakan ke user jika ada pertanyaan yang muncul
