"use client";

import { useEffect, useState } from "react";
import {
  HeartPulse,
  LockKeyhole,
  Pill,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import { apiRequest } from "@/lib/api";
import type { Me, Medication, Patient, Reading, Summary } from "@/lib/types";
import { EmptyState, SectionHeading, StatusBadge } from "@/components/ui";
import styles from "@/app/home/home.module.css";
import { greetingName } from "@/lib/names";

type Notice = { tone: "error" | "success" | "info"; text: string };

export function CaregiverHome({
  me,
  notify,
}: {
  me: Me;
  notify: (notice: Notice | null) => void;
}) {
  const [patient, setPatient] = useState<Patient | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [medicines, setMedicines] = useState<Medication[]>([]);
  const [readings, setReadings] = useState<Reading[]>([]);
  const linkedPatientId = me.patientId || me.patientIds?.[0];
  const consentScope = linkedPatientId
    ? me.patientScopes?.[linkedPatientId]
    : undefined;
  const scopeConfirmed =
    consentScope === "SUMMARY" || consentScope === "SUMMARY_AND_ALERTS";
  const canSeeSharedContext = consentScope === "SUMMARY_AND_ALERTS";

  useEffect(() => {
    if (!linkedPatientId || !scopeConfirmed) return;

    let cancelled = false;
    const id = linkedPatientId;
    const requests = canSeeSharedContext
      ? Promise.all([
          apiRequest<Patient>(`/api/patients/${id}`),
          apiRequest<Summary>(`/api/patients/${id}/summary`).catch(() => null),
          apiRequest<Medication[]>(`/api/patients/${id}/medications`).catch(
            () => [],
          ),
          apiRequest<Reading[]>(`/api/patients/${id}/readings`).catch(() => []),
        ])
      : Promise.all([
          Promise.resolve(null),
          apiRequest<Summary>(`/api/patients/${id}/summary`).catch(() => null),
          Promise.resolve([] as Medication[]),
          Promise.resolve([] as Reading[]),
        ]);

    requests
      .then(([person, nextSummary, nextMedicines, nextReadings]) => {
        if (cancelled) return;
        setPatient(person);
        setSummary(nextSummary);
        setMedicines(nextMedicines);
        setReadings(nextReadings);
      })
      .catch((error) => {
        if (cancelled) return;
        notify({
          tone: "error",
          text:
            error instanceof Error
              ? error.message
              : "The shared care information could not be loaded.",
        });
      });

    return () => {
      cancelled = true;
    };
  }, [
    canSeeSharedContext,
    linkedPatientId,
    notify,
    scopeConfirmed,
  ]);

  return (
    <main className={styles.workspace} id="overview" tabIndex={-1}>
      <section className={styles.workspaceIntro}>
        <div>
          <p>Consented caregiver access</p>
          <h1>Hello, {greetingName(me.displayName)}.</h1>
          <span>You only see what the patient has chosen to share.</span>
        </div>
        {linkedPatientId && scopeConfirmed && (
          <div className={styles.consentSeal}>
            <ShieldCheck size={18} /> Consent active
          </div>
        )}
      </section>

      {!linkedPatientId ? (
        <section className={styles.permissionCard}>
          <span>
            <LockKeyhole size={25} />
          </span>
          <div>
            <p>Caregiver connection</p>
            <h2>Your account is ready for an invitation.</h2>
            <p>
              Ask the patient for their one-time invitation code, then sign out
              and enter it from the sign-in screen. Access begins only after the
              patient grants consent.
            </p>
          </div>
        </section>
      ) : !scopeConfirmed ? (
        <section className={styles.permissionCard} role="status">
          <span>
            <LockKeyhole size={25} />
          </span>
          <div>
            <p>Caregiver connection</p>
            <h2>We could not confirm your sharing permissions.</h2>
            <p>Refresh this page before viewing any shared information.</p>
          </div>
        </section>
      ) : (
        <>
          <section className={styles.caregiverHero}>
            <div>
              <span className={styles.largeAvatar}>
                <UserRound size={24} />
              </span>
              <div>
                <p>{canSeeSharedContext ? "Supporting" : "Shared with you"}</p>
                <h2>
                  {canSeeSharedContext
                    ? patient?.fullName || "Shared patient"
                    : "Care summary"}
                </h2>
                {canSeeSharedContext && <span>{patient?.clinicName}</span>}
              </div>
            </div>
            <div>
              <ShieldCheck size={18} />
              <span>
                <strong>
                  {canSeeSharedContext ? "Shared care context" : "Summary-only access"}
                </strong>
                {canSeeSharedContext
                  ? "The patient has shared their care summary, medicines and home readings."
                  : "The patient has shared the care summary only."}
              </span>
            </div>
          </section>

          <div className={styles.caregiverGrid}>
            <section className={styles.peopleCard}>
              <SectionHeading eyebrow="Latest plan" title="What matters now" />
              {summary ? (
                <>
                  <blockquote className={styles.caregiverSummary} lang={summary.language}>
                    {summary.text}
                  </blockquote>
                  {summary.needsDoctor && (
                    <div className={styles.doctorAttention}>
                      <HeartPulse size={17} />
                      <span>
                        <strong>Needs clinic advice</strong>
                        {summary.needsDoctor}
                      </span>
                    </div>
                  )}
                </>
              ) : (
                <EmptyState
                  title="No care summary yet"
                  copy="The latest finalised plan will appear here when it is ready."
                />
              )}
            </section>

            {canSeeSharedContext && (
              <aside className={styles.peopleCard}>
                <SectionHeading eyebrow="Shared context" title="Medicines" />
                <div className={styles.compactList}>
                  {medicines.map((medicine) => (
                    <div key={medicine.id}>
                      <Pill size={16} />
                      <span>
                        <strong>{medicine.name}</strong>
                        <small>{medicine.source || "Source not stated"}</small>
                      </span>
                    </div>
                  ))}
                  {!medicines.length && <p>No medicines have been shared.</p>}
                </div>
              </aside>
            )}
          </div>

          {canSeeSharedContext && (
            <section className={styles.peopleCard}>
              <SectionHeading eyebrow="Home monitoring" title="Recent readings" />
              <div className={styles.readingList}>
                {readings.slice(0, 6).map((reading) => (
                  <div key={reading.id}>
                    <span className={styles.readingIcon}>
                      <HeartPulse size={17} />
                    </span>
                    <div>
                      <strong>{reading.description}</strong>
                      <small>
                        {new Intl.DateTimeFormat("en-MY", {
                          dateStyle: "medium",
                          timeStyle: "short",
                        }).format(new Date(reading.measuredAt))}
                      </small>
                    </div>
                    <StatusBadge level={reading.level}>
                      {reading.level.toLowerCase()}
                    </StatusBadge>
                  </div>
                ))}
                {!readings.length && (
                  <EmptyState
                    title="No readings shared"
                    copy="Home readings appear here only when the patient has allowed alerts."
                  />
                )}
              </div>
            </section>
          )}
        </>
      )}
    </main>
  );
}
