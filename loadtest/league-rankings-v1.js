import { createScenario } from './league-rankings-scenario.js';

const scenario = createScenario('v1');
export const options = scenario.options;
export const setup = scenario.setup;
export const handleSummary = scenario.handleSummary;
export default scenario.run;
