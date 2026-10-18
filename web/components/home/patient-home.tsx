"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
import { LanguageSwitcher, useUi } from "@/lib/i18n";
import { AiReceiptsPanel } from "@/components/features/ai-receipts";
import { MyDataPanel } from "@/components/features/my-data";
import { ListenButton } from "./listen-button";
import { PacketPhotoReader } from "./packet-photo-reader";
import { TelegramCard } from "./telegram-card";
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
  const { t, locale } = useUi();
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
    <main className={styles.workspace} id="overview" tabIndex={-1}>
      {loadError && (
        <div className={styles.refreshError} role="alert">
          {t("The refresh failed. Showing your last loaded information.")}{" "}
          <button onClick={() => void load()}>{t("Try again")}</button>
        </div>
      )}
      <section className={styles.patientHero}>
        <div>
          <p>{t("Your care, in one place")}</p>
          <h1>{t("Hello, {name}.", { name: firstName })}</h1>
          <span>
            {summary
              ? t("Here is what matters today.")
              : t("Let’s get you ready for your next step.")}
          </span>
        </div>
        <LanguageSwitcher className={styles.languageSwitch} />
        <div className={styles.careStatus}>
          <i />
          <span>
            <strong>
              {summary
                ? t("Plan active")
                : intakeDoneAt
                  ? t("Answers sent")
                  : t("Getting ready")}
            </strong>
            <small>
              {summary
                ? t("Your clinic is following your recovery.")
                : intakeDoneAt
                  ? t("Your doctor will read them before your visit.")
                  : t("Complete the intake before your visit.")}
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
                {t("Your latest care plan")}{" "}
                <small>{summary.language.toUpperCase()}</small>
              </p>
            </div>
            <blockquote lang={summary.language}>{summary.text}</blockquote>
            <ListenButton text={summary.text} language={summary.language} />
            {summary.needsDoctor && (
              <div className={styles.doctorAttention}>
                <MessageCircle size={17} />
                <span>
                  <strong>{t("Your clinic needs to advise you")}</strong>
                  {summary.needsDoctor}
                </span>
              </div>
            )}
            <button className="button-quiet" onClick={() => setActive("plan")}>
              {t("Medicines & appointments")} <ArrowRight size={15} />
            </button>
          </div>
        ) : (
          <div className={styles.summaryFocus}>
            <div className={styles.focusLabel}>
              <span>
                <MessageCircle size={15} />
              </span>
              <p>{t("Before your appointment")}</p>
            </div>
            <blockquote>
              {t("Tell us what has changed, in your own words.")}
            </blockquote>
            <p className={styles.focusCopy}>
              {t("A short guided check-in helps your doctor understand the reason for your visit before you arrive.")}
            </p>
            <button
              className="button-primary"
              onClick={() => setActive("checkin")}
            >
              {intakeDoneAt ? t("Send a new check-in") : t("Start the guided intake")}{" "}
              <ArrowRight size={16} />
            </button>
          </div>
        )}
        <div className={styles.nextCard}>
          <span>
            <CalendarDays size={19} />
          </span>
          {appointment ? (
            <>
              <small>{t("Your next appointment")}</small>
              <h2>
                {new Intl.DateTimeFormat(locale, {
                  weekday: "long",
                  day: "numeric",
                  month: "long",
                }).format(new Date(appointment.startsAt))}
              </h2>
              <p>
                {appointment.time} ·{" "}
                {appointment.reason || t("Clinic appointment")}
              </p>
              <button
                className="button-secondary"
                onClick={() => setActive("plan")}
              >
                {t("Manage appointment")}
              </button>
            </>
          ) : (
            <>
              <small>{t("No appointment booked")}</small>
              <h2>{t("Choose a time that works.")}</h2>
              <p>
                {slots.length
                  ? t("Explore the clinic’s available appointment times.")
                  : t("Check the appointment panel for availability.")}
              </p>
              <button
                className="button-secondary"
                onClick={() => setActive("plan")}
              >
                {t("See available times")}
              </button>
            </>
          )}
        </div>
      </section>
      <section className={styles.quickRow}>
        <button onClick={() => setActive("checkin")}>
          <span>
            <MessageCircle size={19} />
          </span>
          <div>
            <strong>{t("Check in")}</strong>
            <small>{t("Tell the clinic how you feel")}</small>
          </div>
          <ChevronRight size={17} />
        </button>
        <button onClick={() => setActive("readings")}>
          <span>
            <Activity size={19} />
          </span>
          <div>
            <strong>{t("Record a reading")}</strong>
            <small>
              {lastReading
                ? lastReading.description
                : t("Blood pressure or sugar")}
            </small>
          </div>
          <ChevronRight size={17} />
        </button>
        <button onClick={() => setActive("plan")}>
          <span>
            <Pill size={19} />
          </span>
          <div>
            <strong>{t("My medicines")}</strong>
            <small>{t("{n} currently listed", { n: medications.length })}</small>
          </div>
          <ChevronRight size={17} />
        </button>
        <button onClick={() => setActive("people")}>
          <span>
            <UsersRound size={19} />
          </span>
          <div>
            <strong>{t("My people")}</strong>
            <small>
              {caregivers.length
                ? caregivers.length === 1 ? t("1 caregiver") : t("{n} caregivers", { n: caregivers.length })
                : t("Invite someone you trust")}
            </small>
          </div>
          <ChevronRight size={17} />
        </button>
      </section>
      <TelegramCard linked={Boolean(me.telegramLinked)} />
      <div
        className={styles.patientTabs}
        role="group"
        aria-label={t("Choose a care section")}
      >
        <button
          className={active === "plan" ? styles.activeTab : ""}
          aria-pressed={active === "plan"}
          aria-controls="plan-view"
          onClick={() => setActive("plan")}
        >
          {t("My plan")}
        </button>
        <button
          className={active === "checkin" ? styles.activeTab : ""}
          aria-pressed={active === "checkin"}
          aria-controls="checkin-view"
          onClick={() => setActive("checkin")}
          id="check-in"
        >
          {t("Check in")}
        </button>
        <button
          className={active === "readings" ? styles.activeTab : ""}
          aria-pressed={active === "readings"}
          aria-controls="readings-view"
          onClick={() => setActive("readings")}
        >
          {t("Readings")}
        </button>
        <button
          className={active === "people" ? styles.activeTab : ""}
          aria-pressed={active === "people"}
          aria-controls="people-view"
          onClick={() => setActive("people")}
        >
          {t("People & privacy")}
        </button>
      </div>
      <div
        id="care-panel"
        tabIndex={-1}
        className={styles.carePanel}
        aria-label={t("Selected care section")}
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
          {/* Mounted when the tab opens, so the receipts include anything sent since the page loaded. */}
          {active === "people" && <AiReceiptsPanel patientId={patientId} copy={{
            eyebrow: t("Privacy"),
            title: t("What the AI saw"),
            lead: t("Every time your words, a photo or a recording left the clinic's system for the AI service. Your registered name, IC and phone number are replaced before anything is sent."),
            empty: t("Nothing sent yet"),
            emptyCopy: t("None of your words have gone to the AI service."),
            removed: (n, ic, phone) => t("{n} name, {ic} IC, {phone} phone removed", { n, ic, phone }),
            media: t("Photo or recording, not kept"),
            cut: t("Shortened here; the full text was sent."),
            purpose: (label) => t(label),
          }} locale={locale} />}
          <MyDataPanel notify={notify} />
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
  const { t, locale } = useUi();
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
    new Intl.DateTimeFormat(locale, {
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
      notify({ tone: "success", text: t("Your appointment is booked.") });
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
        text: t("Your appointment was cancelled and the time is available again."),
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
        text: t("The medicine was added to your shared list."),
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
      notify({ tone: "success", text: t("The medicine was marked as stopped.") });
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
          eyebrow={t("Your shared list")}
          title={t("Medicines and remedies")}
        />
        <p className={styles.panelIntro}>
          {t("Add medicines, supplements, jamu or herbs from every clinic and pharmacy. Your doctor’s safety check uses this list.")}
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
                    {m.kind === "HERB" ? t("Herb or remedy") : t("Medicine")} ·{" "}
                    {m.source || t("Source not added")}
                  </small>
                </div>
                <StatusBadge level="ok">{t("Shared")}</StatusBadge>
                <button
                  onClick={() => void removeMedicine(m.id)}
                  disabled={busy === m.id}
                  aria-label={t("Stop {name}", { name: m.name })}
                >
                  <Trash2 size={15} />
                </button>
              </div>
            ))
          ) : (
            <EmptyState
              title={t("No medicines listed yet")}
              copy={t("Add everything you take so the clinic can check the complete picture.")}
            />
          )}
        </div>
        <PacketPhotoReader patientId={patientId} onAdded={reload} />
        <form className={styles.inlineForm} onSubmit={addMedicine}>
          <label>
            {t("Medicine or remedy")}
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t("e.g. Metformin 500 mg")}
              required
            />
          </label>
          <label>
            {t("Where is it from?")}
            <input
              value={source}
              onChange={(e) => setSource(e.target.value)}
              placeholder={t("Clinic or pharmacy")}
            />
          </label>
          <button className="button-primary" disabled={busy === "med"}>
            <Plus size={16} />
            {t("Add")}
          </button>
        </form>
      </div>
      <div className={styles.panelSide}>
        <SectionHeading
          eyebrow={t("Appointment")}
          title={appointment ? t("You are booked") : t("Choose a time")}
        />
        {appointment ? (
          <div className={styles.booked}>
            <CalendarDays size={24} />
            <strong>{appointment.date}</strong>
            <span>{appointment.time}</span>
            <p>{appointment.reason || t("Clinic appointment")}</p>
            <button
              className="button-danger"
              disabled={busy === "cancel"}
              onClick={() => void cancel()}
            >
              {t("Cancel appointment")}
            </button>
          </div>
        ) : (
          <>
            <div className={styles.slotGroups}>
              {!slots.length && (
                <p className={styles.panelIntro}>
                  {t("No appointment times are currently available. Please contact your clinic.")}
                </p>
              )}
              {activeDate && (
                <>
                  <label className={styles.slotDateLabel}>
                    {t("Available day")}
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
                        aria-label={t("{time} on {day}", { time: slot.time, day: formatSlotDate(activeDate) })}
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
              {t("Reason for visit")}
              <textarea
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder={t("What would you like help with?")}
              />
            </label>
            <button
              className="button-primary"
              disabled={!selected || busy === "book"}
              onClick={() => void book()}
            >
              {t("Book selected time")}
            </button>
          </>
        )}
      </div>
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
  const { t } = useUi();
  const [lines, setLines] = useState<ChatLine[]>([]);
  const [reply, setReply] = useState("");
  const [update, setUpdate] = useState("");
  const pendingUpdate = useRef<{ text: string; id: string } | null>(null);
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
          text: t("Your intake is complete and ready for the clinic."),
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
    const text = update.trim();
    if (!text) return;
    if (pendingUpdate.current?.text !== text) {
      let storedId: string | null = null;
      if (!pendingUpdate.current) {
        try {
          storedId = sessionStorage.getItem("khabar.pending-followup-id");
        } catch {
          // If storage is unavailable, this tab still gets in-memory retry protection.
        }
      }
      pendingUpdate.current = { text, id: storedId || crypto.randomUUID() };
      try {
        sessionStorage.setItem("khabar.pending-followup-id", pendingUpdate.current.id);
      } catch {
        // If storage is unavailable, this tab still gets in-memory retry protection.
      }
    }
    setBusy(true);
    try {
      const sendUpdate = (clientMessageId: string) => apiRequest<{
        level: string;
        answer: string | null;
        message: string | null;
      }>("/api/followup/replies", {
        method: "POST",
        body: JSON.stringify({ text, clientMessageId }),
      });
      let result;
      try {
        result = await sendUpdate(pendingUpdate.current.id);
      } catch (error) {
        // A saved ID can outlive its original text after a reload. The API's conflict
        // confirms it was already used; a fresh ID safely submits this edited update.
        if (!(error instanceof ApiError) || error.status !== 409) throw error;
        pendingUpdate.current = { text, id: crypto.randomUUID() };
        try {
          sessionStorage.setItem("khabar.pending-followup-id", pendingUpdate.current.id);
        } catch {
          // The fresh ID remains available to retry in this tab.
        }
        result = await sendUpdate(pendingUpdate.current.id);
      }
      const said =
        result.message || result.answer || t("Your clinic has your update.");
      setResponse(said);
      setUpdate("");
      try {
        sessionStorage.removeItem("khabar.pending-followup-id");
      } catch {
        // A stale ID is harmless: a later different update receives a conflict and new ID.
      }
      pendingUpdate.current = null;
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
          eyebrow={t("Private guided intake")}
          title={t("Tell us what has changed")}
        />
        <p className={styles.panelIntro}>
          {t("Please don't type names or IC numbers, yours or anyone else's. Khabar removes your registered name, IC and phone number before AI assistance, but it cannot recognise other names. Your clinic receives the saved report and transcript.")}
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
                <small>{line.role === "user" ? t("You") : "Khabar"}</small>
                <p>{line.content}</p>
              </div>
            ))}
          </div>
        ) : (
          <div className={styles.intakeStart}>
            <span>
              <MessageCircle size={24} />
            </span>
            <h3>{t("Start with one simple question.")}</h3>
            <p>
              {t("You can answer in your own words. There is no need to use medical language.")}
            </p>
            <button
              className="button-primary"
              disabled={busy}
              onClick={() => void next()}
            >
              {t("Begin check-in")} <ArrowRight size={16} />
            </button>
          </div>
        )}
        {complete && (
          <div className={styles.followupResponse} role="status">
            <strong>{t("Your answers are ready for the clinic.")}</strong>
            <p>
              {t("You can send any further recovery update using the follow-up form.")}
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
              {t("Start a new intake")}
            </button>
          </div>
        )}
        {lines.length > 0 && !complete && (
          <form className={styles.chatForm} onSubmit={send}>
            <input
              value={reply}
              onChange={(e) => setReply(e.target.value)}
              placeholder={t("Type your answer…")}
              aria-label={t("Your answer")}
            />
            <button className="button-primary" disabled={busy || !reply.trim()}>
              {busy ? t("Sending…") : t("Send")}
            </button>
          </form>
        )}
      </div>
      <div className={styles.panelSide}>
        <ShieldCheck size={26} />
        <h3>{t("Send a recovery update")}</h3>
        <p className={styles.panelIntro}>
          {t("Already had your visit? Tell the clinic how you feel or ask a follow-up question.")}
        </p>
        <form className={styles.followupForm} onSubmit={followUp}>
          <textarea
            aria-label={t("Recovery update for your clinic")}
            rows={5}
            value={update}
            onChange={(e) => setUpdate(e.target.value)}
            placeholder={t("How are you feeling today?")}
          />
          <button className="button-primary" disabled={busy || !update.trim()}>
            {t("Send securely")}
          </button>
        </form>
        {response && (
          <div className={styles.followupResponse}>
            <strong>{t("Response to your update")}</strong>
            <p>{response}</p>
          </div>
        )}
        <ul>
          <li>{t("Please don't type names or IC numbers. Your registered name, IC and phone number are removed before triage; other names are not.")}</li>
          <li>{t("Messages do not alert clinic staff. If this is an emergency, call 999 or go to the nearest emergency department now.")}</li>
        </ul>
      </div>
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
  const { t, locale } = useUi();
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
      notify({ tone: "success", text: t("Your reading was saved and checked.") });
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
        <SectionHeading eyebrow={t("Home monitoring")} title={t("Recent readings")} />
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
                    {new Intl.DateTimeFormat(locale, {
                      dateStyle: "medium",
                      timeStyle: "short",
                    }).format(new Date(reading.measuredAt))}{" "}
                    · {reading.source}
                  </small>
                </div>
                <StatusBadge level={reading.level}>
                  {reading.level === "OK"
                    ? t("In range")
                    : reading.level.toLowerCase()}
                </StatusBadge>
              </div>
            ))
          ) : (
            <EmptyState
              title={t("No readings yet")}
              copy={t("Record a home blood pressure or blood sugar reading here.")}
            />
          )}
        </div>
      </div>
      <div className={styles.panelSide}>
        <SectionHeading eyebrow={t("Add a reading")} title={t("How are you today?")} />
        <div className={styles.segmented}>
          <button
            className={kind === "pressure" ? styles.segmentActive : ""}
            onClick={() => setKind("pressure")}
          >
            {t("Blood pressure")}
          </button>
          <button
            className={kind === "glucose" ? styles.segmentActive : ""}
            onClick={() => setKind("glucose")}
          >
            {t("Blood sugar")}
          </button>
        </div>
        <form className={styles.readingForm} onSubmit={submit}>
          {kind === "pressure" ? (
            <div className={styles.twoFields}>
              <label>
                {t("Systolic")}
                <input
                  type="number"
                  value={sys}
                  onChange={(e) => setSys(e.target.value)}
                  placeholder="120"
                  required
                />
              </label>
              <label>
                {t("Diastolic")}
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
              {t("Glucose (mmol/L)")}
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
            {t("Save and check")}
          </button>
        </form>
      </div>
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
  const { t, locale } = useUi();
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
        text: t("A one-time caregiver invitation is ready."),
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
        text: t("Caregiver access was removed immediately."),
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
        <SectionHeading eyebrow={t("Your consent")} title={t("People you trust")} />
        <p className={styles.panelIntro}>
          {t("You choose whether a caregiver can see only your care summary or also your patient details, medicines, and home readings. You can remove access at any time.")}
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
                      ? t("Summary and alerts")
                      : t("Summary only")}
                  </small>
                </div>
                <StatusBadge level="ok">{t("Active")}</StatusBadge>
                <button
                  className="button-danger"
                  disabled={busy === person.linkId}
                  onClick={() => void revoke(person.linkId)}
                >
                  {t("Remove")}
                </button>
              </div>
            ))
          ) : (
            <EmptyState
              icon={<UsersRound size={20} />}
              title={t("No caregiver access")}
              copy={t("Invite a trusted family member when you are ready.")}
            />
          )}
        </div>
        <div className={styles.inviteBox}>
          <div>
            <label>
              {t("What may they see?")}
              <select value={scope} onChange={(e) => setScope(e.target.value)}>
                <option value="SUMMARY">{t("Care summary only")}</option>
                <option value="SUMMARY_AND_ALERTS">
                  {t("Summary, details, medicines and home readings")}
                </option>
              </select>
            </label>
            <button
              className="button-primary"
              disabled={busy === "invite"}
              onClick={() => void invite()}
            >
              {t("Create invitation")}
            </button>
          </div>
          {code && (
            <div className={styles.codeBox}>
              <small>{t("One-time code")}</small>
              <strong>{code}</strong>
              <button
                className="button-secondary"
                onClick={() => void navigator.clipboard.writeText(code)}
              >
                {t("Copy code")}
              </button>
            </div>
          )}
        </div>
      </div>
      <div className={styles.panelSide}>
        <SectionHeading eyebrow={t("Access history")} title={t("Who opened my record")} />
        <div className={styles.accessList}>
          {accessLog.slice(0, 8).map((entry, index) => (
            <div key={`${entry.at}-${index}`}>
              <span>
                <ShieldCheck size={15} />
              </span>
              <p>
                <strong>{entry.actor}</strong>
                <small>
                  {t(entry.action.replaceAll("_", " ").toLowerCase())} ·{" "}
                  {new Intl.DateTimeFormat(locale, {
                    dateStyle: "medium",
                  }).format(new Date(entry.at))}
                </small>
              </p>
            </div>
          ))}
          {!accessLog.length && (
            <p className={styles.panelIntro}>
              {t("No access has been recorded yet.")}
            </p>
          )}
        </div>
      </div>
    </section>
  );
}
