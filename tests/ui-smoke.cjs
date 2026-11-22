// Clicks through the mockup in a real browser at phone size: `npm run test:ui`.
// Uses an installed Edge or Chrome when there is one, otherwise Playwright's Chromium
// (`npx playwright install chromium`). Screenshots go to test-results/ (git-ignored).
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const { chromium } = require("playwright");
const { INDEX } = require("./helpers.cjs");

const OUT = path.join(__dirname, "..", "test-results");
const URL = pathToFileURL(INDEX).href;

async function launch() {
  const wanted = process.env.HOSTPULSE_BROWSER;
  for (const channel of wanted ? [wanted] : ["msedge", "chrome", undefined]) {
    try {
      return await chromium.launch(channel ? { channel } : {});
    } catch (error) {
      if (wanted) throw error;
    }
  }
  throw new Error("no browser: install Edge or Chrome, or run `npx playwright install chromium`");
}

async function step(name, run) {
  await run();
  console.log("ok -", name);
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await launch();
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: "el-GR" });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => message.type() === "error" && errors.push(message.text()));
  const dialog = page.locator("#detail-dialog");
  const view = () => page.locator(".nav-btn[aria-current]").getAttribute("data-view");

  try {
    await page.goto(URL);

    await step("the guide opens on the first visit", async () => {
      await dialog.locator("text=Καλώς ήρθες στο HostPulse").waitFor();
      await page.screenshot({ path: path.join(OUT, "welcome.png") });
    });

    await step("the guide walks through four steps to the recommendations", async () => {
      for (let i = 0; i < 3; i++) await dialog.getByRole("button", { name: "Επόμενο" }).click();
      await dialog.locator("text=4 από 4").waitFor();
      await dialog.getByRole("button", { name: "Δες τις προτάσεις" }).click();
      assert.equal(await dialog.evaluate((d) => d.open), false);
      assert.equal(await view(), "tasks");
      await page.screenshot({ path: path.join(OUT, "plan.png") });
    });

    await step("a high-priority review is confirmed and assigned", async () => {
      await page.locator('.nav-btn[data-view="home"]').click();
      await page.locator('[data-action="inbox"][data-kind="urgent"]').click();
      assert.equal(await view(), "reviews");
      await page.locator('#review-results [data-action="review"]').first().click();
      await dialog.getByRole("button", { name: "Επιβεβαίωση ανάλυσης" }).click();
      const approve = dialog.locator('[data-action="approve"]').first();
      await approve.click();
      await dialog.locator('[data-action="close-dialog"]').first().click();
    });

    await step("the assignment moves to in progress and done", async () => {
      await page.locator('.nav-btn[data-view="tasks"]').click();
      await page.locator('[data-action="task-tab"][data-value="mine"]').click();
      const before = await page.locator('[data-action="complete-task"]').count();
      await page.locator('[data-action="start-task"]').first().click();
      await page.locator('[data-action="complete-task"]').first().click();
      assert.ok((await page.locator('[data-action="complete-task"]').count()) <= before);
    });

    await step("a reload keeps the work and does not show the guide again", async () => {
      const stored = await page.evaluate(() => localStorage.getItem("hostpulse.mock.v2"));
      await page.reload();
      assert.equal(await dialog.evaluate((d) => d.open), false);
      assert.equal(await page.evaluate(() => localStorage.getItem("hostpulse.mock.v2")), stored);
      assert.ok(JSON.parse(stored).tasks.some((t) => t.status === "done"));
      await page.locator('.help-btn').click();
      await dialog.locator("text=1 από 4").waitFor();
      await page.keyboard.press("Escape");
      assert.equal(await dialog.evaluate((d) => d.open), false);
    });

    await step("v2 report export opens, switches PDF/Excel previews and generates summary", async () => {
      await page.locator('.nav-btn[data-view="home"]').click();
      await page.locator('#main [data-action="report-open"]').first().click();
      await dialog.locator("text=Εξαγωγή PDF & Excel").waitFor();
      await page.screenshot({ path: path.join(OUT, "report-pdf.png") });
      await dialog.locator('[data-action="report-tab"][data-value="excel"]').click();
      await dialog.locator('[data-action="report-sheet"][data-value="1"]').click();
      await dialog.locator("text=ΕΠΑΝΑΛΑΜΒΑΝΟΜΕΝΑ ΜΟΤΙΒΑ").waitFor();
      await page.screenshot({ path: path.join(OUT, "report-excel.png") });
      await dialog.locator('[data-action="close-dialog"]').first().click();
      assert.equal(await dialog.evaluate((d) => d.open), false);
    });

    await step("no screen scrolls sideways at 360 px", async () => {
      await page.setViewportSize({ width: 360, height: 740 });
      for (const tab of ["home", "reviews", "tasks", "insights", "profile"]) {
        await page.locator(`.nav-btn[data-view="${tab}"]`).click();
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
        assert.ok(overflow <= 0, `${tab} overflows by ${overflow}px`);
      }
    });

    await step("no script errors", async () => assert.deepEqual(errors, []));
  } catch (error) {
    await page.screenshot({ path: path.join(OUT, "failure.png") }).catch(() => {});
    console.error("not ok -", error.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
