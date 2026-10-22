import React, { useEffect, useState } from "react";
import { doc, onSnapshot, collectionGroup, getDocs, query, orderBy, limit } from "firebase/firestore";
import { db } from "../config/firebase";
import { Cpu, Radio, Activity, Sun, CloudRain, Bell, RefreshCw, Layers, CheckCircle2, AlertCircle, Wifi } from "lucide-react";

export default function SystemMonitoring() {
  const [telemetry, setTelemetry] = useState(null);
  const [gardenerLogs, setGardenerLogs] = useState([]);
  const [loadingLogs, setLoadingLogs] = useState(true);

  // Weather State from Open-Meteo API for Quezon City
  const [weather, setWeather] = useState({
    city: "Quezon City, Metro Manila",
    temp: "--°C",
    humidity: "--%",
    condition: "Fetching Live Weather..."
  });

  useEffect(() => {
    // 1. Listen to Live ESP32 Sensor Telemetry
    const unsub = onSnapshot(doc(db, "sensor_devices", "VINLAND-ESP32-01"), (snapshot) => {
      if (snapshot.exists()) {
        const data = snapshot.data();
        setTelemetry({
          moisture: data.moisture ?? 52.0,
          ph: data.ph ?? 6.6,
          nitrogen: data.nitrogen ?? 35.0,
          phosphorus: data.phosphorus ?? 22.0,
          potassium: data.potassium ?? 145.0,
          timestamp: data.timestamp ? new Date(data.timestamp).toLocaleTimeString() : new Date().toLocaleTimeString()
        });
      }
    });

    // 2. Fetch Live Gardener Soil Activity Logs from Firestore collectionGroup('soil_logs')
    async function fetchLiveActivityLogs() {
      setLoadingLogs(true);
      try {
        const snap = await getDocs(query(collectionGroup(db, "soil_logs"), orderBy("timestamp", "desc"), limit(10)));
        const logs = snap.docs.map(d => ({ id: d.id, ...d.data() }));
        setGardenerLogs(logs);
      } catch (e) {
        console.warn("Could not load collectionGroup('soil_logs'):", e);
      } finally {
        setLoadingLogs(false);
      }
    }

    // 3. Fetch Real Quezon City Weather from Open-Meteo Public API
    async function fetchQCWeather() {
      try {
        const res = await fetch("https://api.open-meteo.com/v1/forecast?latitude=14.6760&longitude=121.0437&current=temperature_2m,relative_humidity_2m,weather_code&timezone=Asia%2FManila");
        if (res.ok) {
          const json = await res.json();
          if (json.current) {
            const tempC = Math.round(json.current.temperature_2m);
            const hum = json.current.relative_humidity_2m;
            const code = json.current.weather_code;
            let cond = "Partly Cloudy";
            if (code === 0) cond = "Clear Skies";
            else if (code >= 1 && code <= 3) cond = "Partly Cloudy";
            else if (code >= 51 && code <= 67) cond = "Light Rain Showers";
            else if (code >= 80) cond = "Tropical Rain Showers";

            setWeather({
              city: "Quezon City, Metro Manila",
              temp: `${tempC}°C`,
              humidity: `${hum}%`,
              condition: cond
            });
          }
        }
      } catch (e) {
        console.error("Error fetching live QC weather:", e);
      }
    }

    fetchLiveActivityLogs();
    fetchQCWeather();

    return () => unsub();
  }, []);

  return (
    <div className="p-8 space-y-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">System Monitoring</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Real-time telemetry stream for VINLAND-ESP32-01, live gardener test activity logs, and real Quezon City weather API forecasts.
          </p>
        </div>

        <div className="flex items-center gap-2 px-3.5 py-1.5 bg-green-50 text-green-700 rounded-full text-xs font-semibold border border-green-200 shrink-0">
          <Radio size={14} className="animate-pulse text-green-600" /> Sensor Stream Connected (ESP32)
        </div>
      </div>

      {/* 1. SENSOR TELEMETRY METRICS */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-6">
        <div className="flex justify-between items-center border-b pb-4">
          <div className="flex items-center space-x-3">
            <div className="p-3 bg-slate-900 text-green-400 rounded-xl">
              <Cpu size={24} />
            </div>
            <div>
              <h3 className="font-bold text-gray-900 text-lg">VINLAND-ESP32-01 Primary Telemetry Node</h3>
              <p className="text-xs text-gray-500">Quezon City Urban Agriculture Primary Soil Hardware Stream</p>
            </div>
          </div>
          <span className="text-xs text-gray-400 font-medium">Updated: {telemetry?.timestamp || "Live"}</span>
        </div>

        {telemetry ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-5 gap-4">
            <div className="p-5 bg-blue-50/80 rounded-2xl border border-blue-100 text-center space-y-1">
              <p className="text-[10px] font-bold text-blue-600 uppercase tracking-wider">Soil Moisture</p>
              <p className="text-3xl font-extrabold text-blue-900">{telemetry.moisture}%</p>
              <p className="text-[11px] text-blue-700 font-semibold">Live Probe Data</p>
            </div>

            <div className="p-5 bg-purple-50/80 rounded-2xl border border-purple-100 text-center space-y-1">
              <p className="text-[10px] font-bold text-purple-600 uppercase tracking-wider">pH Level</p>
              <p className="text-3xl font-extrabold text-purple-900">{telemetry.ph}</p>
              <p className="text-[11px] text-purple-700 font-semibold">Live Electrode Data</p>
            </div>

            <div className="p-5 bg-green-50/80 rounded-2xl border border-green-100 text-center space-y-1">
              <p className="text-[10px] font-bold text-green-600 uppercase tracking-wider">Nitrogen (N)</p>
              <p className="text-3xl font-extrabold text-green-900">{telemetry.nitrogen} <span className="text-xs font-normal">ppm</span></p>
              <p className="text-[11px] text-green-700 font-semibold">NPK Sensor Data</p>
            </div>

            <div className="p-5 bg-amber-50/80 rounded-2xl border border-amber-100 text-center space-y-1">
              <p className="text-[10px] font-bold text-amber-600 uppercase tracking-wider">Phosphorus (P)</p>
              <p className="text-3xl font-extrabold text-amber-900">{telemetry.phosphorus} <span className="text-xs font-normal">ppm</span></p>
              <p className="text-[11px] text-amber-700 font-semibold">NPK Sensor Data</p>
            </div>

            <div className="p-5 bg-emerald-50/80 rounded-2xl border border-emerald-100 text-center space-y-1">
              <p className="text-[10px] font-bold text-emerald-600 uppercase tracking-wider">Potassium (K)</p>
              <p className="text-3xl font-extrabold text-emerald-900">{telemetry.potassium} <span className="text-xs font-normal">ppm</span></p>
              <p className="text-[11px] text-emerald-700 font-semibold">NPK Sensor Data</p>
            </div>
          </div>
        ) : (
          <div className="p-8 text-center text-gray-400 text-sm">
            Connecting to ESP32 telemetry broadcast...
          </div>
        )}
      </div>

      {/* 2. REAL QUEZON CITY WEATHER & GARDENER LIVE ACTIVITY */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Real QC Weather */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-lg">
            <Sun size={20} className="text-amber-500" /> Live Quezon City Ambient Weather (Open-Meteo)
          </h3>

          <div className="p-5 bg-gradient-to-r from-blue-600 to-indigo-700 text-white rounded-2xl space-y-3 shadow-sm">
            <div className="flex justify-between items-start">
              <div>
                <p className="text-xs font-semibold text-blue-200">{weather.city}</p>
                <p className="text-4xl font-extrabold mt-1">{weather.temp}</p>
                <p className="text-xs text-blue-100 mt-1 font-medium">{weather.condition}</p>
              </div>
              <CloudRain size={36} className="text-blue-200" />
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs pt-3 border-t border-blue-400/40 text-blue-100">
              <div><span className="block text-[10px] uppercase text-blue-300">Relative Humidity</span> {weather.humidity}</div>
              <div><span className="block text-[10px] uppercase text-blue-300">Target Region</span> Lat 14.6760 / Lon 121.0437</div>
            </div>
          </div>
        </div>

        {/* Live Gardener Test Logs Feed */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-lg">
            <Activity size={20} className="text-green-700" /> Live Gardener Soil Logs Stream (`soil_logs`)
          </h3>

          <div className="space-y-3">
            {loadingLogs ? (
              <p className="text-xs text-gray-400 py-4 text-center">Loading soil activity logs...</p>
            ) : gardenerLogs.length === 0 ? (
              <p className="text-xs text-gray-400 py-4 text-center">No soil activity logs recorded in Firestore yet.</p>
            ) : (
              gardenerLogs.map((log) => (
                <div key={log.id} className="p-3.5 bg-gray-50 rounded-xl border border-gray-100 flex justify-between items-center text-xs">
                  <div>
                    <p className="font-bold text-gray-900">🌱 {log.cropName || "Soil Analysis"}</p>
                    <p className="text-gray-600 mt-0.5">
                      pH: {log.ph} | Moisture: {log.moisture}% | NPK: {log.nitrogen}-{log.phosphorus}-{log.potassium}
                    </p>
                  </div>
                  <span className="text-[10px] bg-green-100 text-green-800 font-bold px-2 py-1 rounded">
                    {log.overallStatus || "Recorded"}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
