"use strict";

/** ISO calendar dates stay stable across time zones and daylight-saving transitions. */
function nextDueDate(isoDate, repeat, baseDate = new Date()) {
  const candidate = /^\d{4}-\d{2}-\d{2}$/.test(isoDate || "") ? isoDate : baseDate.toISOString().slice(0, 10);
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(candidate);
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day));
  if (Number.isNaN(date.getTime()) || date.toISOString().slice(0,10) !== candidate) {
    throw new Error("Invalid date");
  }
  if (repeat === "Täglich") date.setUTCDate(date.getUTCDate() + 1);
  else if (repeat === "Wöchentlich") date.setUTCDate(date.getUTCDate() + 7);
  else if (repeat === "Monatlich") {
    // Clamp 31st -> last valid day of the next month.
    const nextMonth = new Date(Date.UTC(year, month, 1));
    const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
    nextMonth.setUTCDate(Math.min(day, lastDay));
    return nextMonth.toISOString().slice(0, 10);
  } else throw new Error("Unknown repeat frequency");
  return date.toISOString().slice(0, 10);
}
module.exports = { nextDueDate };
