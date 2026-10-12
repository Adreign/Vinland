import React, { useEffect, useState } from "react";
import { doc, onSnapshot } from "firebase/firestore";
import { db } from "../config/firebase";
import { Cpu, Radio, Activity, RefreshCw } from "lucide-react";

export default function SystemMonitoring() {
  const [telemetry, setTelemetry] = useState(null);

  useEffect(() => {
    const unsub = onSnapshot(doc(db, "sensor_devices", "VINLAND-ESP32-01"), (snapshot) => {
      if (snapshot.exists()) {
        setTelemetry(snapshot.data());
      }
    });
    return () => unsub();
  }, []);

  return (
    <div className="p-8 space-y-6 max-w-7xl mx-auto">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">System & ESP32 Telemetry Monitoring</h1>
        <p className="text-sm text-gray-500 mt-0.5">
          Real-time status of paired ESP32 microcontroller nodes and sensor hardware telemetry.
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-6">
        <div className="flex justify-between items-center border-b pb-4">
          <div className="flex items-center space-x-3">
            <div className="p-3 bg-slate-900 text-green-400 rounded-xl">
              <Cpu size={24} />
            </div>
            <div>
              <h3 className="font-bold text-gray-900 text-lg">VINLAND-ESP32-01</h3>
              <p className="text-xs text-gray-500">Quezon City Urban Agriculture Primary Node</p>
            </div>
          </div>
          <span className="flex items-center gap-2 px-3.5 py-1.5 bg-green-50 text-green-700 rounded-full text-xs font-semibold border border-green-200">
            <Radio size={14} className="animate-pulse text-green-600" /> Sensor Stream Connected
          </span>
        </div>

        {telemetry ? (
          <div className="grid grid-cols-1 md:grid-cols-5 gap-4 pt-2">
            <div className="p-5 bg-blue-50/80 rounded-xl border border-blue-100 text-center space-y-1">
              <p className="text-xs font-semibold text-blue-600 uppercase">Soil Moisture</p>
              <p className="text-3xl font-extrabold text-blue-900">{telemetry.moisture}%</p>
            </div>
            <div className="p-5 bg-purple-50/80 rounded-xl border border-purple-100 text-center space-y-1">
              <p className="text-xs font-semibold text-purple-600 uppercase">Soil pH</p>
              <p className="text-3xl font-extrabold text-purple-900">{telemetry.ph}</p>
            </div>
            <div className="p-5 bg-green-50/80 rounded-xl border border-green-100 text-center space-y-1">
              <p className="text-xs font-semibold text-green-600 uppercase">Nitrogen (N)</p>
              <p className="text-3xl font-extrabold text-green-900">{telemetry.nitrogen} ppm</p>
            </div>
            <div className="p-5 bg-amber-50/80 rounded-xl border border-amber-100 text-center space-y-1">
              <p className="text-xs font-semibold text-amber-600 uppercase">Phosphorus (P)</p>
              <p className="text-3xl font-extrabold text-amber-900">{telemetry.phosphorus} ppm</p>
            </div>
            <div className="p-5 bg-emerald-50/80 rounded-xl border border-emerald-100 text-center space-y-1">
              <p className="text-xs font-semibold text-emerald-600 uppercase">Potassium (K)</p>
              <p className="text-3xl font-extrabold text-emerald-900">{telemetry.potassium} ppm</p>
            </div>
          </div>
        ) : (
          <div className="p-8 text-center text-gray-400 text-sm">
            Waiting for sensor telemetry broadcast...
          </div>
        )}
      </div>
    </div>
  );
}
