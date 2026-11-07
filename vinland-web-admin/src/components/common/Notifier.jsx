import React, { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, Info, X } from "lucide-react";

/*
 * Friendly replacements for the browser's alert() and confirm().
 *   notify("Saved!")                       -> small message in the corner
 *   notify("Could not save", "error")
 *   if (await confirmAction("Delete it?")) { ... }
 * <Notifier /> is mounted once in App.jsx.
 */

let listener = null;
let nextId = 1;

export function notify(message, type = "success") {
  listener?.({ kind: "toast", id: nextId++, message: String(message), type });
}

export function confirmAction(message, { confirmLabel = "Confirm", danger = false } = {}) {
  return new Promise((resolve) => {
    if (!listener) {
      resolve(window.confirm(message)); // fallback if the notifier isn't mounted
      return;
    }
    listener({ kind: "confirm", message: String(message), confirmLabel, danger, resolve });
  });
}

const TOAST_STYLE = {
  success: { icon: CheckCircle2, cls: "border-green-200 text-green-900", iconCls: "text-green-600" },
  error: { icon: AlertTriangle, cls: "border-red-200 text-red-900", iconCls: "text-red-600" },
  info: { icon: Info, cls: "border-gray-200 text-gray-900", iconCls: "text-gray-600" },
};

export default function Notifier() {
  const [toasts, setToasts] = useState([]);
  const [dialog, setDialog] = useState(null);

  useEffect(() => {
    listener = (event) => {
      if (event.kind === "toast") {
        setToasts((t) => [...t, event]);
        setTimeout(() => setToasts((t) => t.filter((x) => x.id !== event.id)), event.type === "error" ? 7000 : 3500);
      } else {
        setDialog(event);
      }
    };
    return () => { listener = null; };
  }, []);

  useEffect(() => {
    if (!dialog) return;
    const onKey = (e) => { if (e.key === "Escape") close(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  const close = (answer) => {
    dialog?.resolve(answer);
    setDialog(null);
  };

  return (
    <>
      <div className="fixed bottom-4 right-4 left-4 sm:left-auto z-[60] flex flex-col gap-2 sm:w-96" aria-live="polite">
        {toasts.map((t) => {
          const s = TOAST_STYLE[t.type] || TOAST_STYLE.info;
          const Icon = s.icon;
          return (
            <div key={t.id} className={`animate-slide-in bg-white border rounded-2xl shadow-lg px-4 py-3 flex items-start gap-3 text-sm ${s.cls}`}>
              <Icon size={18} className={`shrink-0 mt-0.5 ${s.iconCls}`} />
              <span className="flex-1 leading-snug">{t.message}</span>
              <button
                onClick={() => setToasts((all) => all.filter((x) => x.id !== t.id))}
                className="text-gray-400 hover:text-gray-700"
                aria-label="Dismiss"
              >
                <X size={16} />
              </button>
            </div>
          );
        })}
      </div>

      {dialog && (
        <div className="fixed inset-0 z-[70] bg-gray-900/40 flex items-center justify-center p-4" onClick={() => close(false)}>
          <div
            role="dialog"
            aria-modal="true"
            className="animate-slide-in bg-white rounded-3xl shadow-2xl w-full max-w-sm p-6 space-y-5"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start gap-3">
              <div className={`p-2 rounded-xl ${dialog.danger ? "bg-red-50 text-red-600" : "bg-green-50 text-green-700"}`}>
                <AlertTriangle size={20} />
              </div>
              <p className="text-sm text-gray-800 leading-relaxed pt-1">{dialog.message}</p>
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => close(false)}
                className="px-4 py-2 rounded-xl text-sm font-medium text-gray-700 hover:bg-gray-100"
              >
                Cancel
              </button>
              <button
                autoFocus
                onClick={() => close(true)}
                className={`px-4 py-2 rounded-xl text-sm font-semibold text-white ${dialog.danger ? "bg-red-600 hover:bg-red-700" : "bg-green-700 hover:bg-green-800"}`}
              >
                {dialog.confirmLabel}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
