/**
 * ChessIcon – SVG silhouette icons for the six chess pieces.
 *
 * Each piece is coloured via the corresponding CSS custom property:
 *   var(--wc-chess-king), var(--wc-chess-queen), var(--wc-chess-rook),
 *   var(--wc-chess-bishop), var(--wc-chess-knight), var(--wc-chess-pawn)
 *
 * All paths use a 24×24 viewBox; the rendered size is controlled by
 * the `size` prop (default 20px).
 */
import React from "react";

// ─── Types ─────────────────────────────────────────────────────────────────────

export type ChessPiece = "KING" | "QUEEN" | "ROOK" | "BISHOP" | "KNIGHT" | "PAWN";

export interface ChessIconProps {
  /** Which chess piece to render. */
  piece: ChessPiece;
  /** Width and height in pixels. @default 20 */
  size?: number;
  /** Extra CSS class(es) applied to the root `<svg>` element. */
  className?: string;
}

// ─── Silhouette paths ──────────────────────────────────────────────────────────

/**
 * Returns the JSX path group for the given chess piece.
 * All pieces share a common base path to keep code DRY.
 */
function PiecePaths({ piece }: { piece: ChessPiece }): React.ReactElement {
  /* shared base: wide rounded rectangle at the bottom of every piece */
  const Base = <path d="M5.5 15 L5.5 17 Q5.5 19 8 19 L16 19 Q18.5 19 18.5 17 L18.5 15 Z" />;

  switch (piece) {
    case "KING":
      return (
        <>
          {/* vertical arm of the cross */}
          <rect x="11" y="2" width="2" height="7" rx="0.5" />
          {/* horizontal arm of the cross */}
          <rect x="8" y="4" width="8" height="2" rx="0.5" />
          {/* body – slight taper */}
          <path d="M8.5 9 L8 15 L16 15 L15.5 9 Z" />
          {Base}
        </>
      );

    case "QUEEN":
      return (
        <>
          {/* three crown orbs */}
          <circle cx="12" cy="3.5" r="1.5" />
          <circle cx="7" cy="5.5" r="1.5" />
          <circle cx="17" cy="5.5" r="1.5" />
          {/* crown body connecting the orbs */}
          <path d="M7 7.5 L9.5 15 L14.5 15 L17 7.5 L14 10 L12 6 L10 10 Z" />
          {Base}
        </>
      );

    case "ROOK":
      return (
        <>
          {/* battlements: three merlons */}
          <path d="M7 3.5 L7 7.5 L9 7.5 L9 5 L10.5 5 L10.5 7.5 L13.5 7.5 L13.5 5 L15 5 L15 7.5 L17 7.5 L17 3.5 Z" />
          {/* rectangular body */}
          <rect x="8" y="7.5" width="8" height="7.5" />
          {Base}
        </>
      );

    case "BISHOP":
      return (
        <>
          {/* finial ball */}
          <circle cx="12" cy="3" r="1.5" />
          {/* mitre – isoceles triangle */}
          <path d="M12 4.5 L8.5 14 L15.5 14 Z" />
          {/* collar band */}
          <rect x="8.5" y="14" width="7" height="1.5" rx="0.5" />
          {Base}
        </>
      );

    case "KNIGHT":
      return (
        <>
          {/* horse-head silhouette profile */}
          <path
            d="
            M9 20 L9 15
            C 8 14  7 12  7.5 10
            C 7.5 8  9 6  11 5.5
            C 11 5.5  10.5 4  10 3.5
            C 9.5 3  10.5 2  12 2.5
            C 14 3  15 4.5  15 6
            C 15 7.5  14 8.5  13 9
            L 14.5 10.5 L 13 11.5
            L 15 13.5
            C 15 13.5  14 14.5  13 15
            L 14 20 Z
          "
          />
        </>
      );

    case "PAWN":
      return (
        <>
          {/* round head */}
          <circle cx="12" cy="5.5" r="3" />
          {/* body – slightly tapering downward */}
          <path d="M10.5 8.5 C 9.5 10.5  9 12  8.5 14 L 15.5 14 C 15 12  14.5 10.5  13.5 8.5 Z" />
          {Base}
        </>
      );
  }
}

// ─── Component ─────────────────────────────────────────────────────────────────

/**
 * Renders a clean SVG silhouette for the specified chess piece.
 *
 * @example
 * <ChessIcon piece="QUEEN" size={24} className={styles.pieceIcon} />
 */
export const ChessIcon: React.FC<ChessIconProps> = ({ piece, size = 20, className }) => {
  const fillColor = `var(--wc-chess-${piece.toLowerCase()})`;

  return (
    <svg
      data-testid={`chess-icon-${piece.toLowerCase()}`}
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill={fillColor}
      aria-hidden="true"
      className={className}
    >
      <PiecePaths piece={piece} />
    </svg>
  );
};
