export function Arrow({ diagonal = false }: { diagonal?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={diagonal ? 'M6 18 18 6M6 6h12v12' : 'M4 12h16m-6-6 6 6-6 6'} />
    </svg>
  )
}

export function BrandMark() {
  return (
    <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
      <path d="M14 9h9v9M9 14v9h9" stroke="currentColor" strokeWidth="2.5" />
      <rect x="4" y="4" width="11" height="11" rx="3" fill="currentColor" />
      <rect x="17" y="17" width="11" height="11" rx="3" fill="currentColor" />
    </svg>
  )
}
