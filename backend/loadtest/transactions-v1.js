import { createTransactionScenario } from "./transaction-scenario.js";

const test = createTransactionScenario("v1");

export const options = test.options;
export default test.run;
export const handleSummary = test.handleSummary;
