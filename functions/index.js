"use strict";
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { nextDueDate } = require("./dates");
initializeApp();
const db = getFirestore();
const region = "europe-west1";

async function notifyMembers(householdId, actorUid, title, body) {
  const [members, devices] = await Promise.all([
    db.collection("households").doc(householdId).collection("members").get(),
    db.collection("households").doc(householdId).collection("devices").get(),
  ]);
  const optOut = new Set(members.docs.filter(d => d.data().notifyComplete === false).map(d => d.id));
  const recipients = devices.docs.filter(d => d.id !== actorUid && !optOut.has(d.id) && typeof d.data().token === "string" && d.data().token);
  if (!recipients.length) return;
  for (let i = 0; i < recipients.length; i += 500) {
    const batch = recipients.slice(i, i + 500);
    const response = await getMessaging().sendEachForMulticast({
      tokens: batch.map(doc => doc.data().token),
      data: { title: title.slice(0, 100), body: body.slice(0, 200), householdId },
      android: { priority: "high" },
    });
    await Promise.all(response.responses.map(async (result, index) => {
      if (!result.success && ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(result.error?.code)) {
        // Delete only if it is still the token for which sending failed.
        const ref = batch[index].ref;
        await db.runTransaction(async tx => {
          const current = await tx.get(ref);
          if (current.exists && current.data().token === batch[index].data().token) tx.delete(ref);
        });
      }
    }));
  }
}

exports.onShoppingCreated = onDocumentCreated({ document: "households/{householdId}/shoppingItems/{itemId}", region }, async event => {
  const data = event.data?.data();
  if (!data) return;
  await notifyMembers(event.params.householdId, data.createdBy, "Neu auf der Einkaufsliste", `${data.name || "Ein Artikel"} wurde hinzugefügt`);
});
exports.onShoppingCompleted = onDocumentUpdated({ document: "households/{householdId}/shoppingItems/{itemId}", region }, async event => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after || before.bought === after.bought) return;
  const who = after.doneByName || "Jemand";
  await notifyMembers(event.params.householdId, after.doneBy, after.bought ? "Einkauf erledigt" : "Wieder auf der Liste",
    after.bought ? `${who} hat ${after.name} besorgt` : `${after.name} ist wieder offen`);
});
exports.onTaskCompleted = onDocumentUpdated({ document: "households/{householdId}/tasks/{taskId}", region }, async event => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after || before.done === after.done) return;
  const who = after.doneByName || "Jemand";
  await notifyMembers(event.params.householdId, after.doneBy, after.done ? "Haushaltsaufgabe erledigt" : "Aufgabe wieder offen",
    after.done ? `${who} hat „${after.title}“ erledigt` : `„${after.title}“ muss wieder erledigt werden`);
  if (!after.done || !["Täglich", "Wöchentlich", "Monatlich"].includes(after.repeat) || !after.completedAt) return;
  const next = nextDueDate(after.due, after.repeat, after.completedAt.toDate());
  // Deterministic ID ensures retries don't create duplicate recurrences.
  const nextId = `${event.params.taskId}--r-${after.completedAt.toMillis()}`;
  const reference = db.collection("households").doc(event.params.householdId).collection("tasks").doc(nextId);
  try {
    await reference.create({ title: after.title, assignee: after.assignee || "Alle", due: next,
      repeat: after.repeat, done: false, createdBy: after.doneBy || "system", doneBy: "", doneByName: "",
      createdAt: new Date(), updatedAt: new Date() });
  } catch (error) {
    if (error.code !== 6 && error.code !== "already-exists") throw error;
  }
});
