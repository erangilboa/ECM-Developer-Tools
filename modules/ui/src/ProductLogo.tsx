import { useId } from "react";
import type { Product } from "./types";

export function ProductLogo({
  product,
  size = 22,
  title,
}: {
  product: Product;
  size?: number;
  title?: string;
}) {
  const label = title ?? (product === "EXTENDED_ECM" ? "OpenText Extended ECM" : "Documentum");
  return (
    <span className="product-logo" title={label} aria-label={label}>
      {product === "EXTENDED_ECM" ? <XecmMark size={size} /> : <DocumentumMark size={size} />}
    </span>
  );
}

export function ProductLockup({ product }: { product: Product }) {
  if (product === "EXTENDED_ECM") {
    return (
      <div className="product-lockup">
        <ProductLogo product={product} size={48} />
        <div>
          <div className="lockup-name xecm">OpenText</div>
          <div className="lockup-sub">Extended ECM · Content Server</div>
        </div>
      </div>
    );
  }
  return (
    <div className="product-lockup">
      <ProductLogo product={product} size={48} />
      <div>
        <div className="lockup-name dctm">documentum</div>
        <div className="lockup-sub">OpenText Content Management</div>
      </div>
    </div>
  );
}

/** Classic Documentum gold ribbon-D (original drawing, not an official file). */
function DocumentumMark({ size }: { size: number }) {
  const uid = useId().replace(/:/g, "");
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" fill="none" aria-hidden>
      <rect width="32" height="32" rx="7" fill="#1a1610" />
      <defs>
        <linearGradient id={`${uid}g`} x1="8" y1="4" x2="28" y2="30" gradientUnits="userSpaceOnUse">
          <stop stopColor="#F6E27A" />
          <stop offset="0.45" stopColor="#E0A824" />
          <stop offset="1" stopColor="#9A6410" />
        </linearGradient>
        <linearGradient id={`${uid}f`} x1="8" y1="6" x2="16" y2="28" gradientUnits="userSpaceOnUse">
          <stop stopColor="#FFF6C8" />
          <stop offset="1" stopColor="#D4A020" />
        </linearGradient>
      </defs>
      <path
        fillRule="evenodd"
        d="M9.5 6.2h9.2c6.9 0 12.3 5.2 12.3 12.3 0 7.1-5.4 12.3-12.3 12.3H9.5V6.2Zm8.4 7.4v10.6h.9c3.1 0 5.4-2.3 5.4-5.3 0-3-2.3-5.3-5.4-5.3h-.9Z"
        fill={`url(#${uid}g)`}
      />
      <path d="M9.5 6.2 16 10.4v15.2L9.5 29.8V6.2Z" fill={`url(#${uid}f)`} opacity="0.95" />
      <path d="M16 10.4 9.5 6.2h2.2L16 10.4Z" fill="#FFF8D6" />
    </svg>
  );
}

/** OpenText-style mark for Extended ECM (original drawing using public brand colors). */
function XecmMark({ size }: { size: number }) {
  const uid = useId().replace(/:/g, "");
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" fill="none" aria-hidden>
      <rect width="32" height="32" rx="7" fill="#071a33" />
      <defs>
        <linearGradient id={`${uid}o`} x1="6" y1="8" x2="28" y2="26" gradientUnits="userSpaceOnUse">
          <stop stopColor="#FF8A3D" />
          <stop offset="1" stopColor="#E04E0A" />
        </linearGradient>
      </defs>
      <circle cx="16" cy="16" r="9.2" stroke={`url(#${uid}o)`} strokeWidth="3.2" fill="none" />
      <path
        d="M10.2 13.2c2.1-3.4 6.6-4.6 10.2-2.6"
        stroke="#FFD0A8"
        strokeWidth="2.2"
        strokeLinecap="round"
        fill="none"
      />
      <circle cx="21.4" cy="12.2" r="2.1" fill="#FF8A3D" />
    </svg>
  );
}
