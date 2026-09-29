"use client";

import { LandingMotionScope } from "@/components/landing/landing-motion";
import { ProductPreview } from "@/components/landing/product-preview";

/**
 * The landing page's clinic/patient preview, driven by the film's motion setting. Kept in its own module so
 * the film can load it (and the Motion library it needs) only when the reader nears it.
 */
export function ScopedProductPreview({ enabled }: { enabled: boolean }) {
  return (
    <LandingMotionScope enabled={enabled}>
      <ProductPreview />
    </LandingMotionScope>
  );
}
