import { SharedArray } from "k6/data";
import { createScenario } from "./support/scenario.js";

const config = JSON.parse(open(__ENV.CONFIG_FILE || "./loadtest-config.json"));
config.version = "v1";
const users = new SharedArray("measurement-users", () =>
  JSON.parse(open("./.local/users.json")).sort(
    (a, b) => a.testNumber - b.testNumber,
  ),
);
const scenario = createScenario(
  config,
  users,
  JSON.parse(open("./.local/warmup-users.json")),
);
export const options = scenario.options;
export const setup = scenario.setup;
export const handleSummary = scenario.handleSummary;
export default scenario.run;
