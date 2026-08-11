export function NavGlyph({ name }: { name: string }) {
  const common = {
    width: 16,
    height: 16,
    viewBox: "0 0 16 16",
    fill: "none",
    "aria-hidden": true as const,
  };
  switch (name) {
    case "folder":
      return (
        <svg {...common}>
          <path
            d="M2.5 4.5A1.5 1.5 0 0 1 4 3h2.4c.3 0 .6.12.8.34L8 4.2h4.5A1.5 1.5 0 0 1 14 5.7v6.8A1.5 1.5 0 0 1 12.5 14h-9A1.5 1.5 0 0 1 2 12.5v-8Z"
            stroke="currentColor"
            strokeWidth="1.3"
          />
        </svg>
      );
    case "code":
      return (
        <svg {...common}>
          <path d="M5.5 4.5 2.5 8l3 3.5M10.5 4.5 13.5 8l-3 3.5M9 3.5 7 12.5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    case "search":
      return (
        <svg {...common}>
          <circle cx="7" cy="7" r="3.4" stroke="currentColor" strokeWidth="1.3" />
          <path d="M9.6 9.6 13 13" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
        </svg>
      );
    case "clock":
      return (
        <svg {...common}>
          <circle cx="8" cy="8" r="5.2" stroke="currentColor" strokeWidth="1.3" />
          <path d="M8 5.2V8l2 1.6" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
        </svg>
      );
    case "inspect":
      return (
        <svg {...common}>
          <rect x="3" y="3" width="10" height="10" rx="2" stroke="currentColor" strokeWidth="1.3" />
          <path d="M5.5 6.5h5M5.5 9.2h3.5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
        </svg>
      );
    case "workspace":
      return (
        <svg {...common}>
          <path d="M3 11.5 8 4l5 7.5H3Z" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" />
          <path d="M5.2 11.5h5.6" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
        </svg>
      );
    case "terminal":
      return (
        <svg {...common}>
          <rect x="2.5" y="3.5" width="11" height="9" rx="1.6" stroke="currentColor" strokeWidth="1.3" />
          <path d="M5 7.2 6.6 8.4 5 9.6M8.2 9.6h2.6" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      );
    default:
      return (
        <svg {...common}>
          <circle cx="8" cy="8" r="5.2" stroke="currentColor" strokeWidth="1.3" />
        </svg>
      );
  }
}
