# HostPulse v0.3 — σύνδεση με hotel-review-nlp

Το mobile πρωτότυπο είναι το περιβάλλον εργασίας του υπευθύνου λειτουργίας.
Το [hotel-review-nlp](https://github.com/krimits/hotel-review-nlp) παρέχει τη
χωριστή ανάλυση συναισθήματος και θεμάτων. Στη v0.3 **δεν γίνονται κλήσεις API**.
Οι πηγές, οι αναλύσεις, οι αναθέσεις και οι απαντήσεις είναι τοπικό mock.

Τα παρακάτω contracts ελέγχθηκαν στον κώδικα του backend στις **22/10/2026**.
Το `docs/PRODUCT_SETUP.md` εξηγεί την εκτέλεση του τοπικού pilot.

## Υπάρχουσες λειτουργίες του backend

| Ανάγκη mobile               | Endpoint                                                           | Contract / όριο                                                                                                         |
| --------------------------- | ------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| Έλεγχος υπηρεσίας           | `GET /health`                                                      | `status`, `model_type`, `model_path`, `device`. Η απόκριση υγείας δεν αποδεικνύει ότι έχει αξιολογηθεί κάθε λειτουργία. |
| Συνολικό δυαδικό συναίσθημα | `POST /predict`                                                    | Request `{"text":"..."}`. Response `label`, nullable `confidence`, `latency_ms`.                                        |
| Μαζικό συναίσθημα           | `POST /predict/batch`                                              | Request `{"texts":["..."]}`. Έως 256 κείμενα. Response `predictions`.                                                   |
| Θέματα και τεκμήρια         | `POST /absa`                                                       | `hotel_id`, `text`, προαιρετικά `review_id`, `source`, `review_date`, `language:"en"`.                                  |
| Μαζική εξαγωγή θεμάτων      | `POST /absa/batch`                                                 | `hotel_id` και `reviews` έως 256 αντικειμένων του ίδιου ξενοδοχείου.                                                    |
| Συστάσεις για ξενοδοχείο    | `GET /hotels/{hotel_id}/recommendations?days=30`                   | `recommendations` με counts, `negative_rate`, nullable `trend`, `priority_score`, `recommendation`.                     |
| Απόσπασμα πίσω από θέμα     | `GET /hotels/{hotel_id}/aspects/{aspect}/evidence?days=30&limit=5` | Πηγή, εξωτερικό `review_id`, ημερομηνία και ακριβές `quote`.                                                            |

Ενδεικτικό **σχήμα** request ABSA, χωρίς πραγματικά στοιχεία επισκέπτη:

```json
{
  "hotel_id": "ACTUAL_HOTEL_ID",
  "review_id": "STABLE_SOURCE_REVIEW_ID",
  "source": "booking",
  "language": "en",
  "text": "The staff were friendly but the room was dirty."
}
```

Το backend αποθηκεύει metadata/hash και σύντομα αποσπάσματα, όχι το πλήρες
κείμενο. Το live mobile δεν μπορεί να ανακτήσει πλήρες review text από αυτούς
τους endpoints: χρειάζεται ξεχωριστός εξουσιοδοτημένος κατάλογος κριτικών ή
σύνδεσμος στην αρχική πηγή. Η εμφάνιση πλήρους κειμένου στο mock στηρίζεται
αποκλειστικά στα ενσωματωμένα συνθετικά δεδομένα.

## Αντιστοίχιση ορολογίας

| Τοπική κατηγορία mock      | Canonical aspect backend |
| -------------------------- | ------------------------ |
| `clean`                    | `cleanliness`            |
| `staff`                    | `staff`                  |
| `location`                 | `location`               |
| `room`                     | `room`                   |
| `food`                     | `food`                   |
| `noise`                    | `noise`                  |
| `value`                    | `value`                  |
| `maintenance`, `amenities` | `facilities`             |

Η οθόνη «Μοτίβα» ομαδοποιεί τις δύο τελευταίες κατηγορίες σε ένα canonical
aspect και μετρά κάθε κριτική μία φορά ανά θέμα. Οι κανόνες του mock δεν είναι
ισοδύναμοι με τον extractor και τις πολιτικές αποθήκευσης του backend.

## Κανόνες για τη live διασύνδεση

- Το `/predict` δίνει τη συνολική ετικέτα. Δεν πρέπει να αποκλείει κριτικές από
  το ABSA επειδή η συνολική εμπειρία είναι θετική: μπορεί να περιέχουν παράπονο.
- Το ABSA response έχει `json_valid`, `salvaged`, `entries_dropped`, `stored`
  και flags ελέγχου quotes. Αποτυχία/απόρριψη πρέπει να οδηγεί σε χειροκίνητο
  έλεγχο, με εμφανή κατάσταση. Δεν αντικαθίσταται σιωπηρά από συνθετικά δεδομένα.
- Η `confidence` του sentiment API είναι μέγιστη softmax πιθανότητα, με
  βαθμονόμηση ακόμη στο roadmap. Δεν εμφανίζεται ως πιθανότητα σωστής
  επιχειρησιακής απόφασης ή ως ακρίβεια του ABSA.
- Το σημερινό `/absa` δέχεται **μόνο αγγλικά**. Η ελληνική υποστήριξη του
  τοπικού mock δεν μεταφέρεται αυτόματα στο live προϊόν.
- Τα `/absa/batch` requests έχουν ένα `hotel_id` και μέχρι 256 κριτικές του
  ίδιου ξενοδοχείου. Το `processing_time_ms` κάθε item είναι null μέσα σε batch.
- Ένα αρνητικό θέμα παραμένει στην ιστορική μέτρηση μετά την επίλυση της
  εργασίας. Η κατάσταση του περιστατικού είναι διαφορετική έννοια από το
  συναίσθημα της αρχικής κριτικής.
- Ο server επιστρέφει 501 όταν δεν έχει aspect store, αντί να εφευρίσκει
  συστάσεις. Το live UI πρέπει να δείχνει αυτή την έλλειψη διαθεσιμότητας.
- Το `/triage` παραμένει **πειραματικό, μη επιλεγμένο ή προωθημένο**. Δεν
  χρησιμοποιείται στο mock ούτε θεωρείται επαληθευμένη αυτόματη ανάθεση.

## Τι λείπει για πραγματικό mobile προϊόν

Οι υπάρχοντες endpoints δεν υλοποιούν πλήρες inbox, live συνδέσεις Booking /
Tripadvisor / Google, κοινό δικαίωμα manager σε όλη την αλυσίδα, καταστάσεις
εργασιών, αναθέσεις προσωπικού, push notifications ή δημοσίευση απαντήσεων.
Αυτές οι λειτουργίες χρειάζονται επιπλέον backend ή επίσημες integrations.

Για τη live φάση χρειάζονται συγκεκριμένη διεύθυνση υπηρεσίας και αντιστοίχιση
των demo properties σε πραγματικά hotel IDs. Η πρόσβαση του pilot είναι scoped
ανά ξενοδοχείο. Η συγκεντρωτική εικόνα αλυσίδας χρειάζεται ελεγχόμενη server-side
σύνθεση των εξουσιοδοτημένων ξενοδοχείων και δικό της API contract.
Τα API keys δεν ενσωματώνονται στο HTML ή στο repository. Το mock δεν τα ζητά.

Η ανάλυση χιλιάδων κριτικών ανά ημέρα, η ακρίβεια ABSA, οι λειτουργίες πολλών
χρηστών και το hosted latency χρειάζονται δοκιμές στο πραγματικό deployment.
Οι μετρήσεις του ταξινομητή δεν πιστοποιούν την κλίμακα του mobile προϊόντος.

## Πηγές ελέγχου

- [Serving schemas](https://github.com/krimits/hotel-review-nlp/blob/main/src/reviewnlp/serving/schemas.py), blob `de09fefd3c169e5d73fa1e52a0f4f9037fdf1788`.
- [ABSA schemas](https://github.com/krimits/hotel-review-nlp/blob/main/src/reviewnlp/serving/absa_schemas.py), blob `86d257641b34d7b7a76ddbd99c20f4dabf8e901b`.
- [ABSA router](https://github.com/krimits/hotel-review-nlp/blob/main/src/reviewnlp/serving/absa_router.py), blob `4de2966b18fcb14489b3c826f7e21b4f749279ce`.
- [Analytics router](https://github.com/krimits/hotel-review-nlp/blob/main/src/reviewnlp/serving/analytics_router.py), blob `e9a6ca5bcf9bdfeecf3bf0c8dd6bb3864b444f42`.
- [Local pilot setup](https://github.com/krimits/hotel-review-nlp/blob/main/docs/PRODUCT_SETUP.md), blob `d991818687c331547652e17a662a2691cdd21cf6`.

Οι blob hashes καταγράφουν το υλικό που ελέγχθηκε. Τα links `main` μπορεί να
αλλάξουν στο μέλλον, οπότε τα contracts χρειάζονται νέο έλεγχο πριν από σύνδεση.
