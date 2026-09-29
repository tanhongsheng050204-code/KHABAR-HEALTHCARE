import { defineConfig, devices } from "@playwright/test";

// Runs against a production build, because scroll timing and bundle size differ in dev.
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  // More browsers than this at once starve the machine and tests time out waiting, not failing.
  workers: 3,
  reporter: [["list"]],
  use: { baseURL: "http://localhost:3100", trace: "retain-on-failure" },
  projects: [
    {
      name: "desktop",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 1440, height: 900 },
      },
    },
    {
      name: "phone",
      use: { ...devices["Pixel 7"], viewport: { width: 390, height: 844 } },
    },
  ],
  webServer: {
    command: "npm run build && npx next start -p 3100",
    url: "http://localhost:3100/",
    reuseExistingServer: !process.env.CI,
    timeout: 300_000,
  },
});
