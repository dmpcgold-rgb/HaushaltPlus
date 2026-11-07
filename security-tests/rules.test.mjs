import { readFileSync } from 'node:fs';
import { test, before, after } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc } from 'firebase/firestore';

let env;
let homeA, homeB;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-haushaltplus-security',
    firestore: {
      host: '127.0.0.1',
      port: 8080,
      rules: readFileSync('firestore.rules', 'utf8')
    }
  });
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, 'households/homeA'), {name:'A',ownerUid:'alice'});
    await setDoc(doc(db, 'households/homeB'), {name:'B',ownerUid:'bob'});
    await setDoc(doc(db, 'households/homeA/members/alice'), {name:'Alice'});
    await setDoc(doc(db, 'households/homeB/members/bob'), {name:'Bob'});
    await setDoc(doc(db, 'households/homeA/shoppingItems/a'), {name:'Äpfel',bought:false});
    await setDoc(doc(db, 'households/homeB/shoppingItems/b'), {name:'Brot',bought:false});
  });
  homeA = env.authenticatedContext('alice').firestore();
  homeB = env.authenticatedContext('bob').firestore();
});
after(async () => { await env?.cleanup(); });

test('members read their own family household', async () => {
  await assertSucceeds(getDoc(doc(homeA, 'households/homeA')));
  await assertSucceeds(getDoc(doc(homeB, 'households/homeB')));
});
test('cannot read another family or its shopping list', async () => {
  await assertFails(getDoc(doc(homeA, 'households/homeB')));
  await assertFails(getDoc(doc(homeA, 'households/homeB/shoppingItems/b')));
});
test('cannot write to another family', async () => {
  await assertFails(setDoc(doc(homeB, 'households/homeA/shoppingItems/x'), {name:'bad'}));
  await assertSucceeds(setDoc(doc(homeA, 'households/homeA/shoppingItems/new'), {name:'Milch'}));
});
test('new collections obey membership', async () => {
  await assertFails(getDoc(doc(homeB, 'households/homeA/trash/anything')));
  await assertFails(setDoc(doc(homeA, 'households/homeB/checklists/test'),
    {name:'fake',tasks:['x'],createdBy:'alice'}));
  await assertSucceeds(setDoc(doc(homeA, 'households/homeA/checklists/test'),
    {name:'Urlaub',tasks:['Tasche packen'],createdBy:'alice'}));
});
test('cannot spoof price creator or insert negative price', async () => {
  await assertFails(setDoc(doc(homeA, 'households/homeA/prices/bad'), {
    product:'Milch',shop:'X',unitPrice:-1,unit:'l',date:'2026-11-07',createdBy:'alice'
  }));
  await assertFails(setDoc(doc(homeA, 'households/homeA/prices/forged'), {
    product:'Milch',shop:'X',unitPrice:1.6,unit:'l',date:'2026-11-07',createdBy:'bob'
  }));
});
test('removing membership revokes reads immediately', async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    await deleteDoc(doc(context.firestore(), 'households/homeB/members/bob'));
  });
  await assertFails(getDoc(doc(homeB, 'households/homeB')));
});
