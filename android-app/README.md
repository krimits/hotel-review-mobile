# Hotel Pulse — Native Android App (Jetpack Compose + Firebase + PDF/Excel Reports)

Native Android υλοποίηση του **HostPulse / Hotel Pulse** (Kotlin, Jetpack Compose, Material 3, Firebase Auth, Cloud Firestore, Firebase AI Logic με Gemini) που δημιουργήθηκε ως μέρος της **2ης έκδοσης (HW2)** με έμφαση στη **διαχείριση κριτικών, AI triage και εξαγωγή αναφορών PDF & Excel (.xlsx)**.

## Τι περιλαμβάνει

- **Επισκόπηση (`DashboardScreen.kt`)**: KPIs (Μέση βαθμολογία, σύνολο κριτικών, θετικό κλίμα %, επείγοντα εκκρεμή), διάγραμμα κατανομής συναισθήματος, triage παραπόνων ανά τμήμα ξενοδοχείου και λίστα επειγουσών κριτικών.
- **Κριτικές & AI (`ReviewsScreen.kt`, `HotelAiService.kt`)**: Εισαγωγή ελληνικών και αγγλικών κριτικών με AI ανάλυση συναισθήματος, ανάθεση τμημάτων, key highlights και προτεινόμενες απαντήσεις (ζεστό / επίσημο ύφος).
- **Στούντιο Αναφορών PDF & Excel (`ReportsStudioScreen.kt`, `PdfReportGenerator.kt`, `ExcelReportGenerator.kt`, `ReportShareHelper.kt`)**:
  - Φίλτρα περιόδου (7/30/90 ημέρες, Όλες, Custom date range), συναισθήματος, τμημάτων και εκκρεμών.
  - Παραγωγή πολυσέλιδου vector A4 **PDF** με γραφήματα, KPIs, προαιρετική επιτελική σύνοψη AI και πίνακα κριτικών.
  - Παραγωγή αρχείου **Excel (`.xlsx`)** 3 φύλλων (`Συνοπτικά KPIs`, `Τάσεις Τμημάτων`, `Αναλυτικές Κριτικές`).
  - Ζωντανή προεπισκόπηση μέσα στην εφαρμογή, αποθήκευση στη συσκευή (SAF) και διαμοιρασμός στην ομάδα μέσω Android Share Sheet (`FileProvider`).
- **Έλεγχοι (`ExampleRobolectricTest.kt`)**: Robolectric test που επαληθεύει τα KPIs, το multi-page PDF rendering, την παραγωγή `.xlsx` 3 φύλλων με ελληνικά UTF-8 και το φιλτράρισμα αναφορών.

## Προεπισκόπηση στο Google AI Studio

Μπορείς να δεις το Android project απευθείας στο AI Studio:
https://ai.studio/apps/3592ba1c-ae80-43cb-84cd-09e1773e5dbd

## Τοπική εκτέλεση σε Android Studio

**Προαπαιτούμενα:** Android Studio (Meerkat ή νεότερο) με Android SDK 36.

1. Άνοιξε τον φάκελο `android-app/` από το Android Studio (**File → Open**).
2. Για σύνδεση με δικό σου Firebase project (Auth + Firestore + Firebase AI Logic):
   - Αντέγραψε το `app/google-services.example.json` σε `app/google-services.json` και αντικατάστησε τα στοιχεία με αυτά του δικού σου Firebase project (το `app/google-services.json` εξαιρείται από το git για λόγους ασφαλείας).
   - Ενεργοποίησε Google Sign-In, Cloud Firestore (`firestore.rules`) και Firebase AI Logic.
   - Το Firebase AI Logic χρησιμοποιεί App Check attestation — **δεν** απαιτείται client-side `GEMINI_API_KEY` στο `.env`.
3. Το `app/build.gradle.kts` ελέγχει αυτόματα αν υπάρχει `debug.keystore` πριν εφαρμόσει custom `debugConfig`, ώστε το debug build να λειτουργεί τοπικά χωρίς χειροκίνητη επεξεργασία.
4. Εκτέλεσε την εφαρμογή σε Android Emulator ή φυσική συσκευή.

> **Σημείωση για γρήγορη αξιολόγηση χωρίς Android Studio:** Στο root του αποθετηρίου υπάρχει το διαδραστικό **Web Mockup V2** (`../index.html`) που ανοίγει με διπλό κλικ σε οποιονδήποτε browser χωρίς εγκατάσταση ή σύνδεση, περιλαμβάνοντας τόσο το σύστημα συστάσεων ενεργειών (σοβαρότητα × συχνότητα) όσο και την εξαγωγή αναφορών PDF & Excel (.xlsx).
