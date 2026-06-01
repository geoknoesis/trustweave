interface Props {
  src: string
  alt: string
  caption?: string
}

/** Registered portrait (CAC personnel or FAA drone photo). */
export function PortraitFrame({ src, alt, caption }: Props) {
  return (
    <div className="drone-photo-frame" style={{ marginBottom: '1rem', textAlign: 'center' }}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={src}
        alt={alt}
        style={{ maxWidth: '100%', height: 'auto', maxHeight: 320, borderRadius: 8, border: '1px solid var(--border)' }}
      />
      {caption && (
        <div className="label" style={{ marginTop: '0.5rem' }}>
          {caption}
        </div>
      )}
    </div>
  )
}
