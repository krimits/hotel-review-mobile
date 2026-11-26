# Hotel Pulse — Native Android App (V2: Jetpack Compose + Multi-Property + Action Recommender + PDF/Excel)

Native Android υλοποίηση του **HostPulse / Hotel Pulse** (Kotlin, Jetpack Compose, Material 3) για τη **Φάση 2 (HW2)**. Υποστηρίζει τόσο **άμεση offline δοκιμή (`Demo Mode`) χωρίς ρύθμιση Firebase** όσο και προαιρετική **λειτουργία Cloud** (Google Sign-In, Cloud Firestore, Firebase AI Logic με Gemini).

## Τι περιλαμβάνει (5 καρτέλες + Multi-Property Header)

- **Επιλογέας Καταλύματος & Κέντρο Ειδοποιήσεων (`MainActivity.kt`)**: Μόνιμη μπάρα στην κορυφή για εναλλαγή ανάμεσα σε «Όλα τα καταλύματα (10)» ή μεμονωμένο ελληνικό κατάλυμα (`HotelProperty.CATALOG`), κουμπί καμπάνας με live `Badge` ενεργών alerts και modal **Κέντρο Κρίσιμων Ειδοποιήσεων** (χαμηλές βαθμολογίες με ρυθμιζόμενο όριο `≤ 3.5 / 4.5 / 5.5`, εκπρόθεσμες εργασίες πέραν SLA `24ω / 48ω / 72ω`, επείγοντα χωρίς ανάθεση, σίγαση/επαναφορά και deep-link προς την κριτική ή την ανάθεση), καθώς και adaptive πλοήγηση (`NavigationBar` σε κινητά, `NavigationRail` σε tablets `>= 600.dp`).
- **Σήμερα (`DashboardScreen.kt`)**: Banner ενεργών κρίσιμων ειδοποιήσεων, 4 KPI κάρτες, προεπισκόπηση Ημερήσιου Πλάνου Ενεργειών (`έως 3/3`), κορυφαία επαναλαμβανόμενα μοτίβα, διάγραμμα κατανομής συναισθήματος (Donut Canvas), triage ανά τμήμα ξενοδοχείου και επείγουσες κριτικές.
- **Κριτικές (`ReviewsScreen.kt`, `HostPulseEngine.kt`, `HotelAiService.kt`)**:
  - Αναζήτηση χωρίς διάκριση τόνων, φίλτρα διαλογής 4 κατηγοριών (`Εκκρεμή`, `Ήδη λυμένα`, `Επιθυμίες`, `Θετικά`), φίλτρο «Προς επιβεβαίωση», αρνητικών και εκκρεμών.
  - Προσθήκη νέας κριτικής (Ελληνικά/Αγγλικά) με 4 γρήγορα δείγματα δοκιμής και ζωντανή προεπισκόπηση διαλογής.
  - Υποχρεωτική ανθρώπινη **«Επιβεβαίωση ανάλυσης»** πριν την ανάθεση εργασιών, ξεχωριστή **αποθήκευση απάντησης** (ζεστό / επίσημο ύφος), αντιγραφή στο πρόχειρο, σήμανση τακτοποίησης και διαγραφή με επιβεβαίωση.
- **Ενέργειες (`ActionsScreen.kt`)**:
  - Action Recommender με βαθμολόγηση **Σοβαρότητα × Συχνότητα 30 ημερών + Πρόσφατη εμφάνιση** και ρυθμιζόμενα βάρη (`1–5`).
  - Άπληστο Ημερήσιο Πλάνο έως 3 παρεμβάσεων εντός ρυθμιζόμενου χρόνου (`15–240 λ.`) και προϋπολογισμού (`0–300€`).
  - Καρτέλα Αναθέσεων με αυστηρές μεταβάσεις (`Ανατέθηκε → Σε εξέλιξη → Ολοκληρώθηκε`), ένδειξη **Εκπρόθεσμη · Xω** όταν ξεπεραστεί το όριο SLA, και **εσωτερικές σημειώσεις μεταξύ τμημάτων** (`Συντήρηση → Υποδοχή`, `Υποδοχή → Καθαριότητα` κ.λπ.) με inline composer και μόνιμη αποθήκευση στο `OperationalStateStore`.
- **Μοτίβα (`PatternsScreen.kt`)**: Εντοπισμός επαναλαμβανόμενων αρνητικών θεμάτων σε παράθυρα `7 / 14 / 30 / 60` ημερών με φίλτρο πηγής, σύγκριση με το προηγούμενο ίσο διάστημα (`μ.π.`), ένδειξη μικρού δείγματος (`< 5` αναφορές) και προβολή ακριβών αποσπασμάτων ανά κατάλυμα.
- **Αναφορές PDF & Excel (`ReportsStudioScreen.kt`, `PdfReportGenerator.kt`, `ExcelReportGenerator.kt`, `ReportShareHelper.kt`)**:
  - Φίλτρα περιόδου (`7/14/30/60/90` ημέρες, Όλες, Custom `DateRangePicker`), συναισθήματος, τμημάτων, εκκρεμών, συμπερίληψης Μοτίβων/Ενεργειών και Επιτελικής Σύνοψης.
  - Πολυσέλιδο διανυσματικό A4 **PDF** (Σελ. 1 KPIs & γραφήματα, Σελ. 2 Επαναλαμβανόμενα Μοτίβα & Προτεραιοποιημένες Ενέργειες, Σελ. 3+ Αναλυτικός πίνακας κριτικών) με ενσωματωμένες ελληνικές γραμματοσειρές (`Manrope`, `IBM Plex Sans`) και εντός-εφαρμογής προεπισκόπηση με zoom.
  - Αρχείο **Excel (`.xlsx`)** 3 φύλλων (`Συνοπτικά KPIs`, `Μοτίβα & Ενέργειες`, `Αναλυτικές Κριτικές`) χωρίς εξωτερικές βιβλιοθήκες.
  - Αποθήκευση στη συσκευή (Storage Access Framework) και διαμοιρασμός στην ομάδα μέσω Android Share Sheet (`FileProvider`).
- **Έλεγχοι (`ExampleRobolectricTest.kt` & `firestore.test.js`)**:
  - Robolectric unit tests για τη διαλογή 4 κατηγοριών (συμπεριλαμβανομένων guards για ελληνικά false positives όπως «πάλι»/«ακριβώς»), seeded tasks, `OperationalStateStore`, greedy daily plan, human confirmation gate, επαναλαμβανόμενα μοτίβα και παραγωγή PDF/XLSX.
  - Firestore security rules unit tests (`firestore.test.js` + `firestore.rules`).

## Προεπισκόπηση στο Google AI Studio

Μπορείς να δεις το Android project απευθείας στο AI Studio:
https://ai.studio/apps/3592ba1c-ae80-43cb-84cd-09e1773e5dbd

## Τοπική εκτέλεση σε Android Studio

**Προαπαιτούμενα:** Android Studio (Meerkat ή νεότερο) με Android SDK 36.

1. Άνοιξε τον φάκελο `android-app/` από το Android Studio (**File → Open**).
2. Το `app/build.gradle.kts` ελέγχει αυτόματα αν υπάρχει `debug.keystore` πριν εφαρμόσει custom `debugConfig`, ώστε το debug build να λειτουργεί τοπικά χωρίς χειροκίνητη επεξεργασία.
3. **Άμεση δοκιμή χωρίς Firebase (προτείνεται για αξιολόγηση):**
   - Εκτέλεσε την εφαρμογή σε Android Emulator ή συσκευή και πάτησε **«Δοκιμή Demo (Χωρίς Σύνδεση)»** στην αρχική οθόνη. Όλες οι λειτουργίες (10 καταλύματα, 24 κριτικές, 4-κατηγοριών διαλογή, Ενέργειες, Μοτίβα, PDF & Excel) λειτουργούν 100% τοπικά.
4. **Προαιρετική σύνδεση με δικό σου Firebase project (Cloud Mode — Auth + Firestore + Firebase AI Logic):**
   - Αντέγραψε το `app/google-services.example.json` σε `app/google-services.json` και αντικατάστησε τα στοιχεία με αυτά του δικού σου Firebase project (το `app/google-services.json` εξαιρείται από το git για λόγους ασφαλείας).
   - Πρόσθεσε το SHA-1 του τοπικού σου debug keystore στο Firebase Console για να λειτουργεί το Google Sign-In (`CredentialManager`).
   - Ενεργοποίησε Google Sign-In, Cloud Firestore (`firestore.rules`) και Firebase AI Logic. Το Firebase AI Logic χρησιμοποιεί App Check attestation — **δεν** απαιτείται client-side `GEMINI_API_KEY` στο `.env`.

## Σημειώσεις αρχιτεκτονικής (Φάση 2 → Φάση 3)

- Στη Φάση 2, οι κριτικές και το προφίλ ξενοδοχείου συγχρονίζονται στο Cloud Firestore (`/users/{userId}/reviews/{reviewId}`), ενώ το επιχειρησιακό workflow state (`reviewPropertyMap`, `checkedReviewIds`, `assignedTasks`, βάρη και όρια) αποθηκεύεται τοπικά ανά χρήστη στο `OperationalStateStore` (`SharedPreferences`) ώστε να παραμένει αμετάβλητο και αυστηρό το schema των `firestore.rules`.
- Για τη Φάση 3 προβλέπεται επέκταση του Firestore schema για multi-device συγχρονισμό εργασιών/καταλυμάτων και σύνδεση με το API του `hotel-review-nlp` (βλ. [`../API_INTEGRATION.md`](../API_INTEGRATION.md)).

> **Σημείωση για γρήγορη αξιολόγηση χωρίς Android Studio:** Στο root του αποθετηρίου υπάρχει το διαδραστικό **Web Mockup V2** (`../index.html`) που ανοίγει με διπλό κλικ σε οποιονδήποτε browser χωρίς εγκατάσταση ή σύνδεση, περιλαμβάνοντας τόσο το σύστημα συστάσεων ενεργειών (σοβαρότητα × συχνότητα) όσο και την εξαγωγή αναφορών PDF & Excel (`.xlsx`).
