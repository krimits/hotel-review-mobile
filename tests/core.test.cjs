// Core logic: rule-based analysis, recommendations, the plan and the demo state.
// Runs the page's script in Node without a DOM: `npm test`.
const test = require("node:test");
const assert = require("node:assert/strict");
const { loadCore, clone } = require("./helpers.cjs");

const core = loadCore();

test("the seed is the documented scenario and passes its own validation", () => {
  const data = core.seedState();
  assert.equal(core.PROPS.length, 10);
  assert.equal(data.reviews.length, 39);
  assert.equal(core.DEMO_DATE, "2026-10-22");
  assert.equal(core.STORAGE_KEY, "hostpulse.mock.v2");
  assert.deepEqual(
    clone(data.tasks.map((t) => [t.key, t.status])),
    [
      ["3:maintenance", "in_progress"],
      ["6:clean", "open"],
    ],
  );
  assert.equal(core.validStoredState(clone(data)), true);
});

test("stored state that does not match the schema is refused", () => {
  const data = core.seedState();
  const broken = [
    (d) => (d.schema = 1),
    (d) => (d.reviews[0].stars = 9),
    (d) => (d.reviews[1].id = d.reviews[0].id),
    (d) => (d.reviews[0].p = "nowhere"),
    (d) => (d.tasks[0].status = "lost"),
    (d) => (d.tasks[0].department = "Λογιστήριο"),
    (d) => (d.replies = []),
    (d) => (d.settings.weekly = "yes"),
  ];
  for (const breakIt of broken) {
    const copy = clone(data);
    breakIt(copy);
    assert.equal(core.validStoredState(copy), false, breakIt.toString());
  }
  assert.equal(core.validStoredState(null), false);
});

test("analysis separates pending, resolved, hypothetical and praise", () => {
  const cases = [
    ["The room was dirty but the staff were friendly.", "real_pending"],
    ["Ο κλιματισμός ήταν χαλασμένος, αλλά διορθώθηκε την ίδια μέρα.", "real_resolved"],
    ["Θα προτιμούσα μεγαλύτερο ψυγείο.", "hypothetical"],
    ["Υπέροχη διαμονή, πεντακάθαρο δωμάτιο.", "positive_comment"],
    ["asdf qwer", "insufficient"],
  ];
  for (const [text, triage] of cases) assert.equal(core.analyze(text).triage, triage, text);
  // Only a real, unresolved problem becomes pending work.
  assert.equal(core.analyze(cases[1][0]).pending.length, 0);
  assert.equal(core.analyze(cases[2][0]).pending.length, 0);
});

test("every issue quotes the review exactly", () => {
  const text = "Great location, but there was mold in the shower and the breakfast was cold.";
  const result = core.analyze(text);
  assert.ok(result.issues.length >= 1);
  for (const issue of result.issues) assert.ok(text.includes(issue.quote), issue.quote);
});

test("a positive review can still carry a pending complaint", () => {
  const result = core.analyze("Wonderful stay and lovely staff, but the WiFi is not working.");
  assert.equal(result.triage, "real_pending");
  assert.deepEqual(
    clone(result.pending.map((i) => i.category)),
    ["maintenance"],
  );
});

test("mould, leaks, hot water and air conditioning are marked urgent", () => {
  for (const text of ["There was mold in the shower.", "No hot water in the morning.", "The air conditioning is broken."]) {
    assert.ok(
      core.analyze(text).pending.some((i) => i.severity === 3),
      text,
    );
  }
  assert.ok(core.analyze("The breakfast was bad.").pending.every((i) => i.severity === 2));
});

test("recommendations are ranked by score and skip work already assigned", () => {
  const data = core.seedState();
  const recs = core.recommendations(data);
  assert.ok(recs.length > 0);
  for (let i = 1; i < recs.length; i++) assert.ok(recs[i - 1].score >= recs[i].score);
  const assigned = new Set(data.tasks.map((t) => t.key));
  assert.ok(recs.every((r) => !assigned.has(r.key)));
  // Every recommendation names a department and the quote behind it.
  assert.ok(recs.every((r) => r.department && r.quote && r.title));
});

test("the severity weight changes the order", () => {
  const data = core.seedState();
  const bySeverity = core.recommendations(data, "all", { severity: 5, recurrence: 1 });
  const byRecurrence = core.recommendations(data, "all", { severity: 1, recurrence: 5 });
  assert.notDeepEqual(
    bySeverity.slice(0, 5).map((r) => r.key),
    byRecurrence.slice(0, 5).map((r) => r.key),
  );
  assert.equal(bySeverity[0].severity, 3);
});

test("a property filter keeps only that property's recommendations", () => {
  const recs = core.recommendations(core.seedState(), "p1");
  assert.ok(recs.every((r) => r.property === "p1"));
});

test("the plan holds at most three actions within time and budget together", () => {
  const recs = core.recommendations(core.seedState());
  for (const limits of [
    { minutes: 120, budget: 150 },
    { minutes: 30, budget: 0 },
    { minutes: 480, budget: 1000 },
  ]) {
    const plan = core.buildPlan(recs, limits);
    assert.ok(plan.selected.length <= 3);
    assert.ok(plan.minutes <= limits.minutes && plan.budget <= limits.budget);
    assert.equal(plan.minutes, plan.selected.reduce((sum, a) => sum + a.effort, 0));
    assert.equal(plan.budget, plan.selected.reduce((sum, a) => sum + a.cost, 0));
  }
  assert.equal(core.buildPlan(recs, { minutes: 0, budget: 0 }).selected.length, 0);
});

test("an assignment needs a confirmed analysis and happens once", () => {
  const data = core.seedState();
  const action = core.recommendations(data)[0];
  assert.equal(data.checked[action.reviewId], undefined);
  assert.equal(core.approveAction(data, action), false);
  assert.equal(core.markReviewed(data, action.reviewId), true);
  assert.equal(core.approveAction(data, action), true);
  assert.equal(core.approveAction(data, action), false);
  assert.ok(!core.recommendations(data).some((r) => r.key === action.key));
  assert.equal(core.markReviewed(data, 99999), false);
});

test("tasks only move forward: open, in progress, done", () => {
  const data = core.seedState();
  const open = data.tasks.find((t) => t.status === "open");
  assert.equal(core.transitionTask(data, open.id, "in_progress"), true);
  assert.equal(core.transitionTask(data, open.id, "open"), false);
  assert.equal(core.transitionTask(data, open.id, "done"), true);
  assert.equal(core.transitionTask(data, open.id, "in_progress"), false);
  assert.equal(core.transitionTask(data, "no-such-task", "done"), false);
});

test("a review is done only when all its pending issues are done", () => {
  const data = core.seedState();
  const review = data.reviews.find((r) => r.id === 6);
  assert.equal(core.reviewStatus(data, review), "assigned");
  const task = data.tasks.find((t) => t.key === "6:clean");
  core.transitionTask(data, task.id, "done");
  const pending = core.analyze(review.text).pending.length;
  assert.equal(core.reviewStatus(data, review), pending === 1 ? "done" : "awaiting");
});

test("topic signals count each review once per topic and refuse other windows", () => {
  const data = core.seedState();
  const signals = core.topicSignals(data, "all", 30);
  assert.ok(signals.length > 0);
  for (const s of signals) {
    assert.equal(new Set(s.reviewIds).size, s.reviewIds.length);
    assert.equal(s.negativeCount, s.reviewIds.length);
    assert.ok(s.negativeRate > 0 && s.negativeRate <= 1);
    assert.ok(s.evidence.every((item) => s.reviewIds.includes(item.reviewId)));
  }
  assert.equal(core.topicSignals(data, "all", 9).length, 0);
});

test("sources, languages and external ids are stable", () => {
  const data = core.seedState();
  const counts = core.sourceCounts(data);
  assert.equal(counts.reduce((sum, c) => sum + c.count, 0), data.reviews.length);
  assert.equal(core.languageOf({ text: "Πολύ καλό" }), "el");
  assert.equal(core.languageOf({ text: "Very good" }), "en");
  assert.equal(core.languageOf({ text: "12345" }), "und");
  assert.equal(core.externalIdOf({ id: 7, text: "" }), `${core.sourceOf({ id: 7 })}-demo-0007`);
});


test("v2 report data filters reviews and includes KPIs, patterns and prioritized actions", () => {
  const data = core.seedState();
  const full = core.buildReportData(data, { property: "all", days: "all" });
  assert.equal(full.kpis.total, data.reviews.length);
  assert.ok(full.kpis.averageStars > 1 && full.kpis.averageStars <= 5);
  assert.ok(full.signals.length > 0);
  assert.ok(full.recommendations.length > 0);
  assert.equal(full.rows.length, data.reviews.length);

  const pendingOnly = core.buildReportData(data, { property: "p1", onlyPending: true });
  assert.ok(pendingOnly.rows.every((r) => r.propertyId === "p1"));
  assert.ok(pendingOnly.rows.every((r) => r.status !== "done" && r.status !== "no_action"));
});

test("v2 xlsx generator produces a valid 3-sheet OOXML zip with Greek UTF-8 content", () => {
  const data = core.seedState();
  const report = core.buildReportData(data, { property: "all", days: "all" });
  const bytes = core.buildXlsxBytes(report);
  assert.ok(ArrayBuffer.isView(bytes));
  assert.ok(bytes.length > 4000);
  // ZIP local file header signature PK\x03\x04
  assert.equal(bytes[0], 0x50);
  assert.equal(bytes[1], 0x4b);
  assert.equal(bytes[2], 0x03);
  assert.equal(bytes[3], 0x04);
  const text = Buffer.from(bytes).toString("utf8");
  assert.ok(text.includes("[Content_Types].xml"));
  assert.ok(text.includes("xl/workbook.xml"));
  assert.ok(text.includes("xl/worksheets/sheet1.xml"));
  assert.ok(text.includes("xl/worksheets/sheet2.xml"));
  assert.ok(text.includes("xl/worksheets/sheet3.xml"));
  assert.ok(text.includes("Συνοπτικά KPIs"));
  assert.ok(text.includes("Μοτίβα &amp; Ενέργειες"));
  assert.ok(text.includes("Αναλυτικές Κριτικές"));
});

test("v2 printable PDF HTML and team summary text include core KPIs and actions", () => {
  const data = core.seedState();
  const report = core.buildReportData(data, { property: "all", days: 30 });
  const htmlDoc = core.buildPrintableReportHTML(report);
  assert.ok(htmlDoc.includes("ΑΝΑΦΟΡΑ ΑΠΟΔΟΣΗΣ"));
  assert.ok(htmlDoc.includes("Προτεραιοποιημένες προτάσεις ενεργειών"));
  const summary = core.buildReportSummaryText(report);
  assert.ok(summary.includes("ΑΝΑΦΟΡΑ ΑΠΟΔΟΣΗΣ HOSTPULSE"));
  assert.ok(summary.includes("ΠΡΟΤΕΡΑΙΟΠΟΙΗΜΕΝΕΣ ΠΡΟΤΑΣΕΙΣ ΕΝΕΡΓΕΙΩΝ"));
});
