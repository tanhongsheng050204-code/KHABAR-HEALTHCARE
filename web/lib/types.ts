export type Role = "DOCTOR" | "NURSE" | "CLINIC_ADMIN" | "PATIENT" | "CAREGIVER"
export type ClinicStaffRole = "DOCTOR" | "NURSE" | "CLINIC_ADMIN"
export type TriageLevel = "RED" | "WATCH" | "REVIEW" | "OK"

export type Me = { id: string; role: Role; clinicRoles?: ClinicStaffRole[]; displayName: string; clinicId?: string; clinicName?: string; patientId?: string; patientIds?: string[]; patientScopes?: Record<string, "SUMMARY" | "SUMMARY_AND_ALERTS">; telegramLinked?: boolean; preferredLanguage?: string | null }
export type ClinicStaff = { grantId: string; userId: string; displayName: string; role: ClinicStaffRole; grantedAt: string; grantedBy: string | null }
export type Patient = { id: string; fullName: string; icMasked: string; preferredLanguage: string; clinicName?: string; followUpDay?: number | null; hasAccount?: boolean }
export type CaseStatus = "NEW" | "ASSIGNED" | "ACKNOWLEDGED" | "IN_PROGRESS" | "ESCALATED" | "UNABLE_TO_CONTACT" | "RESOLVED"
export type ClosureReason = "CONTACTED_NO_FURTHER_ACTION" | "ADVICE_GIVEN_PER_PROTOCOL" | "APPOINTMENT_ARRANGED" | "DOCTOR_REVIEWED" | "REFERRED_TO_EMERGENCY_CARE" | "UNABLE_TO_CONTACT_AFTER_ATTEMPTS" | "DUPLICATE_OR_FALSE_ALARM" | "CONTACT_RECORDED"
export type FollowUpCase = { id: string; status: CaseStatus; level: TriageLevel; reason: string; ownerId: string | null; ownerName: string | null; openedAt: string; acknowledgedAt: string | null; acknowledgeBy: string; overdue: boolean; escalatedAt: string | null; escalatedTo: string | null; autoRoutedAt: string | null; autoRoutedTo: string | null; contactAttempts: number; lastAttemptAt: string | null; closedAt: string | null; closureReason: ClosureReason | null }
export type CaseEvent = { action: string; from: CaseStatus | null; to: CaseStatus; by: string; note: string | null; at: string }
export type Assignee = { userId: string; displayName: string; roles: ClinicStaffRole[] }
export type Coverage = { day: string; onDuty: string | null; backup: string | null; escalationContact: string | null; warning: string | null }
export type CallItem = { patientId: string; fullName: string; preferredLanguage: string; level: TriageLevel; urgentReply: string | null; latestReply: string | null; urgentEnglish?: string | null; latestEnglish?: string | null; followUpDay: number | null; reason: "REPLY" | "READING" | "MISSED_DOSE" | "MISSED_APPOINTMENT" | "NO_REPLY" | "OPEN_CASE"; followUpCase?: FollowUpCase | null; riskScore?: number | null; riskReasons?: string[] }
export type CallList = { items: CallItem[]; counts: { red: number; watch: number; review: number }; patientsInFollowUp: number; snapshotAt: string; coverage?: Coverage }
export type RotaDay = { day: string; primaryUserId: string; primaryName: string | null; backupUserId: string | null; backupName: string | null }
export type ClinicSettings = { hours: string | null; escalationContact: string | null; redAckMinutes: number; watchAckMinutes: number; reviewAckMinutes: number; rota: RotaDay[]; updatedAt: string | null; today: Coverage }
export type ClinicActivity = { by: string; action: string; subject: string; at: string }
export type Integration = { name: string; status: "OK" | "LIMITED" | "OFF" | "DOWN"; detail: string }
export type SummaryLanguage = "en" | "ms" | "zh" | "ta"
export type Summary = { encounterId: string; language: SummaryLanguage; text: string; needsDoctor: string | null; createdAt: string; held?: boolean; heldReason?: string | null }
export type Appointment = { id: string; startsAt: string; date: string; time: string; reason: string | null; status: string; patientId?: string; fullName?: string }
export type Slot = { startsAt: string; date: string; time: string }
export type Reading = { id: string; kind: string; description: string; level: TriageLevel; source: string; measuredAt: string }
export type Medication = { id: string; name: string; kind: "MEDICINE" | "HERB"; source: string | null; addedBy?: Role; addedAt?: string }
export type ChatLine = { role: "assistant" | "user"; content: string }
export type Caregiver = { linkId: string; caregiverName: string; scope: string; consentedAt: string }
export type AccessLog = { at: string; actor: string; action: string }

export type Intake = {
  sessionId: string
  completedAt: string
  report: { reason?: string; answers?: Array<{ topic: string; question: string; answer: string }>; medicines?: Array<{ asWritten: string; generic: string }>; herbs?: string[]; askAbout?: string[]; allergies?: string[]; redFlags?: Array<{ level: string; matched: string }> } | null
  transcript: Array<Record<string, string>>
}

export type Finding = { id: string; check: string; severity: "CRITICAL" | "WARN" | "WARNING" | "INFO"; detail: string; overrideReason: string | null }
export type PrescriptionLine = { raw: string; name: string | null; strengthMg: number | null; unitsPerDose: number | null; timesPerDay: number | null; timing: string | null; asNeeded: boolean }
export type Encounter = { id: string; patientId: string; status: "DRAFT" | "FINAL"; notes?: string | null; diagnosis: string | null; plan: string | null; followUp: string | null; prescription: PrescriptionLine[]; findings: Finding[]; checked: boolean; openCriticalFindings: number; fasting?: boolean }

export type Previsit = {
  patient: Patient & { allergies: string[]; pregnant: boolean }
  intake: Intake | null
  lastVisit: { encounterId: string; finalisedAt: string; doctor: string; diagnosis: string | null; plan: string | null; followUp: string | null; prescription: string[] } | null
  medications: Medication[]
  reconciliation: Array<{ check: string; severity: string; detail: string }> | null
  recentReplies: Array<{ receivedAt: string; level: TriageLevel; text: string; missedDose: boolean; english?: string | null }>
}

export type ApprovedAnswer = { id: string; title: string; triggers: string[]; texts: Record<string, string>; approvedBy: string; approvedAt: string }

// ---- Features added in Oct 2026 (staff chat, AI receipts, plan check, audit chain, impact, notes, privacy) ----
export type AiReceipt = { id: number; purpose: string; purposeLabel: string; endpoint: string; sent: string; truncated: boolean; media: boolean; namesRemoved: number; icsRemoved: number; phonesRemoved: number; at: string }
export type StaffAlertLog = { kind: "ALERT" | "BRIEFING" | "TEST" | "HANDOVER"; outcome: "SENT" | "NOT_SENT" | "FAILED"; text: string; detail: string | null; at: string }
export type StaffChannel = { linked: boolean; linkedAt: string | null; alertsOn: boolean; briefingHour: number | null; handoverHour?: number | null; telegramConfigured: boolean; pendingCode: boolean; recent: StaffAlertLog[] }
export type StaffLinkCode = { code: string; expiresAt: string; command: string }
export type AuditVerification = { intact: boolean; checked: number; unsealed: number; brokenAt: number | null; problem: string | null; tailSeq: number; tailHash: string; checkedAt: string }
export type SummaryStatus = { encounterId: string; text: string; held: boolean; heldReason: string | null; releasedAt: string | null; releaseNote: string | null }
export type ImpactMetric = { key: string; label: string; value: number; of: number | null; note: string | null }
export type Impact = { days: number; from: string; to: string; metrics: ImpactMetric[]; medianAckMinutes: number | null }
export type CaseNote = { id: string; caseId: string; by: string; text: string; mentions: { userId: string; displayName: string }[]; at: string }
export type Mention = { noteId: string; caseId: string; patientId: string | null; patientName: string | null; by: string; text: string; at: string; unread: boolean }
export type Mentions = { unread: number; items: Mention[] }
export type PrivacyRequest = { id: string; patientId: string; patientName: string | null; kind: "ERASURE" | "CORRECTION"; details: string | null; status: "OPEN" | "DONE" | "DECLINED"; createdAt: string; resolvedAt: string | null; resolution: string | null }
export type PrivacyIncident = { id: string; title: string; whatHappened: string | null; peopleAffected: number | null; discoveredAt: string; containedAt: string | null; assessedAt: string | null; significantHarm: boolean; commissionerNotifiedAt: string | null; peopleNotifiedAt: string | null; closedAt: string | null; log: string | null }
export type Quiet = { quiet: boolean; dayNumber: number | null; since: string | null }

export type DoseEntry = { id: string; day: string; slot: string; medicines: string; response: "TAKEN" | "MISSED" | null; sentAt: string }
export type Adherence = { days: number; sent: number; taken: number; missed: number; unanswered: number; doses: DoseEntry[] }
export type ReminderSettings = { doseReminders: boolean; appointmentReminders: boolean; telegramLinked: boolean; hasPlan: boolean; adherence: Adherence }
export type TeachBackQuestion = { id: string; kind: "TIMES_PER_DAY" | "FOOD" | "NEXT_VISIT"; medicine: string | null; options: string[] }
export type TeachBackAttempt = { id: string; correct: number; total: number; missed: string[]; takenAt: string }
export type TeachBackQuiz = { encounterId: string | null; questions: TeachBackQuestion[]; attempts: TeachBackAttempt[] }
export type TeachBackOutcome = { correct: number; total: number; results: Array<{ id: string; kind: string; medicine: string | null; given: string | null; answer: string; correct: boolean }> }
export type CaregiverTelegram = { telegramConfigured: boolean; linked: boolean; linkedAt: string | null; codePending: boolean; recent: Array<{ patientId: string; kind: string; sentAt: string; delivered: boolean }> }
export type TrendPoint = { at: string; value: number }
export type TrendSeries = { key: string; label: string; unit: string; target: number | null; points: TrendPoint[]; direction: string; recent: number | null; earlier: number | null; outOfTarget: number }
export type TrendSummary = { days: number; series: TrendSeries[]; doses: Array<{ day: string; sent: number; taken: number; missed: number }>; doseDirection: string; replies: Array<{ day: string; replies: number; needingCall: number; worst: string | null }>; labs: LabEntry[]; overall: string; notes: string[] }
export type LabEntry = { id: string; test: string; label: string; unit: string; value: number; takenOn: string; target: number | null; status: "IN_TARGET" | "OUT_OF_TARGET" | "NO_TARGET" }
export type PathwayTargets = { glucoseMax: number | null; systolicMax: number | null; diastolicMax: number | null; hba1cMax: number | null }
export type PathwayEntry = { id: string; pathway: string; label: string; targets: PathwayTargets; reviewEveryWeeks: number; nextReviewOn: string | null; reviewDue: boolean; startedAt: string; tracks: string[] }
export type PathwayDefinition = { key: string; label: string; defaults: PathwayTargets; reviewEveryWeeks: number; tracks: string[] }
export type RiskRow = { patientId: string; fullName: string; preferredLanguage: string; score: number; reasons: Array<{ key: string; label: string; points: number }> }
export type RiskWeight = { key: string; label: string; defaultPoints: number; points: number }
export type AskAnswer = { answer: string; source: "model" | "rules"; patients: Array<{ patientId: string; fullName: string; why: string }>; rowsConsidered: number }
export type CallScript = { language: string; lines: Array<{ english: string; spoken: string }>; safety: { english: string; spoken: string }; source: "model" | "rules" }
export type PreBriefData = { since: string; daysSince: number | null; headline: string; lines: Array<{ kind: string; level: string; text: string }> }
export type DietTip = { id: string; category: string; label: string; ms: string; en: string; zh: string | null; ta: string | null }
export type Preferences = { voiceFirst: boolean; simpleMode: boolean }
export type FamilyGroupStatus = { telegramConfigured: boolean; linked: boolean; linkedAt: string | null }
export type WorkloadBoard = { staff: Array<{ userId: string; name: string; role: string; open: number; urgent: number; overdue: number; closedThisWeek: number; callsThisWeek: number }>; unowned: number; unownedUrgent: number; at: string }
export type ImportResult = { saved: boolean; ok: number; problems: number; rows: Array<{ line: number; fullName: string; icMasked: string | null; phone: string; language: string | null; condition: string | null; status: "OK" | "ERROR" | "DUPLICATE"; problems: string[] }> }
export type OutcomesReportData = { clinicName: string; month: string; from: string; to: string; monthInProgress: boolean; generatedAt: string; sections: Array<{ title: string; lines: Array<{ key: string; label: string; value: number; of: number | null; note: string | null }> }> }
