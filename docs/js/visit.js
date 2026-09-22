/*
 * The doctor's visit screen (visit.html): pre-visit page, notes to draft, safety check, overrides with a
 * written reason, and finalise. The finalise button stays disabled while any CRITICAL finding is open,
 * which is the third place that block lives (the API refuses with 409 and the database with a CHECK).
 * Every value from the API is written with textContent, never as HTML.
 */
(function () {
  const api = window.KhabarApi;
  const $ = (id) => document.getElementById(id);
  const LANGUAGES = { ms: "BM", en: "English", zh: "中文", ta: "தமிழ்" };
  const TIMING = { before_food: "Before", after_food: "After", with_food: "With" };
  const MIN_REASON = 10;

  let patientId = null;
  let encounter = null;
  let busy = false;

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function say(message, isError) {
    const status = $("status");
    status.textContent = message || "";
    status.className = "mx-auto max-w-7xl px-4 sm:px-6 pt-3 text-sm min-h-[1.75rem] " + (isError ? "text-error font-semibold" : "text-on-surface-variant");
  }

  function date(iso) {
    return iso ? new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : "";
  }

  async function run(label, work) {
    if (busy) return;
    busy = true;
    say(label + "…");
    render();
    try {
      await work();
      say("");
    } catch (e) {
      say(e.message || String(e), true);
    } finally {
      busy = false;
      render();
    }
  }

  // ---- before the visit ------------------------------------------------------------------

  function renderPrevisit(p) {
    $("pv-name").textContent = p.patient.fullName;
    $("pv-meta").textContent = "Speaks " + (LANGUAGES[p.patient.preferredLanguage] || p.patient.preferredLanguage)
      + (p.patient.pregnant ? ". Pregnant." : ".");

    const allergies = $("pv-allergies");
    allergies.replaceChildren();
    const told = (p.intake && p.intake.report && p.intake.report.allergies) || [];
    const all = [...new Set([...(p.patient.allergies || []), ...told])];
    if (all.length === 0) {
      allergies.append(el("span", "text-sm text-on-surface-variant", "No known allergies"));
    }
    all.forEach((a) => allergies.append(el("span", "rounded-full bg-error-container text-on-error-container px-3 py-1 text-sm font-semibold", "Allergic to " + a)));

    const intake = $("pv-intake");
    intake.replaceChildren();
    const report = p.intake && p.intake.report;
    if (!p.intake) {
      intake.append(el("p", "text-on-surface-variant", "No intake chat before this visit."));
    } else if (!report) {
      intake.append(el("p", "text-on-surface-variant", "The intake was completed, but its report could not be built. Ask the patient directly."));
    } else {
      intake.append(el("p", "", "“" + (report.reason || "No reason given") + "”"));
      (report.redFlags || []).forEach((f) => intake.append(el("p", "mt-1 text-sm font-semibold " + (f.level === "red" ? "text-error" : "text-tertiary"),
        (f.level === "red" ? "Emergency sign mentioned: " : "Worth asking about: ") + f.matched)));
      if ((report.askAbout || []).length) {
        intake.append(el("p", "mt-1 text-sm text-tertiary", "Takes " + report.askAbout.join(", ") + " but did not say what is in it."));
      }
      intake.append(el("p", "mt-1 text-xs text-on-surface-variant", "Told Khabar on " + date(p.intake.completedAt)));
    }

    const last = $("pv-last");
    last.replaceChildren();
    if (!p.lastVisit) {
      last.append(el("p", "text-on-surface-variant", "First visit with Khabar."));
    } else {
      last.append(el("p", "", (p.lastVisit.diagnosis || "No diagnosis written") + " (" + date(p.lastVisit.finalisedAt) + ", " + p.lastVisit.doctor + ")"));
      (p.lastVisit.prescription || []).forEach((line) => last.append(el("p", "font-mono text-sm text-on-surface-variant", line)));
    }

    const meds = $("pv-meds");
    meds.replaceChildren();
    if ((p.medications || []).length === 0) {
      meds.append(el("li", "text-on-surface-variant", "Nothing listed. Ask about other clinics, pharmacies, jamu and supplements."));
    }
    (p.medications || []).forEach((m) => {
      const li = el("li", "flex flex-wrap gap-x-2");
      li.append(el("span", "font-semibold", m.name));
      li.append(el("span", "text-on-surface-variant", (m.kind === "HERB" ? "herb or remedy, " : "") + "from " + (m.source || "not said")));
      meds.append(li);
    });

    const rec = $("pv-reconcile");
    rec.replaceChildren();
    if (p.reconciliation === null || p.reconciliation === undefined) {
      rec.append(el("p", "text-sm text-tertiary", "The list could not be checked right now. The safety check during the visit still runs."));
    } else if (p.reconciliation.length === 0) {
      if ((p.medications || []).length) rec.append(el("p", "text-sm text-on-surface-variant", "No clashes or duplicates within this list."));
    } else {
      p.reconciliation.forEach((f) => rec.append(findingNote(f)));
    }

    const replies = $("pv-replies");
    replies.replaceChildren();
    if ((p.recentReplies || []).length === 0) {
      replies.append(el("li", "text-on-surface-variant", "No follow-up replies."));
    }
    (p.recentReplies || []).forEach((r) => {
      const li = el("li", "");
      li.append(el("p", "", "“" + r.text + "”"));
      li.append(el("p", "text-xs text-on-surface-variant", date(r.receivedAt) + " · " + r.level.toLowerCase() + (r.missedDose ? " · missed a dose" : "")));
      replies.append(li);
    });
  }

  function findingNote(f) {
    const critical = f.severity === "CRITICAL";
    return el("p", "rounded-lg px-3 py-2 text-sm " + (critical ? "bg-error-container text-on-error-container" : "bg-tertiary-fixed text-tertiary"), f.detail);
  }

  // ---- this visit ------------------------------------------------------------------------

  function render() {
    const final = encounter && encounter.status === "FINAL";
    $("draft-btn").disabled = busy || final || !patientId;
    $("notes").disabled = final;
    $("fasting").disabled = final;
    $("speak-btn").disabled = (busy && !recorder) || final || !patientId;
    $("check-btn").disabled = busy || final || !encounter || draftEmpty();

    const draft = $("draft");
    draft.hidden = !encounter || draftEmpty();
    if (encounter) {
      $("d-diagnosis").textContent = encounter.diagnosis || "Not written";
      $("d-plan").textContent = encounter.plan || "Not written";
      $("d-followup").textContent = encounter.followUp || "Not written";
      const rows = $("d-rx");
      rows.replaceChildren();
      (encounter.prescription || []).forEach((l) => {
        const tr = el("tr", "border-b border-outline-variant/40");
        tr.append(el("td", "py-1.5 pr-3 font-semibold", l.name || l.raw));
        tr.append(el("td", "py-1.5 pr-3", l.strengthMg != null ? l.strengthMg + " mg" : ""));
        tr.append(el("td", "py-1.5 pr-3", l.unitsPerDose != null ? String(l.unitsPerDose) : ""));
        tr.append(el("td", "py-1.5 pr-3", l.asNeeded ? "When needed" : l.timesPerDay != null ? String(l.timesPerDay) : ""));
        tr.append(el("td", "py-1.5", TIMING[l.timing] || ""));
        rows.append(tr);
      });
      if (!(encounter.prescription || []).length) {
        const tr = el("tr");
        tr.append(el("td", "py-1.5 text-on-surface-variant", "No medicines in the notes."));
        rows.append(tr);
      }
    }

    renderFindings(final);
    renderGate(final);
    $("done").hidden = !final;
  }

  function renderFindings(final) {
    const list = $("findings");
    list.replaceChildren();
    const hint = $("check-hint");
    if (!encounter || draftEmpty()) {
      hint.textContent = "Draft the report first. The check compares it with the allergies, pregnancy, everything the patient takes elsewhere, and the notes themselves.";
      return;
    }
    if (!encounter.checked) {
      hint.textContent = "Not checked yet, or the report changed since the last check. Run the check on this version.";
      return;
    }
    const findings = encounter.findings || [];
    hint.textContent = findings.length === 0 ? "No problems found." : findings.length + (findings.length === 1 ? " thing to look at." : " things to look at.");

    findings.forEach((f) => {
      const critical = f.severity === "CRITICAL";
      const li = el("li", "rounded-xl p-4 " + (critical ? "bg-error-container text-on-error-container" : "bg-tertiary-fixed text-tertiary"));
      const head = el("p", "text-sm font-bold", (critical ? "Critical: " : "Check: ") + f.check.replace("_", " "));
      li.append(head, el("p", "mt-1 text-[15px] leading-6", f.detail));

      if (f.overrideReason) {
        li.append(el("p", "mt-2 text-sm", "Your reason: " + f.overrideReason));
      } else if (critical && !final) {
        const id = "reason-" + f.id;
        const label = el("label", "block mt-3 text-sm font-semibold", "Why is it safe to go ahead? (at least " + MIN_REASON + " characters, saved in the audit log)");
        label.htmlFor = id;
        const box = el("textarea", "mt-1 w-full rounded-lg border border-outline-variant bg-white text-on-surface p-2 text-sm");
        box.id = id;
        box.rows = 2;
        const save = el("button", "mt-2 rounded-full bg-on-error-container text-white px-4 py-1.5 text-sm font-semibold disabled:opacity-40", "Record the reason");
        save.type = "button";
        save.disabled = true;
        box.addEventListener("input", () => { save.disabled = busy || box.value.trim().length < MIN_REASON; });
        save.addEventListener("click", () => run("Recording the reason", async () => {
          encounter = await api.call("/api/encounters/" + encounter.id + "/findings/" + f.id + "/override", {
            method: "POST", body: JSON.stringify({ reason: box.value.trim() }),
          });
        }));
        li.append(label, box, save);
      }
      list.append(li);
    });
  }

  function renderGate(final) {
    const gate = $("gate");
    gate.hidden = !patientId;
    const reason = $("gate-reason");
    const button = $("finalise-btn");
    let blocker = null;
    if (final) blocker = "This report is final.";
    else if (!encounter || draftEmpty()) blocker = "Write the notes and draft the report first.";
    else if (!encounter.checked) blocker = "Run the safety check on this version of the report.";
    else if (encounter.openCriticalFindings > 0) {
      const n = encounter.openCriticalFindings;
      blocker = n + (n === 1 ? " critical finding needs" : " critical findings need") + " a written reason before this can be finalised.";
    }
    reason.textContent = blocker || "Ready. Finalising starts the 30-day follow-up and sends the summary.";
    button.disabled = busy || blocker !== null;
    gate.classList.toggle("gate-open", blocker === null);
    gate.classList.toggle("gate-shut", blocker !== null);
  }

  function draftEmpty() {
    return !encounter.diagnosis && !encounter.plan && !(encounter.prescription || []).length;
  }

  // ---- actions ---------------------------------------------------------------------------

  async function loadPatient(id) {
    patientId = id;
    encounter = null;
    $("notes").value = "";
    $("fasting").checked = false;
    $("summary").textContent = "";
    const url = new URL(location.href);
    url.searchParams.set("patient", id);
    history.replaceState(null, "", url);
    await run("Loading the pre-visit page", async () => {
      renderPrevisit(await api.call("/api/patients/" + id + "/previsit"));
    });
  }

  $("draft-btn").addEventListener("click", () => {
    const notes = $("notes").value.trim();
    if (!notes) { say("Write the notes first.", true); return; }
    run("Drafting the report", async () => {
      await ensureEncounter();
      encounter = await api.call("/api/encounters/" + encounter.id + "/notes", {
        method: "PUT", body: JSON.stringify({ notes, fasting: $("fasting").checked }),
      });
    });
  });

  // ---- speaking instead of typing ---------------------------------------------------------

  let recorder = null;

  async function ensureEncounter() {
    if (!encounter) {
      encounter = await api.call("/api/patients/" + patientId + "/encounters", { method: "POST" });
    }
  }

  async function toggleRecording() {
    const button = $("speak-btn");
    if (recorder) {
      recorder.stop();
      return;
    }
    let stream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch {
      say("The microphone is blocked. Allow it in the browser, or type the notes.", true);
      return;
    }
    const chunks = [];
    recorder = new MediaRecorder(stream);
    recorder.addEventListener("dataavailable", (e) => chunks.push(e.data));
    recorder.addEventListener("stop", () => {
      stream.getTracks().forEach((t) => t.stop());
      recorder = null;
      button.textContent = "Speak the notes";
      const audio = new Blob(chunks, { type: chunks[0] ? chunks[0].type : "audio/webm" });
      run("Turning speech into text", async () => {
        await ensureEncounter();
        const form = new FormData();
        form.append("audio", audio, "notes.webm");
        const { text } = await api.call("/api/encounters/" + encounter.id + "/audio", { method: "POST", body: form });
        const notes = $("notes");
        notes.value = (notes.value.trim() ? notes.value.trim() + "\n" : "") + text;
        say("Check the text, then draft the report.");
      });
    });
    recorder.start();
    button.textContent = "Stop and turn into text";
    say("Listening… speak the notes, then press stop.");
  }

  if (navigator.mediaDevices && window.MediaRecorder) {
    $("speak-btn").hidden = false;
    $("speak-btn").addEventListener("click", toggleRecording);
  }

  $("check-btn").addEventListener("click", () => run("Running the safety check", async () => {
    encounter = await api.call("/api/encounters/" + encounter.id + "/check", { method: "POST" });
  }));

  $("finalise-btn").addEventListener("click", () => run("Finalising", async () => {
    encounter = await api.call("/api/encounters/" + encounter.id + "/finalise", { method: "POST" });
    try {
      const summary = await api.call("/api/patients/" + patientId + "/summary");
      $("summary").textContent = summary.text + (summary.needsDoctor ? "\n\nNeeds your advice: " + summary.needsDoctor : "");
    } catch {
      $("summary").textContent = "The summary could not be built just now. The visit is final and follow-up has started.";
    }
  }));

  $("patient").addEventListener("change", (e) => loadPatient(e.target.value));

  async function start() {
    if (!api || !api.enabled()) {
      $("offline").hidden = false;
      $("app").hidden = true;
      return;
    }
    try {
      let me = api.signedIn() ? await api.call("/api/me").catch(() => null) : null;
      if (!me) me = await api.signInAsDemoDoctor();
      if (!me) throw new Error("Could not sign in. Is the API running?");
      if (me.role !== "DOCTOR") throw new Error("The visit screen is for clinic doctors.");
      $("doctor").textContent = me.displayName;

      const patients = await api.call("/api/clinic/patients");
      const select = $("patient");
      select.replaceChildren();
      patients.forEach((p) => {
        const option = el("option", "", p.fullName + (p.followUpDay != null ? " (day " + p.followUpDay + ")" : ""));
        option.value = p.id;
        select.append(option);
      });
      select.disabled = patients.length === 0;
      if (patients.length === 0) { say("No patients at this clinic yet."); return; }
      const wanted = new URLSearchParams(location.search).get("patient");
      const first = patients.find((p) => p.id === wanted) || patients.find((p) => p.fullName.startsWith("Aminah")) || patients[0];
      select.value = first.id;
      await loadPatient(first.id);
    } catch (e) {
      say(e.message || String(e), true);
    }
  }

  render();
  start();
})();
