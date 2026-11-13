import React from "react";
import { Link } from "react-router-dom";
import { QRCodeSVG } from "qrcode.react";
import { Download as DownloadIcon, Smartphone, ShieldCheck, ArrowLeft } from "lucide-react";
import { LeafLogo } from "../components/common/Sidebar";

// The APK lives in vinland-web-admin/public/vinland.apk.
// To publish a new version, replace that file and push. The QR code stays the same.
const APK_PATH = "/vinland.apk";
const APP_VERSION = "1.0";

const steps = [
  "Scan the QR code with your phone camera, or tap Download APK.",
  "Open the downloaded file (vinland.apk) from your notifications or Downloads folder.",
  'If Android asks, allow "Install unknown apps" for your browser, then go back.',
  "Tap Install, then Open. Sign up or log in with your Vinland account.",
];

export default function Download() {
  // Full link, e.g. https://your-site.vercel.app/vinland.apk
  const apkUrl = `${window.location.origin}${APK_PATH}`;

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <div className="bg-slate-900 rounded-b-[2.5rem] px-4 pt-12 pb-24 text-center">
        <LeafLogo className="w-16 h-16 mx-auto rounded-2xl" />
        <h1 className="mt-4 text-3xl font-extrabold text-white tracking-tight">Get the Vinland App</h1>
        <p className="mt-1 text-sm text-slate-200">Quezon City Urban Agriculture · Android</p>
      </div>

      <div className="flex-1 px-4 -mt-16 pb-10">
        <div className="max-w-md mx-auto bg-white border border-gray-200 rounded-3xl shadow-xl p-6 sm:p-8 space-y-6">
          <div className="flex flex-col items-center text-center space-y-3">
            <div className="p-4 bg-white border border-gray-200 rounded-2xl">
              <QRCodeSVG value={apkUrl} size={208} level="M" fgColor="#14532d" includeMargin={false} />
            </div>
            <p className="text-sm text-gray-500">Scan with your phone camera to download</p>
          </div>

          <a
            href={APK_PATH}
            download="vinland.apk"
            className="w-full bg-green-700 hover:bg-green-800 text-white font-semibold py-3 rounded-xl transition-colors text-sm flex items-center justify-center gap-2"
          >
            <DownloadIcon size={18} />
            <span>Download APK</span>
          </a>

          <div className="flex justify-center gap-4 text-xs text-gray-500">
            <span className="flex items-center gap-1"><Smartphone size={14} /> Android 7.0 or newer</span>
            <span>Version {APP_VERSION}</span>
          </div>

          <div>
            <h2 className="text-sm font-bold text-gray-900 mb-3">How to install</h2>
            <ol className="space-y-2.5">
              {steps.map((s, i) => (
                <li key={i} className="flex gap-3 text-sm text-gray-700">
                  <span className="shrink-0 w-6 h-6 rounded-full bg-green-100 text-green-800 font-bold text-xs flex items-center justify-center">
                    {i + 1}
                  </span>
                  <span>{s}</span>
                </li>
              ))}
            </ol>
          </div>

          <div className="p-3.5 bg-green-50 border border-green-200 rounded-xl flex items-start gap-2.5 text-green-900 text-xs">
            <ShieldCheck size={16} className="shrink-0 mt-0.5 text-green-600" />
            <span>
              Android may warn that the app is from an unknown source because it isn't on the Play Store. That's expected for this app.
            </span>
          </div>

          <Link to="/login" className="flex items-center justify-center gap-1 text-sm text-gray-600 hover:text-gray-900 font-medium">
            <ArrowLeft size={14} /> Admin sign in
          </Link>
        </div>
      </div>
    </div>
  );
}
