import type { Metadata } from "next"
import { HomeClient } from "@/components/home/home-client"

export const metadata: Metadata = { title: "Home" }
export default function HomePage() { return <HomeClient /> }
