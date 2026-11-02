import { createPortfolio1000Scenario } from "../support/scenario.js";

const scenario = createPortfolio1000Scenario("calculate");
export const options = scenario.options;
export const setup = scenario.setup;
export const teardown = scenario.teardown;
export const handleSummary = scenario.handleSummary;
export default scenario.run;
