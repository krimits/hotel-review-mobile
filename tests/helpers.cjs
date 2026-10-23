// Shared loaders for the tests. The app is one file, index.html, with one inline script.
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const INDEX = path.join(__dirname, "..", "index.html");
const html = () => fs.readFileSync(INDEX, "utf8");

// Runs the inline script without a DOM. It publishes globalThis.HostPulseCore and
// stops before the UI code, so the analysis, recommendation and state logic can
// be tested directly.
function loadCore() {
  const source = html();
  const start = source.indexOf("<script>") + "<script>".length;
  const code = source.slice(start, source.lastIndexOf("</script>"));
  const context = vm.createContext({});
  vm.runInContext(code, context, { filename: "index.html" });
  return context.HostPulseCore;
}

// A plain copy in this realm: a test can mutate it without touching the seed, and
// deepStrictEqual can compare it (arrays made inside the vm have another prototype).
const clone = (value) => JSON.parse(JSON.stringify(value));

module.exports = { INDEX, html, loadCore, clone };
