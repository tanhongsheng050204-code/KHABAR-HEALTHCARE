import type { Metadata } from "next"
import { OutcomesReportPage } from "@/components/features/outcomes-report"

export const metadata: Metadata = { title: "Outcomes report" }

export default function ReportPage() {
  return <OutcomesReportPage />
}
