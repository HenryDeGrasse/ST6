import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { PixelBackground } from "../components/PixelBackground.js";
import { ThemeProvider } from "../theme/ThemeContext.js";

describe("PixelBackground", () => {
  it("renders nothing when the canvas 2D context is unavailable", () => {
    render(
      <ThemeProvider>
        <PixelBackground />
      </ThemeProvider>,
    );

    expect(screen.queryByTestId("pixel-background")).not.toBeInTheDocument();
  });
});
