import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ChessIcon } from "../components/icons/ChessIcon.js";
import type { ChessPiece } from "../components/icons/ChessIcon.js";

const PIECES: ChessPiece[] = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];

describe("ChessIcon", () => {
  it.each(PIECES)("renders an SVG element for piece %s with the correct data-testid", (piece) => {
    render(<ChessIcon piece={piece} />);
    const svg = screen.getByTestId(`chess-icon-${piece.toLowerCase()}`);
    expect(svg).toBeInTheDocument();
    expect(svg.tagName.toLowerCase()).toBe("svg");
  });

  it("renders all 6 pieces without throwing", () => {
    for (const piece of PIECES) {
      const { unmount } = render(<ChessIcon piece={piece} />);
      expect(screen.getByTestId(`chess-icon-${piece.toLowerCase()}`)).toBeInTheDocument();
      unmount();
    }
  });

  it("applies the default size of 20 to width and height attributes", () => {
    render(<ChessIcon piece="KING" />);
    const svg = screen.getByTestId("chess-icon-king");
    expect(svg).toHaveAttribute("width", "20");
    expect(svg).toHaveAttribute("height", "20");
  });

  it("applies a custom size when provided", () => {
    render(<ChessIcon piece="QUEEN" size={32} />);
    const svg = screen.getByTestId("chess-icon-queen");
    expect(svg).toHaveAttribute("width", "32");
    expect(svg).toHaveAttribute("height", "32");
  });

  it("forwards an extra className to the SVG element", () => {
    render(<ChessIcon piece="PAWN" className="my-custom-class" />);
    const svg = screen.getByTestId("chess-icon-pawn");
    expect(svg).toHaveClass("my-custom-class");
  });

  it("sets fill to the correct CSS custom property for each piece", () => {
    for (const piece of PIECES) {
      const { unmount } = render(<ChessIcon piece={piece} />);
      const svg = screen.getByTestId(`chess-icon-${piece.toLowerCase()}`);
      expect(svg).toHaveAttribute("fill", `var(--wc-chess-${piece.toLowerCase()})`);
      unmount();
    }
  });

  it("sets aria-hidden to true (decorative icon)", () => {
    render(<ChessIcon piece="KNIGHT" />);
    const svg = screen.getByTestId("chess-icon-knight");
    expect(svg).toHaveAttribute("aria-hidden", "true");
  });
});
