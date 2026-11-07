"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { nextDueDate } = require("../dates");
test("daily recurrence handles leap years", () => {
  assert.equal(nextDueDate("2028-02-28", "Täglich"), "2028-02-29");
  assert.equal(nextDueDate("2028-02-29", "Täglich"), "2028-03-01");
});
test("weekly recurrence preserves local calendar day during DST", () => {
  assert.equal(nextDueDate("2026-03-27", "Wöchentlich"), "2026-04-03");
});
test("monthly recurrence clamps end-of-month correctly", () => {
  assert.equal(nextDueDate("2026-01-31", "Monatlich"), "2026-02-28");
  assert.equal(nextDueDate("2028-01-31", "Monatlich"), "2028-02-29");
  assert.equal(nextDueDate("2026-12-31", "Monatlich"), "2027-01-31");
});
test("invalid repeat rejected", () => {
  assert.throws(() => nextDueDate("2026-11-07", "Nie"), /Unknown/);
});
test("invalid calendar date rejected", () => {
  assert.throws(() => nextDueDate("2026-02-30", "Täglich"), /Invalid/);
});
