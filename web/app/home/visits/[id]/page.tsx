import type { Metadata } from "next"
import { VisitWorkspace } from "@/components/clinical/visit-workspace"

export const metadata: Metadata = { title: "Visit" }
export default async function VisitPage({params}:{params:Promise<{id:string}>}) { const {id}=await params; return <VisitWorkspace encounterId={id}/> }
