export type Role = "DOCTOR" | "PATIENT" | "CAREGIVER"
export type TriageLevel = "RED" | "WATCH" | "REVIEW" | "OK"

export type Me = { id: string; role: Role; displayName: string; clinicId?: string; clinicName?: string; patientId?: string; patientIds?: string[] }
export type Patient = { id: string; fullName: string; icMasked: string; preferredLanguage: string; clinicName?: string; followUpDay?: number | null; hasAccount?: boolean }
export type CallItem = { patientId: string; fullName: string; preferredLanguage: string; level: TriageLevel; urgentReply: string | null; latestReply: string | null; followUpDay: number | null; reason: "REPLY" | "READING" | "MISSED_DOSE" | "NO_REPLY" }
export type CallList = { items: CallItem[]; counts: { red: number; watch: number; review: number }; patientsInFollowUp: number }
export type Summary = { encounterId: string; language: string; text: string; needsDoctor: string | null; createdAt: string }
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

export type Finding = { id: string; check: string; severity: "CRITICAL" | "WARNING" | "INFO"; detail: string; overrideReason: string | null }
export type PrescriptionLine = { raw: string; name: string | null; strengthMg: number | null; unitsPerDose: number | null; timesPerDay: number | null; timing: string | null; asNeeded: boolean }
export type Encounter = { id: string; patientId: string; status: "DRAFT" | "FINAL"; diagnosis: string | null; plan: string | null; followUp: string | null; prescription: PrescriptionLine[]; findings: Finding[]; checked: boolean; openCriticalFindings: number; fasting?: boolean }

export type Previsit = {
  patient: Patient & { allergies: string[]; pregnant: boolean }
  intake: Intake | null
  lastVisit: { encounterId: string; finalisedAt: string; doctor: string; diagnosis: string | null; plan: string | null; followUp: string | null; prescription: string[] } | null
  medications: Medication[]
  reconciliation: Array<{ check: string; severity: string; detail: string }> | null
  recentReplies: Array<{ receivedAt: string; level: TriageLevel; text: string; missedDose: boolean }>
}

export type ApprovedAnswer = { id: string; title: string; triggers: string[]; texts: Record<string, string>; approvedBy: string; approvedAt: string }
