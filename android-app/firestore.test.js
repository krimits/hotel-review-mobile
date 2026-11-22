const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_hotel_mgr";
const BOB_UID = "bob_hotel_mgr";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validReviewPayload(userId, createdDate) {
  return {
    userId,
    guestName: "Ελένη Παπαδοπούλου",
    source: "BOOKING",
    language: "EL",
    rating: 8.5,
    stayDateMillis: 1794800000000,
    reviewText: "Εξαιρετική θέα και ευγενικό προσωπικό, αλλά το Wi-Fi στο δωμάτιο ήταν αργό.",
    sentiment: "POSITIVE",
    sentimentScore: 78,
    priority: "MEDIUM",
    departments: ["STAFF_SERVICE", "FACILITIES_WIFI"],
    keyHighlights: ["Ευγενικό προσωπικό", "Αργό Wi-Fi στο δωμάτιο"],
    summaryLine: "Θετική διαμονή με παρατήρηση για την ταχύτητα του Wi-Fi.",
    suggestedReply: "Σας ευχαριστούμε θερμά κα Παπαδοπούλου για τα καλά σας λόγια!",
    resolved: false,
    createdAt: createdDate,
    updatedAt: createdDate,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write profiles or reviews", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("reviews").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reviews")
      .doc("rev_1")
      .set(validReviewPayload(ALICE_UID, ts))
  );
});

test("Authenticated owner can create and update HotelProfile; cannot list all users", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      hotelName: "Aegean Horizon Suites",
      hotelLocation: "Σαντορίνη, Ελλάδα",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      hotelName: "Aegean Horizon Boutique Hotel",
      hotelLocation: "Οία, Σαντορίνη",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Listing root /users must fail (no blanket list rule)
  await assertFails(aliceDb.collection("users").get());
});

test("Authenticated owner: Kotlin-mirrored review create, list, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Empty subcollection list must succeed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("reviews").get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reviews")
      .doc("rev_1")
      .set(validReviewPayload(ALICE_UID, created))
  );

  // Partial update Action 1: mark resolved & update reply
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reviews")
      .doc("rev_1")
      .update({
        resolved: true,
        suggestedReply: "Ευχαριστούμε πολύ! Αναβαθμίσαμε ήδη τον εξοπλισμό Wi-Fi.",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Query matching Kotlin observeReviews ordered by stayDateMillis
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reviews")
      .orderBy("stayDateMillis", "desc")
      .get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reviews")
      .doc("rev_1")
      .delete()
  );
});

test("Security bounds: reject cross-user access, extra @DocumentId 'id' field, invalid enums, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reviews")
      .doc("alice_rev")
      .set(validReviewPayload(ALICE_UID, created))
  );

  // Cross-user read, list, update, delete must fail
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reviews")
      .doc("alice_rev")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("reviews").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reviews")
      .doc("alice_rev")
      .update({
        resolved: true,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Extra "id" field or shadow field must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reviews")
      .doc("rev_bad_id")
      .set({
        ...validReviewPayload(ALICE_UID, created),
        id: "rev_bad_id",
      })
  );

  // Invalid department code must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reviews")
      .doc("rev_bad_dept")
      .set({
        ...validReviewPayload(ALICE_UID, created),
        departments: ["UNKNOWN_DEPT"],
      })
  );

  // Mutating immutable createdAt must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reviews")
      .doc("alice_rev")
      .update({
        createdAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Future timestamp must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("reviews")
      .doc("rev_future")
      .set(validReviewPayload(ALICE_UID, new Date(Date.now() + 60000)))
  );
});
