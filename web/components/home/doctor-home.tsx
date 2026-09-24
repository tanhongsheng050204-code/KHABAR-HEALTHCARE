"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowRight,
  CalendarDays,
  Check,
  HeartPulse,
  Plus,
  RefreshCw,
  Search,
  UserPlus,
  UsersRound,
} from "lucide-react";
import { apiRequest } from "@/lib/api";
import type { Appointment, CallList, Me, Patient } from "@/lib/types";
import { EmptyState, SectionHeading, StatusBadge } from "@/components/ui";
import { ClinicTools } from "@/components/clinical/clinic-tools";
import styles from "@/app/home/home.module.css";
import { greetingName, initials } from "@/lib/names";
import { WorkspaceLoading, WorkspaceError } from "./workspace-state";
import { useWorkspaceNavigation } from "./workspace-navigation";

// Urgency in words as well as colour, so it reads the same to everyone
const LEVEL_WORDS: Record<string, string> = {
  RED: "urgent",
  WATCH: "watch",
  REVIEW: "review",
  OK: "ok",
};

type Notice = { tone: "error" | "success" | "info"; text: string };

export function DoctorHome({
  me,
  notify,
}: {
  me: Me;
  notify: (notice: Notice | null) => void;
}) {
  const [calls, setCalls] = useState<CallList | null>(null);
  const [patients, setPatients] = useState<Patient[]>([]);
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [dialogNotice, setDialogNotice] = useState<Notice | null>(null);
  const { section } = useWorkspaceNavigation();
  const dialog = useRef<HTMLDialogElement>(null);
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [updatedAt, setUpdatedAt] = useState("");
  const [callLimit, setCallLimit] = useState(6);
  const [patientLimit, setPatientLimit] = useState(12);
  useEffect(() => {
    if (!loaded || (section !== "people" && section !== "schedule")) return;
    const target = document.getElementById(section);
    target?.scrollIntoView({ block: "start" });
    target?.focus({ preventScroll: true });
  }, [loaded, section]);
  useEffect(() => {
    if (!showAdd) return;
    const modal = dialog.current;
    const opener = document.activeElement as HTMLElement | null;
    modal?.showModal();
    return () => {
      modal?.close();
      opener?.focus();
    };
  }, [showAdd]);
  const [newPatient, setNewPatient] = useState({
    fullName: "",
    icNumber: "",
    phone: "",
    preferredLanguage: "en",
  });
  const [invite, setInvite] = useState<string | null>(null);
  const today = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kuala_Lumpur",
  }).format(new Date());
  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const [nextCalls, nextPatients, nextAppointments] = await Promise.all([
        apiRequest<CallList>("/api/clinic/call-list"),
        apiRequest<Patient[]>("/api/clinic/patients"),
        apiRequest<Appointment[]>(`/api/clinic/appointments?date=${today}`),
      ]);
      setCalls(nextCalls);
      setPatients(nextPatients);
      setAppointments(nextAppointments);
      setLoaded(true);
      setUpdatedAt(
        new Intl.DateTimeFormat("en-MY", {
          hour: "numeric",
          minute: "2-digit",
        }).format(new Date()),
      );
    } catch (error) {
      setLoadError(
        error instanceof Error
          ? error.message
          : "Clinic information could not be loaded.",
      );
      notify({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Clinic information could not be loaded.",
      });
    } finally {
      setLoading(false);
    }
  }, [notify, today]);
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);
  const visible = useMemo(
    () =>
      patients.filter(
        (p) =>
          p.fullName.toLowerCase().includes(query.toLowerCase()) ||
          p.icMasked.toLowerCase().includes(query.toLowerCase()),
      ),
    [patients, query],
  );
  async function markCalled(patientId: string, snapshotAt: string) {
    const confirmed = window.confirm(
      "Record successful patient contact? This closes open replies, worrying readings, and unanswered check-ins that were present at the last refresh. Review all related concerns first; newer items will stay open.",
    );
    if (!confirmed) return;
    setBusy(patientId);
    try {
      await apiRequest(`/api/clinic/call-list/${patientId}/called`, {
        method: "POST",
        body: JSON.stringify({ observedThrough: snapshotAt }),
      });
      notify({
        tone: "success",
        text: "Successful contact was recorded. Newer items remain on the call list.",
      });
      await load();
    } catch (error) {
      notify({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "The call could not be recorded.",
      });
    } finally {
      setBusy(null);
    }
  }
  async function addPatient(event: React.FormEvent) {
    event.preventDefault();
    setBusy("patient");
    setDialogNotice(null);
    try {
      const result = await apiRequest<{ inviteCode: string }>(
        "/api/clinic/patients",
        {
          method: "POST",
          body: JSON.stringify({
            ...newPatient,
            allergies: [],
            pregnant: false,
          }),
        },
      );
      setInvite(result.inviteCode);
      setNewPatient({
        fullName: "",
        icNumber: "",
        phone: "",
        preferredLanguage: "en",
      });
      await load();
      setDialogNotice({
        tone: "success",
        text: "The patient record is ready. Share the one-time code securely.",
      });
    } catch (error) {
      setDialogNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "The patient could not be added.",
      });
    } finally {
      setBusy(null);
    }
  }
  const hour = new Date().getHours();
  const greeting =
    hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
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
          The refresh failed. Showing the last loaded information.{" "}
          <button onClick={() => void load()}>Retry</button>
        </div>
      )}
      <section className={styles.workspaceIntro}>
        <div>
          <p>
            {new Intl.DateTimeFormat("en-MY", {
              weekday: "long",
              day: "numeric",
              month: "long",
            }).format(new Date())}
          </p>
          <h1>
            {greeting}, {greetingName(me.displayName)}.
          </h1>
          <span>Start with the people who need a human response.</span>
        </div>
        <button
          className="button-primary"
          onClick={() => {
            setDialogNotice(null);
            setShowAdd(true);
          }}
        >
          <UserPlus size={17} />
          Add patient
        </button>
      </section>
      <section
        className={styles.metricStrip}
        aria-label="Today's clinic summary"
      >
        <div className={styles.urgentMetric}>
          <span>
            <AlertTriangle size={17} />
            Call now
          </span>
          <strong>{calls?.counts.red ?? "—"}</strong>
          <small>Urgent replies and readings</small>
        </div>
        <div>
          <span>
            <HeartPulse size={17} />
            Check today
          </span>
          <strong>{calls?.counts.watch ?? "—"}</strong>
          <small>Needs a closer look</small>
        </div>
        <div>
          <span>
            <CalendarDays size={17} />
            Appointments
          </span>
          <strong>{appointments.length}</strong>
          <small>On today’s schedule</small>
        </div>
        <div>
          <span>
            <UsersRound size={17} />
            In follow-up
          </span>
          <strong>{calls?.patientsInFollowUp ?? "—"}</strong>
          <small>Across the clinic</small>
        </div>
      </section>
      <div className={styles.dashboardGrid}>
        <section className={styles.priorityCard}>
          <SectionHeading
            eyebrow={`Clinic priorities · updated ${updatedAt}`}
            title="Who needs you first"
            action={
              <button
                className="button-quiet"
                disabled={loading}
                onClick={() => void load()}
              >
                <RefreshCw size={15} />
                {loading ? "Refreshing…" : "Refresh"}
              </button>
            }
          />
          {calls?.items.length ? (
            calls.items.slice(0, callLimit).map((item, index) => (
              <article className={styles.priorityRow} key={item.patientId}>
                <span
                  className={`${styles.rank} ${styles[`rank${item.level}`]}`}
                >
                  {String(index + 1).padStart(2, "0")}
                </span>
                <div className={styles.personCell}>
                  <Link href={`/home/patients/${item.patientId}`}>
                    <strong>{item.fullName}</strong>
                  </Link>
                  <span>
                    {item.preferredLanguage.toUpperCase()} ·{" "}
                    {item.followUpDay
                      ? `day ${item.followUpDay}`
                      : "new follow-up"}
                  </span>
                </div>
                <div className={styles.reasonCell}>
                  <StatusBadge level={item.level}>
                    {LEVEL_WORDS[item.level] ?? item.level.toLowerCase()} ·{" "}
                    {item.reason.replaceAll("_", " ").toLowerCase()}
                  </StatusBadge>
                  <p>
                    {item.urgentReply ||
                      item.latestReply ||
                      "No reply for 48 hours."}
                  </p>
                </div>
                <button
                  className="button-secondary"
                  disabled={busy === item.patientId}
                  onClick={() => void markCalled(item.patientId, calls.snapshotAt)}
                >
                  {busy === item.patientId ? "Saving…" : "Record contact"}
                </button>
              </article>
            ))
          ) : (
            <EmptyState
              icon={<Check size={20} />}
              title="The call list is clear"
              copy="No outstanding calls in this update. Refresh to check for new replies and readings."
            />
          )}
          {calls && calls.items.length > callLimit && (
            <button
              className={styles.showMore}
              onClick={() => setCallLimit(callLimit + 6)}
            >
              Show more priorities ({calls.items.length - callLimit} remaining){" "}
              <ArrowRight size={15} />
            </button>
          )}
        </section>
        <aside className={styles.todayCard} id="schedule" tabIndex={-1}>
          <SectionHeading eyebrow="Today" title="Clinic rhythm" />
          <div className={styles.scheduleList}>
            {appointments.length ? (
              appointments.map((item) => (
                <div key={item.id}>
                  <span>{item.time}</span>
                  <i />
                  <div>
                    <strong>{item.fullName || "Booked patient"}</strong>
                    <small>{item.reason || "Consultation"}</small>
                  </div>
                </div>
              ))
            ) : (
              <div className={styles.noVisits}>
                <span>
                  <CalendarDays size={18} />
                </span>
                <div>
                  <strong>No visits today</strong>
                  <p>The day is open. Patient bookings will appear here.</p>
                </div>
              </div>
            )}
          </div>
          <div className={styles.followupSignal}>
            <span>
              <HeartPulse size={18} />
            </span>
            <div>
              <strong>
                {calls?.patientsInFollowUp ?? 0} active follow-ups
              </strong>
              <p>Khabar is watching replies, missed doses and home readings.</p>
            </div>
          </div>
        </aside>
      </div>
      <section className={styles.peopleCard} id="people" tabIndex={-1}>
        <SectionHeading
          eyebrow="Clinic directory"
          title="People in your care"
          action={
            <div className={styles.search}>
              <Search size={16} />
              <input
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setPatientLimit(12);
                }}
                placeholder="Search patients"
                aria-label="Search patients"
              />
            </div>
          }
        />
        <div className={styles.peopleTable}>
          <div className={styles.tableHead}>
            <span>Patient</span>
            <span>Language</span>
            <span>Follow-up</span>
            <span>Account</span>
            <span />
          </div>
          {visible.slice(0, patientLimit).map((patient) => (
            <div className={styles.tableRow} key={patient.id}>
              <div>
                <span className={styles.avatar}>
                  {initials(patient.fullName)}
                </span>
                <p>
                  <strong>{patient.fullName}</strong>
                  <small>{patient.icMasked}</small>
                </p>
              </div>
              <span>{patient.preferredLanguage.toUpperCase()}</span>
              <span>
                {patient.followUpDay
                  ? `Day ${patient.followUpDay}`
                  : "Not started"}
              </span>
              <StatusBadge level={patient.hasAccount ? "ok" : "review"}>
                {patient.hasAccount ? "Linked" : "Invite pending"}
              </StatusBadge>
              <Link
                href={`/home/patients/${patient.id}`}
                aria-label={`Open ${patient.fullName}'s record`}
              >
                Open <ArrowRight size={14} aria-hidden="true" />
              </Link>
            </div>
          ))}
          {!visible.length && (
            <EmptyState
              title={query ? "No matching patients" : "Your directory is ready"}
              copy={
                query
                  ? "Try a different name or identifier."
                  : "Add a patient to begin their care journey."
              }
            />
          )}
        </div>
        {visible.length > patientLimit && (
          <button
            className={styles.showMore}
            onClick={() => setPatientLimit(patientLimit + 12)}
          >
            Show more patients ({visible.length - patientLimit} remaining){" "}
            <ArrowRight size={15} />
          </button>
        )}
      </section>
      {showAdd && (
        <dialog
          ref={dialog}
          className={styles.modalBackdrop}
          aria-labelledby="add-patient-title"
          onCancel={() => {
            setShowAdd(false);
            setInvite(null);
          }}
        >
          <section className={styles.modal}>
            <div className={styles.modalHead}>
              <div>
                <p>Clinic onboarding</p>
                <h2 id="add-patient-title">Add a patient</h2>
              </div>
              <button
                onClick={() => {
                  setShowAdd(false);
                  setInvite(null);
                }}
                aria-label="Close"
              >
                ×
              </button>
            </div>
            {dialogNotice && (
              <p
                className={styles.dialogNotice}
                role={dialogNotice.tone === "error" ? "alert" : "status"}
                data-tone={dialogNotice.tone}
              >
                {dialogNotice.text}
              </p>
            )}
            {invite ? (
              <div className={styles.inviteSuccess}>
                <span>
                  <Check size={22} />
                </span>
                <h3>Patient record created</h3>
                <p>
                  Share this one-time code securely. It expires in seven days.
                </p>
                <strong>{invite}</strong>
                <button
                  className="button-primary"
                  onClick={async () => {
                    try {
                      await navigator.clipboard.writeText(invite);
                      setDialogNotice({
                        tone: "success",
                        text: "Invitation code copied.",
                      });
                    } catch {
                      setDialogNotice({
                        tone: "error",
                        text: "The code could not be copied. Select the code and copy it manually.",
                      });
                    }
                  }}
                >
                  Copy code
                </button>
              </div>
            ) : (
              <form className={styles.modalForm} onSubmit={addPatient}>
                <label>
                  Full name
                  <input
                    value={newPatient.fullName}
                    onChange={(e) =>
                      setNewPatient({ ...newPatient, fullName: e.target.value })
                    }
                    required
                  />
                </label>
                <div className={styles.twoFields}>
                  <label>
                    IC number
                    <input
                      value={newPatient.icNumber}
                      inputMode="numeric"
                      pattern="([0-9]{12}|[0-9]{6}-[0-9]{2}-[0-9]{4})"
                      title="Enter 12 digits, with or without hyphens, or leave this blank."
                      onChange={(e) =>
                        setNewPatient({
                          ...newPatient,
                          icNumber: e.target.value,
                        })
                      }
                    />
                  </label>
                  <label>
                    Phone
                    <input
                      value={newPatient.phone}
                      onChange={(e) =>
                        setNewPatient({ ...newPatient, phone: e.target.value })
                      }
                    />
                  </label>
                </div>
                <label>
                  Preferred language
                  <select
                    value={newPatient.preferredLanguage}
                    onChange={(e) =>
                      setNewPatient({
                        ...newPatient,
                        preferredLanguage: e.target.value,
                      })
                    }
                  >
                    <option value="en">English</option>
                    <option value="ms">Bahasa Melayu</option>
                    <option value="zh">中文</option>
                    <option value="ta">தமிழ்</option>
                  </select>
                </label>
                <div className={styles.modalActions}>
                  <button
                    type="button"
                    className="button-secondary"
                    onClick={() => setShowAdd(false)}
                  >
                    Cancel
                  </button>
                  <button
                    className="button-primary"
                    disabled={busy === "patient"}
                  >
                    {busy === "patient" ? (
                      "Creating…"
                    ) : (
                      <>
                        <Plus size={16} />
                        Create record
                      </>
                    )}
                  </button>
                </div>
              </form>
            )}
          </section>
        </dialog>
      )}
      <ClinicTools notify={(notice) => notify(notice)} />
    </main>
  );
}
