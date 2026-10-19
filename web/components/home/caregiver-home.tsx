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
import { ListenButton } from "./listen-button";
import { LanguageSwitcher, useUi } from "@/lib/i18n";
import type { Quiet } from "@/lib/types";
import featureStyles from "@/components/features/features.module.css";
import { CaregiverTelegramPanel } from "@/components/features/caregiver-telegram";
import { CaregiverActPanel } from "@/components/features/access";

type Notice = { tone: "error" | "success" | "info"; text: string };

export function CaregiverHome({
  me,
  notify,
}: {
  me: Me;
  notify: (notice: Notice | null) => void;
}) {
  const { t, locale } = useUi();
  const [patient, setPatient] = useState<Patient | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [medicines, setMedicines] = useState<Medication[]>([]);
  const [readings, setReadings] = useState<Reading[]>([]);
  const [quiet, setQuiet] = useState<Quiet | null>(null);
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

    // The no-reply sequence: with alert consent, a caregiver sees when the patient has gone quiet for two days.
    if (canSeeSharedContext) {
      apiRequest<Quiet>(`/api/patients/${id}/quiet`)
        .then((next) => { if (!cancelled) setQuiet(next); })
        .catch(() => { if (!cancelled) setQuiet(null); });
    }
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
          <p>{t("Consented caregiver access")}</p>
          <h1>{t("Hello, {name}.", { name: greetingName(me.displayName) })}</h1>
          <span>{t("You only see what the patient has chosen to share.")}</span>
        </div>
        <LanguageSwitcher className={styles.languageSwitch} />
        {linkedPatientId && scopeConfirmed && (
          <div className={styles.consentSeal}>
            <ShieldCheck size={18} /> {t("Consent active")}
          </div>
        )}
      </section>

      {!linkedPatientId ? (
        <section className={styles.permissionCard}>
          <span>
            <LockKeyhole size={25} />
          </span>
          <div>
            <p>{t("Caregiver connection")}</p>
            <h2>{t("Your account is ready for an invitation.")}</h2>
            <p>
              {t("Ask the patient for their one-time invitation code, then sign out and enter it from the sign-in screen. Access begins only after the patient grants consent.")}
            </p>
          </div>
        </section>
      ) : !scopeConfirmed ? (
        <section className={styles.permissionCard} role="status">
          <span>
            <LockKeyhole size={25} />
          </span>
          <div>
            <p>{t("Caregiver connection")}</p>
            <h2>{t("We could not confirm your sharing permissions.")}</h2>
            <p>{t("Refresh this page before viewing any shared information.")}</p>
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
                <p>{canSeeSharedContext ? t("Supporting") : t("Shared with you")}</p>
                <h2>
                  {canSeeSharedContext
                    ? patient?.fullName || t("Shared patient")
                    : t("Care summary")}
                </h2>
                {canSeeSharedContext && <span>{patient?.clinicName}</span>}
              </div>
            </div>
            <div>
              <ShieldCheck size={18} />
              <span>
                <strong>
                  {canSeeSharedContext ? t("Shared care context") : t("Summary-only access")}
                </strong>
                {canSeeSharedContext
                  ? t("The patient has shared their care summary, medicines and home readings.")
                  : t("The patient has shared the care summary only.")}
              </span>
            </div>
          </section>

          {quiet?.quiet && (
            <div className={featureStyles.banner} role="status">
              <HeartPulse size={20} aria-hidden="true" />
              <div className={featureStyles.panelFlat}>
                <strong>{t("{name} has not answered the day {day} check-in for two days.", { name: patient?.fullName || t("Shared patient"), day: quiet.dayNumber ?? "" })}</strong>
                <span>{t("It may be nothing. A quick call or visit can help, and the clinic has them on its call list too.")}</span>
              </div>
            </div>
          )}
          <div className={styles.caregiverGrid}>
            <section className={styles.peopleCard}>
              <SectionHeading eyebrow={t("Latest plan")} title={t("What matters now")} />
              {summary ? (
                <>
                  <blockquote className={styles.caregiverSummary} lang={summary.language}>
                    {summary.text}
                  </blockquote>
                  <ListenButton text={summary.text} language={summary.language} />
                  {summary.needsDoctor && (
                    <div className={styles.doctorAttention}>
                      <HeartPulse size={17} />
                      <span>
                        <strong>{t("Needs clinic advice")}</strong>
                        {summary.needsDoctor}
                      </span>
                    </div>
                  )}
                </>
              ) : (
                <EmptyState
                  title={t("No care summary yet")}
                  copy={t("The latest finalised plan will appear here when it is ready.")}
                />
              )}
            </section>

            {canSeeSharedContext && (
              <aside className={styles.peopleCard}>
                <SectionHeading eyebrow={t("Shared context")} title={t("Medicines")} />
                <div className={styles.compactList}>
                  {medicines.map((medicine) => (
                    <div key={medicine.id}>
                      <Pill size={16} />
                      <span>
                        <strong>{medicine.name}</strong>
                        <small>{medicine.source || t("Source not stated")}</small>
                      </span>
                    </div>
                  ))}
                  {!medicines.length && <p>{t("No medicines have been shared.")}</p>}
                </div>
              </aside>
            )}
          </div>

          {canSeeSharedContext && patient && (
            <CaregiverActPanel patientId={patient.id} name={greetingName(patient.fullName)} notify={notify} />
          )}

          {canSeeSharedContext && (
            <CaregiverTelegramPanel notify={notify} names={patient ? { [patient.id]: patient.fullName } : {}} />
          )}

          {canSeeSharedContext && (
            <section className={styles.peopleCard}>
              <SectionHeading eyebrow={t("Home monitoring")} title={t("Recent readings")} />
              <div className={styles.readingList}>
                {readings.slice(0, 6).map((reading) => (
                  <div key={reading.id}>
                    <span className={styles.readingIcon}>
                      <HeartPulse size={17} />
                    </span>
                    <div>
                      <strong>{reading.description}</strong>
                      <small>
                        {new Intl.DateTimeFormat(locale, {
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
                    title={t("No readings shared")}
                    copy={t("Home readings appear here only when the patient has allowed alerts.")}
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
