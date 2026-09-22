import type { Metadata } from "next"
import { PatientRecord } from "@/components/clinical/patient-record"

export const metadata: Metadata = { title: "Patient record" }
export default async function PatientRecordPage({params}:{params:Promise<{id:string}>}) { const {id}=await params; return <PatientRecord patientId={id}/> }
