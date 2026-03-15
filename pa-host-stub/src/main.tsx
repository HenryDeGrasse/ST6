import React from "react";
import ReactDOM from "react-dom/client";
import { HostShell } from "./HostShell.js";

const root = document.getElementById("root");
if (root) {
  ReactDOM.createRoot(root).render(
    <React.StrictMode>
      <HostShell />
    </React.StrictMode>,
  );
}
