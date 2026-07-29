[OPEN] multi-forward-crash

## Ringkasan
- Gejala: saat forward banyak pesan (lebih dari 1) aplikasi crash atau hang/ANR.
- Target: identifikasi titik bottleneck/exception dengan bukti runtime, lalu buat fix minimal.

## Hipotesis (falsifiable)
1) NPE saat forward multi pesan karena ada `MessageObject` dengan `messageOwner == null` atau field lain null di jalur Special Forward (mis. akses `message.messageOwner.grouped_id`).
2) Hang/ANR karena pekerjaan berat O(N pesan × M tujuan) terjadi di main thread (sorting, rebuild grouped, atau loop `prepareSendingDocuments` pada mode forwardAsFile).
3) Crash karena OOM/memory spike saat forward banyak media (alokasi list besar/bitmap/thumbnail) di jalur forward/copy-forward.
4) “Hang” yang dirasakan user sebenarnya adalah forward tertunda yang tidak pernah selesai karena state map `delayedRestrictedForwards` tidak pernah ter-drain (key mismatch event fileLoaded vs attachFileName).
5) Callback proses updates besar dari `TL_messages_forwardMessages` memicu loop mahal (remove dalam loop), menyebabkan freeze sementara.

## Rencana Bukti
- Tambahkan instrumentation log di entry point forward (ChatActivity), forward helper (SendMessagesHelper), dan SpecialForwardActivity (fan-out) untuk:
  - ukuran list pesan, jumlah target dialog, flags mode (forwardAsFile/copy-forward)
  - timing per fase (ms)
  - deteksi kondisi null sebelum titik akses yang rawan crash
  - capture exception top-level di jalur forward UI action

## Status
- [ ] Instrumentation ditambahkan
- [ ] Repro didapat + log pre-fix
- [ ] Akar masalah terkonfirmasi
- [ ] Fix minimal
- [ ] Verifikasi post-fix

