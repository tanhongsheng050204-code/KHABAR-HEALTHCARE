"use client";

import { useCallback, useEffect, useState } from "react";
import { Copy, RefreshCw, ShieldCheck } from "lucide-react";
import { apiRequest } from "@/lib/api";
import type { Assignee, CallList, ClinicStaff, ClinicStaffRole, Me, Patient } from "@/lib/types";
import { CaseControls, CoverageBanner, QueueFilters, filterItems, type QueueFilter } from "./case-controls";
import { ActivityPanel, ClinicSettingsPanel, IntegrationsPanel } from "./clinic-admin";
import { SectionHeading, StatusBadge } from "@/components/ui";
import { useWorkspaceNavigation } from "./workspace-navigation";
import styles from "@/app/home/home.module.css";

type Notice = { tone: "error" | "success" | "info"; text: string };
const roleNames: Record<ClinicStaffRole, string> = {
  DOCTOR: "Doctor",
  NURSE: "Nurse",
  CLINIC_ADMIN: "Clinic administrator",
};

/** Clinic staff workspaces are intentionally separate from the doctor encounter workspace. */
export function ClinicStaffHome({ me, notify }: { me: Me; notify: (notice: Notice | null) => void }) {
  const { section } = useWorkspaceNavigation();
  const roles = me.clinicRoles ?? [];
  const nurse = roles.includes("NURSE");
  const doctor = roles.includes("DOCTOR");
  const defaultAdmin = roles.includes("CLINIC_ADMIN") && !nurse && !doctor;
  const manager = roles.includes("DOCTOR") || roles.includes("CLINIC_ADMIN");
  const [callList, setCallList] = useState<CallList | null>(null);
  const [assignees, setAssignees] = useState<Assignee[]>([]);
  const [filter, setFilter] = useState<QueueFilter>("ALL");
  const [patients, setPatients] = useState<Patient[]>([]);
  const [staff, setStaff] = useState<ClinicStaff[]>([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [inviteRole, setInviteRole] = useState<ClinicStaffRole>("NURSE");
  const [inviteCode, setInviteCode] = useState("");
  const [copied, setCopied] = useState(false);
  const [loadError, setLoadError] = useState("");

  const loadQueue = useCallback(async () => {
    setLoading(true); setLoadError("");
    try {
      const [nextCalls, nextPatients, nextAssignees] = await Promise.all([
        apiRequest<CallList>("/api/clinic/call-list"),
        apiRequest<Patient[]>("/api/clinic/patients"),
        apiRequest<Assignee[]>("/api/clinic/cases/assignees").catch(() => [] as Assignee[]),
      ]);
      setCallList(nextCalls); setPatients(nextPatients); setAssignees(nextAssignees);
    } catch (error) {
      const message = error instanceof Error ? error.message : "The follow-up workspace could not be loaded.";
      setLoadError(message); notify({ tone: "error", text: message });
    } finally { setLoading(false); }
  }, [notify]);

  const loadStaff = useCallback(async () => {
    setLoading(true); setLoadError("");
    try { setStaff(await apiRequest<ClinicStaff[]>("/api/clinic/staff")); }
    catch (error) {
      const message = error instanceof Error ? error.message : "Clinic staff could not be loaded.";
      setLoadError(message); notify({ tone: "error", text: message });
    } finally { setLoading(false); }
  }, [notify]);

  useEffect(() => {
    void Promise.resolve().then(() => {
      if ((section === "staff" || (defaultAdmin && section === "overview")) && manager) return loadStaff();
      if (section === "people" && nurse) return apiRequest<Patient[]>("/api/clinic/patients").then(setPatients).catch((e: unknown) => notify({ tone: "error", text: e instanceof Error ? e.message : "Patient roster could not be loaded." }));
      if (nurse) return loadQueue();
    });
  }, [section, manager, nurse, defaultAdmin, loadQueue, loadStaff, notify]);



  async function inviteStaff(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy("invite"); setInviteCode("");
    try {
      const result = await apiRequest<{ inviteCode: string }>("/api/clinic/staff-invites", { method: "POST", body: JSON.stringify({ role: inviteRole }) });
      setInviteCode(result.inviteCode);
      notify({ tone: "success", text: "One-time invitation created. Share it securely with the intended staff member." });
    } catch (error) { notify({ tone: "error", text: error instanceof Error ? error.message : "Invitation could not be created." }); }
    finally { setBusy(null); }
  }

  async function copyInviteCode() {
    try {
      await navigator.clipboard.writeText(inviteCode);
      setCopied(true);
    } catch {
      notify({ tone: "error", text: "Clipboard access is unavailable. Select the invitation code and copy it manually." });
    }
  }

  async function revoke(grant: ClinicStaff) {
    if (!window.confirm(`Remove ${roleNames[grant.role]} access for ${grant.displayName}?`)) return;
    setBusy(grant.grantId);
    try { await apiRequest(`/api/clinic/staff/${grant.grantId}`, { method: "DELETE" }); await loadStaff(); notify({ tone: "success", text: "Staff access revoked." }); }
    catch (error) { notify({ tone: "error", text: error instanceof Error ? error.message : "Access could not be revoked." }); }
    finally { setBusy(null); }
  }

  const adminView = manager && (section === "staff" || (defaultAdmin && section === "overview"));
  const rosterView = section === "people" && nurse && me.role !== "DOCTOR";
  const showQueue = !adminView && !rosterView && nurse && me.role !== "DOCTOR";
  if (doctor && !adminView) return null;
  // Below the doctor's home this is one more section of that page; on its own it is the page's main content.
  const Region = doctor ? "section" : "main";
  return (
    <Region className={styles.workspace} id={adminView ? "staff" : rosterView ? "people" : "overview"} tabIndex={-1}
      aria-label={doctor ? "Staff & access" : undefined}>
      <header className={styles.workspaceIntro}>
        <div><p>{me.clinicName || "Clinic workspace"}</p><h1>{adminView ? "Staff & access" : rosterView ? "Patient roster" : "Follow-up, together."}</h1>
          <span>{adminView ? "Manage clinic staff grants. Patient records are not shown here." : rosterView ? "A clinic roster for coordinating follow-up; open clinical records remain doctor-only." : "Work the clinic's follow-up queue and record contact."}</span></div>
        <button className="button-secondary" onClick={() => adminView ? void loadStaff() : void loadQueue()} disabled={loading}><RefreshCw size={16} /> Refresh</button>
      </header>
      {loadError && <p role="alert" className={styles.errorNotice}>{loadError}</p>}
      {showQueue && <>
        <section className={styles.metricStrip} aria-label="Follow-up counts">
          <div><span>Urgent</span><strong>{callList?.counts.red ?? "—"}</strong><small>Needs clinic review</small></div>
          <div><span>Watch</span><strong>{callList?.counts.watch ?? "—"}</strong><small>Follow clinic protocol</small></div>
          <div><span>Review</span><strong>{callList?.counts.review ?? "—"}</strong><small>Follow-up needed</small></div>
          <div><span>In follow-up</span><strong>{callList?.patientsInFollowUp ?? "—"}</strong><small>Patients enrolled</small></div>
        </section>
        <section className={styles.priorityCard} id="care-panel"><SectionHeading eyebrow="Clinic workflow" title="Today's follow-up" action={callList ? `Updated ${new Date(callList.snapshotAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}` : undefined} />
          {loading && <p role="status">Loading the follow-up queue…</p>}
          {!loading && callList?.items.length === 0 && <p>No open follow-up items in this snapshot.</p>}
          <CoverageBanner coverage={callList?.coverage} />
          {callList && callList.items.length > 0 && <QueueFilters value={filter} onChange={setFilter} items={callList.items} me={me} />}
          <div className={styles.staffRows}>{callList && filterItems(callList.items, filter, me).map((item) => <article className={styles.staffRow} key={item.patientId}>
            <div><strong>{item.fullName}</strong><span>{item.reason.replaceAll("_", " ")} · {item.preferredLanguage.toUpperCase()}{item.followUpDay ? ` · Day ${item.followUpDay}` : ""}</span>
              {item.urgentReply && <p>{item.urgentReply}</p>}
              <CaseControls item={item} snapshotAt={callList.snapshotAt} me={me} assignees={assignees} onChanged={loadQueue} notify={notify} /></div>
            <StatusBadge level={item.level}>{item.level.toLowerCase()}</StatusBadge>
          </article>)}</div>
          <p className={styles.staffCaution}><ShieldCheck size={15} /> This queue supports follow-up; it does not diagnose or replace clinic escalation procedures.</p>
        </section>
      </>}
      {rosterView && <section className={styles.peopleCard}><SectionHeading eyebrow="Clinic follow-up" title="Patient roster" />
        <div className={styles.staffRows}>{patients.map((patient) => <article className={styles.staffRow} key={patient.id}><div><strong>{patient.fullName}</strong><span>{patient.preferredLanguage.toUpperCase()} · {patient.icMasked || "No ID on file"}{patient.followUpDay ? ` · Day ${patient.followUpDay}` : ""}</span></div><span>{patient.hasAccount ? "Account linked" : "Not activated"}</span></article>)}</div>
        {patients.length === 0 && <p>{loading ? "Loading roster…" : "No patients found."}</p>}
      </section>}
      {adminView && <>
        <section className={styles.peopleCard}><SectionHeading eyebrow="Clinic access" title="Invite a staff member" />
          <form className={styles.staffInvite} onSubmit={(event) => void inviteStaff(event)}><label>Role to grant<select value={inviteRole} onChange={(event) => setInviteRole(event.target.value as ClinicStaffRole)}><option value="NURSE">Nurse — follow-up queue</option><option value="CLINIC_ADMIN">Clinic administrator — staff access only</option>{roles.includes("DOCTOR") && <option value="DOCTOR">Doctor — clinical access</option>}</select></label><button className="button-primary" disabled={busy === "invite"}>{busy === "invite" ? "Creating…" : "Create invite"}</button></form>
          {inviteCode && <div className={styles.inviteCode}><code>{inviteCode}</code><button className="button-secondary" onClick={() => void copyInviteCode()}><Copy size={15} /> {copied ? "Copied" : "Copy code"}</button></div>}
          <p className={styles.staffCaution}><ShieldCheck size={15} /> Invite codes are one-time credentials. Share them only with the intended colleague through an approved channel.</p>
        </section>
        <section className={styles.peopleCard}><SectionHeading eyebrow="Active grants" title="Clinic staff" action={`${staff.length} access grants`} />
          <div className={styles.staffRows}>{staff.map((grant) => <article className={styles.staffRow} key={grant.grantId}><div><strong>{grant.displayName}</strong><span>{roleNames[grant.role]} · Added {new Date(grant.grantedAt).toLocaleDateString()}</span></div><button className="button-secondary" disabled={busy === grant.grantId || (grant.role === "DOCTOR" && !roles.includes("DOCTOR"))} onClick={() => void revoke(grant)}>Revoke access</button></article>)}</div>
          {staff.length === 0 && <p>{loading ? "Loading staff…" : "No active grants."}</p>}
        </section>
        <ClinicSettingsPanel notify={notify} />
        <IntegrationsPanel />
        <ActivityPanel />
      </>}
      {!nurse && !manager && <section className={styles.peopleCard}><SectionHeading eyebrow="Clinic workspace" title="Access not assigned" /><p>Ask your clinic administrator to assign a clinic role.</p></section>}
    </Region>
  );
}
