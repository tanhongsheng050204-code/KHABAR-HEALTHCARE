"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Activity,
  ArrowRight,
  CalendarDays,
  Check,
  ChevronRight,
  Droplets,
  HeartPulse,
  MessageCircle,
  Pill,
  Plus,
  ShieldCheck,
  Trash2,
  UserRound,
  UsersRound,
} from "lucide-react";
import { apiRequest, ApiError } from "@/lib/api";
import type {
  AccessLog,
  Appointment,
  Caregiver,
  ChatLine,
  Me,
  Medication,
  Reading,
  Slot,
  Summary,
} from "@/lib/types";
import { EmptyState, SectionHeading, StatusBadge } from "@/components/ui";
import { PacketPhotoReader } from "./packet-photo-reader";
import styles from "@/app/home/home.module.css";
import { greetingName } from "@/lib/names";
import { useWorkspaceNavigation } from "./workspace-navigation";
import { WorkspaceLoading, WorkspaceError } from "./workspace-state";

type Notice = { tone: "error" | "success" | "info"; text: string };

export function PatientHome({
  me,
  notify,
}: {
  me: Me;
  notify: (notice: Notice | null) => void;
}) {
  const patientId = me.patientId!;
  const [summary, setSummary] = useState<Summary | null>(null);
  const [appointment, setAppointment] = useState<Appointment | null>(null);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [readings, setReadings] = useState<Reading[]>([]);
  const [medications, setMedications] = useState<Medication[]>([]);
  const [caregivers, setCaregivers] = useState<Caregiver[]>([]);
  const [accessLog, setAccessLog] = useState<AccessLog[]>([]);
  const { section, navigate: setActive } = useWorkspaceNavigation();
  const active =
    section === "checkin" || section === "readings" || section === "people"
      ? section
      : "plan";
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [loadError, setLoadError] = useState("");
  useEffect(() => {
    if (!loaded || section === "overview") return;
    const panel = document.getElementById("care-panel");
    panel?.scrollIntoView({ block: "start" });
    panel?.focus({ preventScroll: true });
  }, [section, loaded]);
  const [busy, setBusy] = useState<string | null>(null);
  const [intakeDoneAt, setIntakeDoneAt] = useState<string | null>(null);
  const optional = useCallback(async <T,>(path: string) => {
    try {
      return await apiRequest<T>(path);
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) return null;
      throw error;
    }
  }, []);
  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const [
        nextSummary,
        nextAppointment,
        nextIntake,
        nextSlots,
        nextReadings,
        nextMeds,
        nextCaregivers,
        nextLog,
      ] = await Promise.all([
        optional<Summary>(`/api/patients/${patientId}/summary`),
        optional<Appointment>("/api/appointments/mine"),
        optional<{ completedAt: string }>(`/api/patients/${patientId}/intake`),
        apiRequest<Slot[]>("/api/clinic/slots"),
        apiRequest<Reading[]>(`/api/patients/${patientId}/readings`),
        apiRequest<Medication[]>(`/api/patients/${patientId}/medications`),
        apiRequest<Caregiver[]>("/api/patients/me/caregivers"),
        apiRequest<AccessLog[]>(`/api/patients/${patientId}/access-log`),
      ]);
      setSummary(nextSummary);
      setAppointment(nextAppointment);
      setIntakeDoneAt(nextIntake?.completedAt ?? null);
      setSlots(nextSlots);
      setReadings(nextReadings);
      setMedications(nextMeds);
      setCaregivers(nextCaregivers);
      setAccessLog(nextLog);
      setLoaded(true);
    } catch (error) {
      setLoadError(
        error instanceof Error
          ? error.message
          : "Your care information could not be loaded.",
      );
      notify({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Your care information could not be loaded.",
      });
    } finally {
      setLoading(false);
    }
  }, [notify, optional, patientId]);
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);
  const firstName = greetingName(me.displayName);
  const lastReading = readings[0];
  if (!loaded)
    return loading ? (
      <WorkspaceLoading />
    ) : (
      <WorkspaceError message={loadError} retry={() => void load()} />
    );
  return (
    <main className={styles.workspace} id="overview">
      {loadError && (
        <div className={styles.refreshError} role="alert">
          The refresh failed. Showing your last loaded information.{" "}
          <button onClick={() => void load()}>Try again</button>
        </div>
      )}
      <section className={styles.patientHero}>
        <div>
          <p>Your care, in one place</p>
          <h1>Hello, {firstName}.</h1>
          <span>
            {summary
              ? "Here is what matters today."
              : "Let’s get you ready for your next step."}
          </span>
        </div>
        <div className={styles.careStatus}>
          <i />
          <span>
            <strong>
              {summary
                ? "Plan active"
                : intakeDoneAt
                  ? "Answers sent"
                  : "Getting ready"}
            </strong>
            <small>
              {summary
                ? "Your clinic is following your recovery."
                : intakeDoneAt
                  ? "Your doctor will read them before your visit."
                  : "Complete the intake before your visit."}
            </small>
          </span>
        </div>
      </section>
      <section className={styles.patientFocus}>
        {summary ? (
          <div className={styles.summaryFocus}>
            <div className={styles.focusLabel}>
              <span>
                <Check size={15} />
              </span>
              <p>
                Your latest care plan{" "}
                <small>{summary.language.toUpperCase()}</small>
              </p>
            </div>
            <blockquote>{summary.text}</blockquote>
            {summary.needsDoctor && (
              <div className={styles.doctorAttention}>
                <MessageCircle size={17} />
                <span>
                  <strong>Your clinic needs to advise you</strong>
                  {summary.needsDoctor}
                </span>
              </div>
            )}
            <button className="button-quiet" onClick={() => setActive("plan")}>
              Medicines & appointments <ArrowRight size={15} />
            </button>
          </div>
        ) : (
          <div className={styles.summaryFocus}>
            <div className={styles.focusLabel}>
              <span>
                <MessageCircle size={15} />
              </span>
              <p>Before your appointment</p>
            </div>
            <blockquote>
              Tell us what has changed, in your own words.
            </blockquote>
            <p className={styles.focusCopy}>
              A short guided check-in helps your doctor understand the reason
              for your visit before you arrive.
            </p>
            <button
              className="button-primary"
              onClick={() => setActive("checkin")}
            >
              {intakeDoneAt ? "Send a new check-in" : "Start the guided intake"}{" "}
              <ArrowRight size={16} />
            </button>
          </div>
        )}
        <aside className={styles.nextCard}>
          <span>
            <CalendarDays size={19} />
          </span>
          {appointment ? (
            <>
              <small>Your next appointment</small>
              <h2>
                {new Intl.DateTimeFormat("en-MY", {
                  weekday: "long",
                  day: "numeric",
                  month: "long",
                }).format(new Date(appointment.startsAt))}
              </h2>
              <p>
                {appointment.time} ·{" "}
                {appointment.reason || "Clinic appointment"}
              </p>
              <button
                className="button-secondary"
                onClick={() => setActive("plan")}
              >
                Manage appointment
              </button>
            </>
          ) : (
            <>
              <small>No appointment booked</small>
              <h2>Choose a time that works.</h2>
              <p>
                {slots.length
                  ? "Explore the clinic’s available appointment times."
                  : "Check the appointment panel for availability."}
              </p>
              <button
                className="button-secondary"
                onClick={() => setActive("plan")}
              >
                See available times
              </button>
            </>
          )}
        </aside>
      </section>
      <section className={styles.quickRow}>
        <button onClick={() => setActive("checkin")}>
          <span>
            <MessageCircle size={19} />
          </span>
          <div>
            <strong>Check in</strong>
            <small>Tell the clinic how you feel</small>
          </div>
          <ChevronRight size={17} />
        </button>
        <button onClick={() => setActive("readings")}>
          <span>
            <Activity size={19} />
          </span>
          <div>
            <strong>Record a reading</strong>
            <small>
              {lastReading
                ? lastReading.description
                : "Blood pressure or sugar"}
            </small>
          </div>
          <ChevronRight size={17} />
        </button>
        <button onClick={() => setActive("plan")}>
          <span>
            <Pill size={19} />
          </span>
          <div>
            <strong>My medicines</strong>
            <small>{medications.length} currently listed</small>
          </div>
          <ChevronRight size={17} />
        </button>
        <button onClick={() => setActive("people")}>
          <span>
            <UsersRound size={19} />
          </span>
          <div>
            <strong>My people</strong>
            <small>
              {caregivers.length
                ? `${caregivers.length} caregiver${caregivers.length === 1 ? "" : "s"}`
                : "Invite someone you trust"}
            </small>
          </div>
          <ChevronRight size={17} />
        </button>
      </section>
      <div
        className={styles.patientTabs}
        role="group"
        aria-label="Choose a care section"
      >
        <button
          className={active === "plan" ? styles.activeTab : ""}
          aria-pressed={active === "plan"}
          aria-controls="plan-view"
          onClick={() => setActive("plan")}
        >
          My plan
        </button>
        <button
          className={active === "checkin" ? styles.activeTab : ""}
          aria-pressed={active === "checkin"}
          aria-controls="checkin-view"
          onClick={() => setActive("checkin")}
          id="check-in"
        >
          Check in
        </button>
        <button
          className={active === "readings" ? styles.activeTab : ""}
          aria-pressed={active === "readings"}
          aria-controls="readings-view"
          onClick={() => setActive("readings")}
        >
          Readings
        </button>
        <button
          className={active === "people" ? styles.activeTab : ""}
          aria-pressed={active === "people"}
          aria-controls="people-view"
          onClick={() => setActive("people")}
        >
          People & privacy
        </button>
      </div>
      <div
        id="care-panel"
        tabIndex={-1}
        className={styles.carePanel}
        aria-label="Selected care section"
      >
        <div
          id="plan-view"
          hidden={active !== "plan"}
          className={styles.panelTransition}
        >
          <PlanPanel
            patientId={patientId}
            appointment={appointment}
            slots={slots}
            medications={medications}
            busy={busy}
            setBusy={setBusy}
            reload={load}
            notify={notify}
          />
        </div>
        <div
          id="checkin-view"
          hidden={active !== "checkin"}
          className={styles.panelTransition}
        >
          <CheckInPanel notify={notify} onIntakeDone={load} />
        </div>
        <div
          id="readings-view"
          hidden={active !== "readings"}
          className={styles.panelTransition}
        >
          <ReadingsPanel
            patientId={patientId}
            readings={readings}
            busy={busy}
            setBusy={setBusy}
            reload={load}
            notify={notify}
          />
        </div>
        <div
          id="people-view"
          hidden={active !== "people"}
          className={styles.panelTransition}
        >
          <PeoplePanel
            caregivers={caregivers}
            accessLog={accessLog}
            busy={busy}
            setBusy={setBusy}
            reload={load}
            notify={notify}
          />
        </div>
      </div>
    </main>
  );
}

function PlanPanel({
  patientId,
  appointment,
  slots,
  medications,
  busy,
  setBusy,
  reload,
  notify,
}: {
  patientId: string;
  appointment: Appointment | null;
  slots: Slot[];
  medications: Medication[];
  busy: string | null;
  setBusy: (v: string | null) => void;
  reload: () => Promise<void>;
  notify: (n: Notice) => void;
}) {
  const [selected, setSelected] = useState("");
  const [selectedDate, setSelectedDate] = useState("");
  const [reason, setReason] = useState("");
  const [name, setName] = useState("");
  const [source, setSource] = useState("");
  const grouped = useMemo(
    () =>
      slots.reduce<Record<string, Slot[]>>((all, slot) => {
        (all[slot.date] ??= []).push(slot);
        return all;
      }, {}),
    [slots],
  );
  const availableDates = Object.keys(grouped);
  const activeDate = availableDates.includes(selectedDate)
    ? selectedDate
    : availableDates[0];
  const formatSlotDate = (date: string) =>
    new Intl.DateTimeFormat("en-MY", {
      weekday: "short",
      day: "numeric",
      month: "short",
    }).format(new Date(`${date}T12:00:00`));
  async function book() {
    if (!selected) return;
    setBusy("book");
    try {
      await apiRequest("/api/appointments", {
        method: "POST",
        body: JSON.stringify({ startsAt: selected, reason }),
      });
      notify({ tone: "success", text: "Your appointment is booked." });
      setSelected("");
      setReason("");
      await reload();
    } catch (error) {
      notify({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "The appointment could not be booked.",
      });
    } finally {
      setBusy(null);
    }
  }
  async function cancel() {
    if (!appointment) return;
    setBusy("cancel");
    try {
      await apiRequest(`/api/appointments/${appointment.id}`, {
        method: "DELETE",
      });
      notify({
        tone: "success",
        text: "Your appointment was cancelled and the time is available again.",
      });
      await reload();
    } catch (error) {
      notify({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "The appointment could not be cancelled.",
      });
    } finally {
      setBusy(null);
    }
  }
  async function addMedicine(event: React.FormEvent) {
    event.preventDefault();
    setBusy("med");
    try {
      await apiRequest(`/api/patients/${patientId}/medications`, {
        method: "POST",
        body: JSON.stringify({ name, kind: "MEDICINE", source }),
      });
      setName("");
      setSource("");
      await reload();
      notify({
        tone: "success",
        text: "The medicine was added to your shared list.",
      });
    } catch (error) {
      notify({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "The medicine could not be added.",
      });
    } finally {
      setBusy(null);
    }
  }
  async function removeMedicine(id: string) {
    setBusy(id);
    try {
      await apiRequest(`/api/patients/${patientId}/medications/${id}`, {
        method: "DELETE",
      });
      await reload();
      notify({ tone: "success", text: "The medicine was marked as stopped." });
    } catch (error) {
      notify({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "The medicine could not be updated.",
      });
    } finally {
      setBusy(null);
    }
  }
  return (
    <section className={styles.patientPanel} id="plan">
      <div className={styles.panelMain}>
        <SectionHeading
          eyebrow="Your shared list"
          title="Medicines and remedies"
        />
        <p className={styles.panelIntro}>
          Add medicines, supplements, jamu or herbs from every clinic and
          pharmacy. Your doctor’s safety check uses this list.
        </p>
        <div className={styles.medList}>
          {medications.length ? (
            medications.map((m) => (
              <div key={m.id}>
                <span className={styles.medIcon}>
                  <Pill size={17} />
                </span>
                <div>
                  <strong>{m.name}</strong>
                  <small>
                    {m.kind === "HERB" ? "Herb or remedy" : "Medicine"} ·{" "}
                    {m.source || "Source not added"}
                  </small>
                </div>
                <StatusBadge level="ok">Shared</StatusBadge>
                <button
                  onClick={() => void removeMedicine(m.id)}
                  disabled={busy === m.id}
                  aria-label={`Stop ${m.name}`}
                >
                  <Trash2 size={15} />
                </button>
              </div>
            ))
          ) : (
            <EmptyState
              title="No medicines listed yet"
              copy="Add everything you take so the clinic can check the complete picture."
            />
          )}
        </div>
        <PacketPhotoReader patientId={patientId} onAdded={reload} />
        <form className={styles.inlineForm} onSubmit={addMedicine}>
          <label>
            Medicine or remedy
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Metformin 500 mg"
              required
            />
          </label>
          <label>
            Where is it from?
            <input
              value={source}
              onChange={(e) => setSource(e.target.value)}
              placeholder="Clinic or pharmacy"
            />
          </label>
          <button className="button-primary" disabled={busy === "med"}>
            <Plus size={16} />
            Add
          </button>
        </form>
      </div>
      <aside className={styles.panelSide}>
        <SectionHeading
          eyebrow="Appointment"
          title={appointment ? "You are booked" : "Choose a time"}
        />
        {appointment ? (
          <div className={styles.booked}>
            <CalendarDays size={24} />
            <strong>{appointment.date}</strong>
            <span>{appointment.time}</span>
            <p>{appointment.reason || "Clinic appointment"}</p>
            <button
              className="button-danger"
              disabled={busy === "cancel"}
              onClick={() => void cancel()}
            >
              Cancel appointment
            </button>
          </div>
        ) : (
          <>
            <div className={styles.slotGroups}>
              {!slots.length && (
                <p className={styles.panelIntro}>
                  No appointment times are currently available. Please contact
                  your clinic.
                </p>
              )}
              {activeDate && (
                <>
                  <label className={styles.slotDateLabel}>
                    Available day
                    <select
                      value={activeDate}
                      onChange={(event) => {
                        setSelectedDate(event.target.value);
                        setSelected("");
                      }}
                    >
                      {availableDates.map((date) => (
                        <option value={date} key={date}>
                          {formatSlotDate(date)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <div className={styles.slotTimes}>
                    {grouped[activeDate].map((slot) => (
                      <button
                        key={slot.startsAt}
                        type="button"
                        aria-label={`${slot.time} on ${formatSlotDate(activeDate)}`}
                        aria-pressed={selected === slot.startsAt}
                        className={
                          selected === slot.startsAt ? styles.selectedSlot : ""
                        }
                        onClick={() => setSelected(slot.startsAt)}
                      >
                        {slot.time}
                      </button>
                    ))}
                  </div>
                </>
              )}
            </div>
            <label className={styles.reasonLabel}>
              Reason for visit
              <textarea
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="What would you like help with?"
              />
            </label>
            <button
              className="button-primary"
              disabled={!selected || busy === "book"}
              onClick={() => void book()}
            >
              Book selected time
            </button>
          </>
        )}
      </aside>
    </section>
  );
}

function CheckInPanel({
  notify,
  onIntakeDone,
}: {
  notify: (n: Notice) => void;
  onIntakeDone: () => Promise<void>;
}) {
  const [lines, setLines] = useState<ChatLine[]>([]);
  const [reply, setReply] = useState("");
  const [update, setUpdate] = useState("");
  const [response, setResponse] = useState<string | null>(null);
  const [complete, setComplete] = useState(false);
  const [busy, setBusy] = useState(false);
  async function next(nextLines = lines) {
    setBusy(true);
    try {
      const result = await apiRequest<{
        nextQuestion: string;
        complete: boolean;
      }>("/api/intake/chat", {
        method: "POST",
        body: JSON.stringify({ messages: nextLines }),
      });
      setLines([
        ...nextLines,
        { role: "assistant", content: result.nextQuestion },
      ]);
      setReply("");
      if (result.complete) {
        setComplete(true);
        notify({
          tone: "success",
          text: "Your intake is complete and ready for the clinic.",
        });
        await onIntakeDone();
      }
    } catch (error) {
      notify({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Your answer could not be sent.",
      });
    } finally {
      setBusy(false);
    }
  }
  async function send(event: React.FormEvent) {
    event.preventDefault();
    if (!reply.trim()) return;
    const nextLines = [
      ...lines,
      { role: "user" as const, content: reply.trim() },
    ];
    await next(nextLines);
  }
  async function followUp(event: React.FormEvent) {
    event.preventDefault();
    if (!update.trim()) return;
    setBusy(true);
    try {
      const result = await apiRequest<{
        level: string;
        answer: string | null;
        message: string | null;
      }>("/api/followup/replies", {
        method: "POST",
        body: JSON.stringify({ text: update.trim() }),
      });
      const said =
        result.message || result.answer || "Your clinic has your update.";
      setResponse(said);
      setUpdate("");
      notify({
        tone: result.level === "RED" ? "error" : "success",
        text: said,
      });
    } catch (error) {
      notify({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Your update could not be sent.",
      });
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className={`${styles.patientPanel} ${styles.chatPanel}`}>
      <div className={styles.panelMain}>
        <SectionHeading
          eyebrow="Private guided intake"
          title="Tell us what has changed"
        />
        <p className={styles.panelIntro}>
          Khabar removes your identifying details before AI assistance. Your
          clinic receives the saved report and transcript.
        </p>
        {lines.length ? (
          <div className={styles.chatLog}>
            {lines.map((line, index) => (
              <div
                key={index}
                className={
                  line.role === "user"
                    ? styles.userBubble
                    : styles.assistantBubble
                }
              >
                <small>{line.role === "user" ? "You" : "Khabar"}</small>
                <p>{line.content}</p>
              </div>
            ))}
          </div>
        ) : (
          <div className={styles.intakeStart}>
            <span>
              <MessageCircle size={24} />
            </span>
            <h3>Start with one simple question.</h3>
            <p>
              You can answer in your own words. There is no need to use medical
              language.
            </p>
            <button
              className="button-primary"
              disabled={busy}
              onClick={() => void next()}
            >
              Begin check-in <ArrowRight size={16} />
            </button>
          </div>
        )}
        {complete && (
          <div className={styles.followupResponse} role="status">
            <strong>Your answers are ready for the clinic.</strong>
            <p>
              You can send any further recovery update using the follow-up form.
            </p>
            <button
              className="button-secondary"
              disabled={busy}
              onClick={() => {
                setLines([]);
                setReply("");
                setComplete(false);
              }}
            >
              Start a new intake
            </button>
          </div>
        )}
        {lines.length > 0 && !complete && (
          <form className={styles.chatForm} onSubmit={send}>
            <input
              value={reply}
              onChange={(e) => setReply(e.target.value)}
              placeholder="Type your answer…"
              aria-label="Your answer"
            />
            <button className="button-primary" disabled={busy || !reply.trim()}>
              {busy ? "Sending…" : "Send"}
            </button>
          </form>
        )}
      </div>
      <aside className={styles.panelSide}>
        <ShieldCheck size={26} />
        <h3>Send a recovery update</h3>
        <p className={styles.panelIntro}>
          Already had your visit? Tell the clinic how you feel or ask a
          follow-up question.
        </p>
        <form className={styles.followupForm} onSubmit={followUp}>
          <textarea
            aria-label="Recovery update for your clinic"
            rows={5}
            value={update}
            onChange={(e) => setUpdate(e.target.value)}
            placeholder="How are you feeling today?"
          />
          <button className="button-primary" disabled={busy || !update.trim()}>
            Send securely
          </button>
        </form>
        {response && (
          <div className={styles.followupResponse}>
            <strong>Response to your update</strong>
            <p>{response}</p>
          </div>
        )}
        <ul>
          <li>Your identifying details are removed before triage.</li>
          <li>Warning symptoms are always escalated to people.</li>
        </ul>
      </aside>
    </section>
  );
}

function ReadingsPanel({
  patientId,
  readings,
  busy,
  setBusy,
  reload,
  notify,
}: {
  patientId: string;
  readings: Reading[];
  busy: string | null;
  setBusy: (v: string | null) => void;
  reload: () => Promise<void>;
  notify: (n: Notice) => void;
}) {
  const [kind, setKind] = useState<"pressure" | "glucose">("pressure");
  const [sys, setSys] = useState("");
  const [dia, setDia] = useState("");
  const [glucose, setGlucose] = useState("");
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy("reading");
    const body =
      kind === "glucose"
        ? { glucose: Number(glucose) }
        : { systolic: Number(sys), diastolic: Number(dia) };
    try {
      await apiRequest(`/api/patients/${patientId}/readings`, {
        method: "POST",
        body: JSON.stringify(body),
      });
      setSys("");
      setDia("");
      setGlucose("");
      await reload();
      notify({ tone: "success", text: "Your reading was saved and checked." });
    } catch (error) {
      notify({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Check the reading and try again.",
      });
    } finally {
      setBusy(null);
    }
  }
  return (
    <section className={styles.patientPanel}>
      <div className={styles.panelMain}>
        <SectionHeading eyebrow="Home monitoring" title="Recent readings" />
        <div className={styles.readingList}>
          {readings.length ? (
            readings.map((reading) => (
              <div key={reading.id}>
                <span className={styles.readingIcon}>
                  {reading.kind?.includes("GLUCOSE") ? (
                    <Droplets size={17} />
                  ) : (
                    <HeartPulse size={17} />
                  )}
                </span>
                <div>
                  <strong>{reading.description}</strong>
                  <small>
                    {new Intl.DateTimeFormat("en-MY", {
                      dateStyle: "medium",
                      timeStyle: "short",
                    }).format(new Date(reading.measuredAt))}{" "}
                    · {reading.source}
                  </small>
                </div>
                <StatusBadge level={reading.level}>
                  {reading.level === "OK"
                    ? "In range"
                    : reading.level.toLowerCase()}
                </StatusBadge>
              </div>
            ))
          ) : (
            <EmptyState
              title="No readings yet"
              copy="Record a home blood pressure or blood sugar reading here."
            />
          )}
        </div>
      </div>
      <aside className={styles.panelSide}>
        <SectionHeading eyebrow="Add a reading" title="How are you today?" />
        <div className={styles.segmented}>
          <button
            className={kind === "pressure" ? styles.segmentActive : ""}
            onClick={() => setKind("pressure")}
          >
            Blood pressure
          </button>
          <button
            className={kind === "glucose" ? styles.segmentActive : ""}
            onClick={() => setKind("glucose")}
          >
            Blood sugar
          </button>
        </div>
        <form className={styles.readingForm} onSubmit={submit}>
          {kind === "pressure" ? (
            <div className={styles.twoFields}>
              <label>
                Systolic
                <input
                  type="number"
                  value={sys}
                  onChange={(e) => setSys(e.target.value)}
                  placeholder="120"
                  required
                />
              </label>
              <label>
                Diastolic
                <input
                  type="number"
                  value={dia}
                  onChange={(e) => setDia(e.target.value)}
                  placeholder="80"
                  required
                />
              </label>
            </div>
          ) : (
            <label>
              Glucose (mmol/L)
              <input
                type="number"
                step="0.1"
                value={glucose}
                onChange={(e) => setGlucose(e.target.value)}
                placeholder="5.6"
                required
              />
            </label>
          )}
          <button className="button-primary" disabled={busy === "reading"}>
            Save and check
          </button>
        </form>
      </aside>
    </section>
  );
}

function PeoplePanel({
  caregivers,
  accessLog,
  busy,
  setBusy,
  reload,
  notify,
}: {
  caregivers: Caregiver[];
  accessLog: AccessLog[];
  busy: string | null;
  setBusy: (v: string | null) => void;
  reload: () => Promise<void>;
  notify: (n: Notice) => void;
}) {
  const [scope, setScope] = useState("SUMMARY");
  const [code, setCode] = useState<string | null>(null);
  async function invite() {
    setBusy("invite");
    try {
      const result = await apiRequest<{ inviteCode: string }>(
        "/api/patients/me/caregiver-invites",
        { method: "POST", body: JSON.stringify({ scope }) },
      );
      setCode(result.inviteCode);
      notify({
        tone: "success",
        text: "A one-time caregiver invitation is ready.",
      });
    } catch (error) {
      notify({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "The invitation could not be created.",
      });
    } finally {
      setBusy(null);
    }
  }
  async function revoke(id: string) {
    setBusy(id);
    try {
      await apiRequest(`/api/patients/me/caregivers/${id}`, {
        method: "DELETE",
      });
      await reload();
      notify({
        tone: "success",
        text: "Caregiver access was removed immediately.",
      });
    } catch (error) {
      notify({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Access could not be removed.",
      });
    } finally {
      setBusy(null);
    }
  }
  return (
    <section className={styles.patientPanel} id="privacy">
      <div className={styles.panelMain}>
        <SectionHeading eyebrow="Your consent" title="People you trust" />
        <p className={styles.panelIntro}>
          You decide who can see your care summary. You can remove access at any
          time.
        </p>
        <div className={styles.caregiverList}>
          {caregivers.length ? (
            caregivers.map((person) => (
              <div key={person.linkId}>
                <span className={styles.avatar}>
                  <UserRound size={16} />
                </span>
                <div>
                  <strong>{person.caregiverName}</strong>
                  <small>
                    {person.scope === "SUMMARY_AND_ALERTS"
                      ? "Summary and alerts"
                      : "Summary only"}
                  </small>
                </div>
                <StatusBadge level="ok">Active</StatusBadge>
                <button
                  className="button-danger"
                  disabled={busy === person.linkId}
                  onClick={() => void revoke(person.linkId)}
                >
                  Remove
                </button>
              </div>
            ))
          ) : (
            <EmptyState
              icon={<UsersRound size={20} />}
              title="No caregiver access"
              copy="Invite a trusted family member when you are ready."
            />
          )}
        </div>
        <div className={styles.inviteBox}>
          <div>
            <label>
              What may they see?
              <select value={scope} onChange={(e) => setScope(e.target.value)}>
                <option value="SUMMARY">Care summary only</option>
                <option value="SUMMARY_AND_ALERTS">
                  Summary and important alerts
                </option>
              </select>
            </label>
            <button
              className="button-primary"
              disabled={busy === "invite"}
              onClick={() => void invite()}
            >
              Create invitation
            </button>
          </div>
          {code && (
            <div className={styles.codeBox}>
              <small>One-time code</small>
              <strong>{code}</strong>
              <button
                className="button-secondary"
                onClick={() => void navigator.clipboard.writeText(code)}
              >
                Copy code
              </button>
            </div>
          )}
        </div>
      </div>
      <aside className={styles.panelSide}>
        <SectionHeading eyebrow="Access history" title="Who opened my record" />
        <div className={styles.accessList}>
          {accessLog.slice(0, 8).map((entry, index) => (
            <div key={`${entry.at}-${index}`}>
              <span>
                <ShieldCheck size={15} />
              </span>
              <p>
                <strong>{entry.actor}</strong>
                <small>
                  {entry.action.replaceAll("_", " ").toLowerCase()} ·{" "}
                  {new Intl.DateTimeFormat("en-MY", {
                    dateStyle: "medium",
                  }).format(new Date(entry.at))}
                </small>
              </p>
            </div>
          ))}
          {!accessLog.length && (
            <p className={styles.panelIntro}>
              No access has been recorded yet.
            </p>
          )}
        </div>
      </aside>
    </section>
  );
}
