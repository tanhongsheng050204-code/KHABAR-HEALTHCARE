/*
 * Live call list for clinic_command.html. When the Khabar API is reachable and the doctor is
 * signed in, this adds a "Live call list" panel above the sample cases, fills the acuity counts,
 * and lets the doctor mark a patient as called. Otherwise the page stays as a design mock-up.
 */
(function () {
  const api = window.KhabarApi;
  const POLL_MS = 15000;
  const LANGUAGES = { ms: "BM", en: "English", zh: "中文", ta: "தமிழ்" };
  const LEVELS = {
    RED: { label: "Call now", chip: "bg-error text-on-error", bar: "bg-error" },
    WATCH: { label: "Watch", chip: "bg-tertiary-fixed text-on-tertiary-fixed", bar: "bg-tertiary-container" },
    REVIEW: { label: "Needs a person", chip: "bg-surface-container-highest text-on-surface", bar: "bg-outline" },
  };

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function ago(iso) {
    const minutes = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
    if (minutes < 1) return "just now";
    if (minutes < 60) return minutes + " min ago";
    const hours = Math.round(minutes / 60);
    return hours + (hours === 1 ? " hour ago" : " hours ago");
  }

  function mount() {
    const worklist = document.querySelector("#telemetry-feed > div");
    if (!worklist) return null;
    const panel = el("section", "flex flex-col gap-space-sm");
    panel.id = "khabar-live";
    panel.setAttribute("aria-live", "polite");
    worklist.prepend(panel);
    return { panel, worklist };
  }

  function card(children, extra = "") {
    const box = el("div", "p-space-md rounded-xl bg-surface-container-lowest shadow-sm border border-surface-container-high/60 " + extra);
    children.forEach((c) => box.append(c));
    return box;
  }

  function showMessage(panel, title, body, link) {
    panel.replaceChildren(card([
      el("p", "font-label-sm text-label-sm uppercase tracking-wider font-semibold text-on-surface-variant", title),
      el("p", "font-body-md text-body-md text-on-surface mt-1", body),
      ...(link ? [Object.assign(el("a", "inline-block mt-2 font-label-md text-label-md text-primary font-semibold underline", link.text), { href: link.href })] : []),
    ]));
  }

  function labelSamples(worklist) {
    if (worklist.querySelector("#khabar-sample-heading")) return;
    const firstSample = worklist.querySelector(":scope > article");
    if (!firstSample) return;
    const heading = el("h3", "font-label-md text-label-md uppercase tracking-wider font-semibold text-on-surface-variant mt-space-sm", "Sample cases (design mock-up, not live data)");
    heading.id = "khabar-sample-heading";
    firstSample.before(heading);
  }

  function setRibbon(counts) {
    const numbers = Array.from(document.querySelectorAll("#priority-triage span"))
      .filter((s) => /^\s*\d+\s+Cases?\s*$/.test(s.textContent));
    const cases = (n) => n + (n === 1 ? " Case" : " Cases");
    if (numbers[0]) numbers[0].textContent = cases(counts.red);
    if (numbers[1]) numbers[1].textContent = cases(counts.watch + counts.review);
  }

  function renderItem(item, onCalled) {
    const style = LEVELS[item.level] || LEVELS.REVIEW;
    const row = el("article", "relative overflow-hidden p-space-md rounded-xl bg-surface-container-lowest shadow-sm border border-surface-container-high/60");
    row.append(el("div", "absolute left-0 top-0 bottom-0 w-2 " + style.bar));

    const body = el("div", "flex flex-wrap items-start justify-between gap-space-sm pl-2");
    const info = el("div", "flex flex-col gap-1 min-w-0");
    const top = el("div", "flex items-center gap-2 flex-wrap");
    top.append(
      el("h2", "font-headline-sm text-headline-sm text-on-surface font-semibold", item.fullName),
      el("span", "px-2 py-0.5 rounded-full font-label-sm text-label-sm font-semibold uppercase tracking-wide " + style.chip, style.label),
      el("span", "px-2 py-0.5 rounded-full bg-surface-container-high text-on-surface-variant font-label-sm text-label-sm",
        (item.followUpDay ? "Day " + item.followUpDay : "Follow-up") + " · " + (LANGUAGES[item.preferredLanguage] || item.preferredLanguage)),
    );
    const silent = item.reason === "NO_REPLY";
    const reading = item.reason === "READING";
    const quote = el("p", "font-body-md text-body-md text-on-surface",
      silent ? "No reply to the last check-in" : reading ? item.urgentReply : "“" + item.urgentReply + "”");
    const missed = item.reason === "MISSED_DOSE";
    const meta = el("p", "font-label-sm text-label-sm text-on-surface-variant", silent
      ? "Check-in sent " + ago(item.latestAt)
      : reading ? "Home reading " + ago(item.latestAt) + (item.unhandledReplies > 1 ? " · " + item.unhandledReplies + " readings to look at" : "")
      : (missed ? "Missed a dose · " : "") + "Latest reply " + ago(item.latestAt)
        + (item.unhandledReplies > 1 ? " · " + item.unhandledReplies + " replies waiting" : ""));
    info.append(top, quote, meta);

    const button = el("button", "px-4 py-2 rounded-full bg-primary text-on-primary font-label-md text-label-md font-semibold shadow-sm hover:opacity-90 transition-opacity", "Mark as called");
    button.type = "button";
    button.addEventListener("click", async () => {
      if (!window.confirm("Record successful patient contact? This closes open replies, worrying readings, and unanswered check-ins that were present at the last refresh. Review all related concerns first; newer items will stay open.")) return;
      button.disabled = true;
      button.textContent = "Saving…";
      try {
        await onCalled(item);
      } catch (e) {
        button.disabled = false;
        button.textContent = "Try again";
      }
    });
    const open = el("a", "px-4 py-2 rounded-full border border-primary text-primary font-label-md text-label-md font-semibold hover:bg-primary-fixed/40", "Open visit");
    open.href = "visit.html?patient=" + encodeURIComponent(item.patientId);
    const actions = el("div", "flex flex-wrap gap-2");
    actions.append(open, button);
    body.append(info, actions);
    row.append(body);
    return row;
  }

  async function refresh(state) {
    const { panel } = state;
    try {
      const [me, list] = await Promise.all([api.call("/api/me"), api.call("/api/clinic/call-list")]);
      setRibbon(list.counts);
      const header = el("div", "flex flex-wrap items-center justify-between gap-2");
      const left = el("div", "flex flex-col");
      left.append(
        el("span", "inline-flex items-center gap-1.5 font-label-sm text-label-sm uppercase tracking-wider font-semibold text-secondary", "● Live from the Khabar API"),
        el("h2", "font-headline-sm text-headline-sm text-on-surface font-semibold", "Call these patients today"),
        el("p", "font-body-sm text-body-sm text-on-surface-variant",
          me.displayName + " · " + me.clinicName + " · " + list.patientsInFollowUp + " patients in follow-up · updated " + new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })),
      );
      const signOut = el("button", "font-label-sm text-label-sm text-on-surface-variant underline", "Sign out");
      signOut.type = "button";
      signOut.addEventListener("click", () => { api.signOut(); location.href = "login.html"; });
      header.append(left, signOut);

      const items = list.items.map((item) => renderItem(item, async (it) => {
        await api.call("/api/clinic/call-list/" + it.patientId + "/called", {
          method: "POST",
          body: JSON.stringify({ observedThrough: list.snapshotAt }),
        });
        await refresh(state);
      }));
      const empty = el("p", "font-body-md text-body-md text-on-surface-variant", "Nobody needs a call right now.");
      panel.replaceChildren(card([header], "border-secondary/40"), ...(items.length ? items : [card([empty])]));
      labelSamples(state.worklist);
    } catch (e) {
      if (e.status === 401 || e.status === 403) {
        showMessage(panel, "Sign in needed", e.status === 401 ? "Your session has ended." : "This account is not a clinic doctor.", { text: "Go to sign-in", href: "login.html" });
      } else {
        showMessage(panel, "Backend not reachable", e.message + ". The sample cases below are design mock-ups.", null);
      }
    }
  }

  function start() {
    if (!api) return;
    const state = mount();
    if (!state) return;
    if (!api.enabled()) {
      showMessage(state.panel, "Demo mode",
        "The cases below are design mock-ups with fictional data. Run the Khabar backend locally to see the live call list (see docs/FRONTEND.md).", null);
      return;
    }
    if (!api.signedIn()) {
      showMessage(state.panel, "Sign in needed", "Sign in to see this clinic's live call list.", { text: "Go to sign-in", href: "login.html" });
      return;
    }
    refresh(state);
    setInterval(() => { if (!document.hidden && api.signedIn()) refresh(state); }, POLL_MS);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start);
  else start();
})();
