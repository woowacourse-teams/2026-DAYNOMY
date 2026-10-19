import { SharedArray } from "k6/data";
import { readConfig } from "./support/prepare.js";
import { createScenario } from "./support/scenario.js";

const config = readConfig("v1");
const accounts = new SharedArray("portfolio-accounts", () =>
  JSON.parse(open(__ENV.ACCOUNTS_FILE || "./.local/accounts.json")).sort(
    (a, b) => a.testNumber - b.testNumber,
  ),
);
const scenario = createScenario(
  config,
  accounts.slice(0, 50),
  accounts.slice(50),
);
export const options = scenario.options;
export const setup = scenario.setup;
export const handleSummary = scenario.handleSummary;
export default scenario.run;
