import type { Metadata } from "next"

import "./globals.css"
import { ThemeProvider } from "@/components/theme-provider"
import { siteFontVariables } from "./fonts"

export const metadata: Metadata = {
  title: { default: "Khabar — Care that carries on", template: "%s · Khabar" },
  description: "Clinic-led care before, during, and after every visit.",
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      data-scroll-behavior="smooth"
      className={`antialiased ${siteFontVariables}`}
    >
      <body>
        <ThemeProvider>{children}</ThemeProvider>
      </body>
    </html>
  )
}
