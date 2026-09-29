// Every reply the film shows must get, from the product's own word lists, the level the page shows for it:
// the page promises "exactly what Khabar sends back". Mirrors classify_reply in services/agents/agents/triage.py.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { DAY_REPLIES, LANGS, REPLY_STATE, REPLY_TEXT } from "../components/film/messages.ts";
import { HOMES } from "../components/film/town.ts";

const words = JSON.parse(await readFile(new URL("../../services/agents/data/triage_words.json", import.meta.url), "utf8"));
const LATIN = /^[a-z' -]+$/;
const escape = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

/** triage.py: whole words for Latin script, substrings for Chinese and Tamil; first of red, watch, ok wins. */
function classify(text) {
  const lowered = text.toLowerCase();
  const contains = (word) =>
    LATIN.test(word) ? new RegExp(`(?<![a-z])${escape(word)}(?![a-z])`).test(lowered) : lowered.includes(word);
  for (const level of ["red", "watch", "ok"]) {
    const listed = Object.values(words[level]).flat().map((w) => w.toLowerCase());
    if (listed.some(contains)) return level;
  }
  return "review";
}

test("the matcher agrees with triage.py on known cases", () => {
  assert.equal(classify("Sakit dada"), "red");
  assert.equal(classify("pening sikit"), "watch");
  assert.equal(classify("நான் நலமாக இருக்கிறேன்."), "review"); // நலமாக does not contain நலம்
});

for (const { day, reply, state } of DAY_REPLIES) {
  for (const { id } of LANGS) {
    test(`day ${day} in ${id}: the reply is triaged as the ${state} the thread shows`, () => {
      assert.equal(classify(reply[id]), state, reply[id]);
    });
  }
}

for (const [reply, state] of Object.entries(REPLY_STATE)) {
  for (const { id } of LANGS) {
    test(`Reply for Aminah "${reply}" in ${id} is triaged as ${state}`, () => {
      assert.equal(classify(REPLY_TEXT[reply][id]), state, REPLY_TEXT[reply][id]);
    });
  }
}

for (const { name, reply, status } of HOMES) {
  test(`${name}'s reply in the town is triaged as the ${status} the town shows`, () => {
    assert.equal(classify(reply), status, reply);
  });
}
