// The page in jsdom: the first-use guide, navigation and the stored demo state.
// `npm run test:dom`. No browser needed; dialogs get a minimal stand-in.
const test = require("node:test");
const assert = require("node:assert/strict");
const { JSDOM } = require("jsdom");
const { html } = require("./helpers.cjs");

const WELCOME_KEY = "hostpulse.welcome.v1";
const STATE_KEY = "hostpulse.mock.v2";

// jsdom has no modal dialogs; this keeps `open` and the close event, which is all the app uses.
function dialogStandIn(window) {
  const proto = window.HTMLDialogElement.prototype;
  proto.showModal = function () {
    this.setAttribute("open", "");
  };
  proto.close = function () {
    if (!this.hasAttribute("open")) return;
    this.removeAttribute("open");
    this.dispatchEvent(new window.Event("close"));
  };
  window.HTMLElement.prototype.scrollIntoView = () => {};
}

function openPage({ storage = {}, blockStorage = false } = {}) {
  const dom = new JSDOM(html(), {
    url: "http://localhost/",
    runScripts: "dangerously",
    pretendToBeVisual: true,
    beforeParse(window) {
      dialogStandIn(window);
      for (const [key, value] of Object.entries(storage)) window.localStorage.setItem(key, value);
      if (blockStorage)
        Object.defineProperty(window, "localStorage", {
          get() {
            throw new window.DOMException("blocked", "SecurityError");
          },
        });
    },
  });
  const { document } = dom.window;
  const click = (selector) => {
    const element = document.querySelector(selector);
    assert.ok(element, `nothing matches ${selector}`);
    element.click();
  };
  const dialog = document.getElementById("detail-dialog");
  const title = () => document.getElementById("dialog-title")?.textContent;
  const view = () => document.querySelector(".nav-btn[aria-current]")?.dataset.view;
  return { dom, document, window: dom.window, click, dialog, title, view };
}

test("the guide opens by itself on the first visit", () => {
  const page = openPage();
  assert.ok(page.dialog.open);
  assert.equal(page.title(), "Καλώς ήρθες στο HostPulse");
  assert.match(page.dialog.textContent, /1 από 4/);
  assert.equal(page.dialog.querySelectorAll(".welcome-dots span").length, 4);
  assert.equal(page.view(), "home");
});

test("the guide steps forward and back, and its last step opens the recommendations", () => {
  const page = openPage();
  const titles = [page.title()];
  for (let i = 0; i < 3; i++) {
    page.click('#detail-dialog [data-action="welcome-step"]:last-child');
    titles.push(page.title());
  }
  assert.deepEqual(titles, [
    "Καλώς ήρθες στο HostPulse",
    "Τι γράφει ο επισκέπτης",
    "Σου προτείνει τι να κάνεις",
    "Εσύ αποφασίζεις",
  ]);
  assert.match(page.dialog.textContent, /4 από 4/);
  page.click('#detail-dialog [data-action="welcome-step"]');
  assert.equal(page.title(), "Σου προτείνει τι να κάνεις");
  page.click('#detail-dialog [data-action="welcome-step"]:last-child');

  page.click('#detail-dialog [data-action="welcome-close"][data-view="tasks"]');
  assert.equal(page.dialog.open, false);
  assert.equal(page.view(), "tasks");
  assert.equal(page.window.localStorage.getItem(WELCOME_KEY), "seen");
});

test("skipping the guide closes it, remembers it and leaves the demo state alone", () => {
  const page = openPage();
  const before = page.window.localStorage.getItem(STATE_KEY);
  page.click('#detail-dialog .sheet-head [data-action="welcome-close"]');
  assert.equal(page.dialog.open, false);
  assert.equal(page.view(), "home");
  assert.equal(page.window.localStorage.getItem(WELCOME_KEY), "seen");
  assert.equal(page.window.localStorage.getItem(STATE_KEY), before);
  assert.ok(!before.includes("welcome"));
});

test("closing with Esc or a click outside also counts as seen", () => {
  const page = openPage();
  page.dialog.close();
  assert.equal(page.window.localStorage.getItem(WELCOME_KEY), "seen");
});

test("a returning visitor does not see the guide, but can reopen it", () => {
  const page = openPage({ storage: { [WELCOME_KEY]: "seen" } });
  assert.equal(page.dialog.open, false);
  page.click('.help-btn[data-action="welcome-open"]');
  assert.ok(page.dialog.open);
  assert.equal(page.title(), "Καλώς ήρθες στο HostPulse");
  page.click('#detail-dialog .sheet-head [data-action="welcome-close"]');

  page.click('.nav-btn[data-view="profile"]');
  page.click('#main [data-action="welcome-open"]');
  assert.ok(page.dialog.open);
  assert.match(page.dialog.textContent, /1 από 4/);
});

test("without browser storage the app still works and shows the guide", () => {
  const page = openPage({ blockStorage: true });
  assert.ok(page.dialog.open);
  assert.equal(page.document.getElementById("storage-notice").hidden, false);
  page.click('#detail-dialog .sheet-head [data-action="welcome-close"]');
  page.click('.nav-btn[data-view="reviews"]');
  assert.equal(page.view(), "reviews");
});

test("every tab renders its screen", () => {
  const page = openPage({ storage: { [WELCOME_KEY]: "seen" } });
  for (const view of ["home", "reviews", "tasks", "insights", "profile"]) {
    page.click(`.nav-btn[data-view="${view}"]`);
    assert.equal(page.view(), view);
    assert.ok(page.document.getElementById("screen-title")?.textContent, view);
  }
  page.click('.demo-chip[data-action="nav"]');
  assert.ok(page.document.getElementById("lab-form"));
});

test("the plan on the actions tab is the core's plan", () => {
  const page = openPage({ storage: { [WELCOME_KEY]: "seen" } });
  page.click('.nav-btn[data-view="tasks"]');
  const core = page.window.HostPulseCore;
  const stored = JSON.parse(page.window.localStorage.getItem(STATE_KEY));
  const plan = core.buildPlan(core.recommendations(stored), { minutes: 120, budget: 150 });
  const shown = page.document.getElementById("task-results").textContent;
  for (const action of plan.selected) assert.ok(shown.includes(action.title), action.title);
});


test("v2 report export dialog previews PDF and 3 Excel sheets and populates print root", () => {
  const page = openPage({ storage: { [WELCOME_KEY]: "seen" } });
  page.click('#main [data-action="report-open"]');
  assert.ok(page.dialog.open);
  assert.equal(page.title(), "Εξαγωγή PDF & Excel");
  assert.match(page.dialog.textContent, /ΑΝΑΦΟΡΑ ΑΠΟΔΟΣΗΣ/);

  page.click('#detail-dialog [data-action="report-tab"][data-value="excel"]');
  assert.match(page.dialog.textContent, /Συνοπτικά KPIs/);
  page.click('#detail-dialog [data-action="report-sheet"][data-value="1"]');
  assert.match(page.dialog.textContent, /ΕΠΑΝΑΛΑΜΒΑΝΟΜΕΝΑ ΜΟΤΙΒΑ/);

  page.window.print = () => {};
  page.click('#detail-dialog [data-action="export-pdf"]');
  const printRoot = page.document.getElementById("print-report-root");
  assert.match(printRoot.textContent, /Αναλυτικός πίνακας κριτικών/);

  page.click('#detail-dialog [data-action="share-summary"]');
  assert.ok(page.document.getElementById("report-summary-preview"));
});
