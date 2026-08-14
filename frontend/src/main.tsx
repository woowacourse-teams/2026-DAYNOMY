import * as Sentry from "@sentry/react";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App.tsx";
import { initSentry } from "./monitoring/sentry.ts";

initSentry();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Sentry.ErrorBoundary
      fallback={
        <main role="alert">
          <h1>문제가 발생했습니다.</h1>
          <p>페이지를 새로고침하거나 잠시 후 다시 시도해 주세요.</p>
          <a href="/">홈으로 이동</a>
        </main>
      }
    >
      <App />
    </Sentry.ErrorBoundary>
  </StrictMode>,
);
