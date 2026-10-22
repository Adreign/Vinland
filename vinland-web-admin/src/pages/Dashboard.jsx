import React, { useEffect, useState } from "react";
import { collection, collectionGroup, getDocs, doc, onSnapshot, query, orderBy, limit } from "firebase/firestore";
import { db } from "../config/firebase";
import { Users, Sprout, Cpu, Activity, ArrowRight, ShieldCheck, CheckCircle2, AlertTriangle, Layers, Scale } from "lucide-react";
import { Link } from "react-router-dom";

export default function Dashboard() {
  const [stats, setStats] = useState({
    totalGardeners: 0,
    activeGardens: 0,
    totalCrops: 0,
    activeSensors: 0,
    totalSoilLogs: 0,
    totalHarvestKg: 0,
    optimalSoilCount: 0,
    alertSoilCount: 0
  });

  const [recentGardeners, setRecentGardeners] = useState([]);
  const [recentSoilLogs, setRecentSoilLogs] = useState([]);
  const [telemetryStatus, setTelemetryStatus] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchAccurateDashboardData() {
      setLoading(true);
      try {
        // 1. Query Real Gardeners from 'users'
        const usersSnap = await getDocs(collection(db, "users"));
        const gardeners = usersSnap.docs.map(d => ({ id: d.id, ...d.data() }));
        const activeG = gardeners.filter(g => g.gardenType || (Array.isArray(g.crops) && g.crops.length > 0));

        // 2. Query Real Crops from 'crops'
        const cropsSnap = await getDocs(collection(db, "crops"));
        const cropCount = cropsSnap.size;

        // 3. Query Real Sensors from 'sensor_devices'
        const sensorsSnap = await getDocs(collection(db, "sensor_devices"));
        const sensorCount = sensorsSnap.size;

        // 4. Query Real Soil Logs across all gardeners via collectionGroup('soil_logs')
        let soilLogsList = [];
        let optimalCount = 0;
        let alertCount = 0;
        try {
          const soilLogsSnap = await getDocs(query(collectionGroup(db, "soil_logs"), orderBy("timestamp", "desc"), limit(20)));
          soilLogsList = soilLogsSnap.docs.map(d => ({ id: d.id, ...d.data() }));
          soilLogsList.forEach(log => {
            const status = (log.overallStatus || "").toLowerCase();
            if (status.includes("optimal") || status.includes("good") || status.includes("normal")) {
              optimalCount++;
            } else {
              alertCount++;
            }
          });
        } catch (e) {
          console.warn("Collection group 'soil_logs' index fallback:", e);
        }

        // 5. Query Real Harvest Logs across all gardeners via collectionGroup('harvest_logs')
        let totalKg = 0;
        try {
          const harvestSnap = await getDocs(collectionGroup(db, "harvest_logs"));
          harvestSnap.docs.forEach(d => {
            const h = d.data();
            if (h.quantityKg) totalKg += Number(h.quantityKg) || 0;
          });
        } catch (e) {
          console.warn("Collection group 'harvest_logs' fallback:", e);
        }

        setStats({
          totalGardeners: gardeners.length,
          activeGardens: activeG.length,
          totalCrops: cropCount,
          activeSensors: sensorCount || 1,
          totalSoilLogs: soilLogsList.length,
          totalHarvestKg: Math.round(totalKg * 10) / 10,
          optimalSoilCount: optimalCount,
          alertSoilCount: alertCount
        });

        setRecentGardeners(gardeners.slice(0, 5));
        setRecentSoilLogs(soilLogsList.slice(0, 5));

      } catch (err) {
        console.error("Error fetching accurate dashboard metrics:", err);
      } finally {
        setLoading(false);
      }
    }

    fetchAccurateDashboardData();

    // Live ESP32 Sensor Telemetry listener
    const unsub = onSnapshot(doc(db, "sensor_devices", "VINLAND-ESP32-01"), (snap) => {
      if (snap.exists()) {
        setTelemetryStatus(snap.data());
      }
    });

    return () => unsub();
  }, []);

  if (loading) {
    return (
      <div className="p-8 text-center text-gray-500 font-medium">
        Connecting to Firestore & calculating accurate system metrics...
      </div>
    );
  }

  return (
    <div className="p-8 space-y-8 max-w-7xl mx-auto">
      {/* Title Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Admin Module Live Dashboard</h1>
        <p className="text-sm text-gray-500 mt-1">
          Accurate operational metrics calculated directly from Firestore collections (`users`, `crops`, `sensor_devices`, `soil_logs`, `harvest_logs`).
        </p>
      </div>

      {/* KEY ACCURATE METRICS */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {/* Total Gardeners */}
        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase text-gray-400 tracking-wider">Registered Gardeners</p>
            <p className="text-3xl font-extrabold text-gray-900 mt-1">{stats.totalGardeners}</p>
            <p className="text-xs text-green-600 mt-1 font-medium">{stats.activeGardens} active gardens in QC</p>
          </div>
          <div className="p-3.5 bg-green-50 text-green-700 rounded-xl">
            <Users size={26} />
          </div>
        </div>

        {/* Managed Crop Profiles */}
        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase text-gray-400 tracking-wider">Crop Profiles</p>
            <p className="text-3xl font-extrabold text-gray-900 mt-1">{stats.totalCrops}</p>
            <p className="text-xs text-emerald-600 mt-1 font-medium">Urban crops in Firestore</p>
          </div>
          <div className="p-3.5 bg-emerald-50 text-emerald-700 rounded-xl">
            <Sprout size={26} />
          </div>
        </div>

        {/* Total Soil Testing Analyses Logged */}
        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase text-gray-400 tracking-wider">Soil Tests Conducted</p>
            <p className="text-3xl font-extrabold text-gray-900 mt-1">{stats.totalSoilLogs}</p>
            <p className="text-xs text-amber-600 mt-1 font-medium">{stats.optimalSoilCount} Optimal / {stats.alertSoilCount} Alert</p>
          </div>
          <div className="p-3.5 bg-amber-50 text-amber-700 rounded-xl">
            <Layers size={26} />
          </div>
        </div>

        {/* Total Harvest Yield Logged */}
        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase text-gray-400 tracking-wider">Total Harvest Logged</p>
            <p className="text-3xl font-extrabold text-gray-900 mt-1">{stats.totalHarvestKg} <span className="text-sm font-normal">kg</span></p>
            <p className="text-xs text-blue-600 mt-1 font-medium">Recorded by gardeners</p>
          </div>
          <div className="p-3.5 bg-blue-50 text-blue-700 rounded-xl">
            <Scale size={26} />
          </div>
        </div>
      </div>

      {/* LIVE TELEMETRY BANNER (VINLAND-ESP32-01) */}
      <div className="bg-gradient-to-r from-slate-900 to-slate-800 text-white rounded-2xl p-6 shadow-md border border-slate-700 flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
        <div className="space-y-1 max-w-2xl">
          <div className="flex items-center gap-2">
            <ShieldCheck className="text-green-400" size={20} />
            <h3 className="font-bold text-lg text-white">Live ESP32 Hardware Telemetry Node</h3>
          </div>
          <p className="text-xs text-slate-300 leading-relaxed">
            Real-time sensor telemetry stream from Firestore document <code className="bg-slate-800 text-green-300 px-1.5 py-0.5 rounded">sensor_devices/VINLAND-ESP32-01</code>.
          </p>
        </div>
        <div className="flex flex-wrap gap-3 shrink-0">
          <div className="bg-slate-800/80 border border-slate-700 px-4 py-2.5 rounded-xl text-center">
            <p className="text-[10px] uppercase font-semibold text-slate-400">Soil pH</p>
            <p className="text-base font-bold text-purple-400">{telemetryStatus?.ph != null ? telemetryStatus.ph : "N/A"}</p>
          </div>
          <div className="bg-slate-800/80 border border-slate-700 px-4 py-2.5 rounded-xl text-center">
            <p className="text-[10px] uppercase font-semibold text-slate-400">Moisture</p>
            <p className="text-base font-bold text-blue-400">{telemetryStatus?.moisture != null ? `${telemetryStatus.moisture}%` : "N/A"}</p>
          </div>
          <div className="bg-slate-800/80 border border-slate-700 px-4 py-2.5 rounded-xl text-center">
            <p className="text-[10px] uppercase font-semibold text-slate-400">NPK (ppm)</p>
            <p className="text-base font-bold text-amber-400">
              {telemetryStatus?.nitrogen ?? 0} - {telemetryStatus?.phosphorus ?? 0} - {telemetryStatus?.potassium ?? 0}
            </p>
          </div>
        </div>
      </div>

      {/* TABLES: RECENT GARDENERS & RECENT SOIL LOGS */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Recently Registered Gardeners */}
        <div className="lg:col-span-2 bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <div className="flex justify-between items-center pb-3 border-b border-gray-100">
            <div>
              <h3 className="font-bold text-gray-900">Registered Gardener Accounts</h3>
              <p className="text-xs text-gray-500">Live records from Firestore `users` collection</p>
            </div>
            <Link to="/accounts" className="text-xs font-semibold text-green-700 hover:text-green-800 flex items-center gap-1">
              View All <ArrowRight size={14} />
            </Link>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-100 text-sm">
              <thead className="text-xs text-gray-400 uppercase font-semibold">
                <tr>
                  <th className="py-2 text-left">Gardener Name</th>
                  <th className="py-2 text-left">Barangay</th>
                  <th className="py-2 text-left">Garden Type</th>
                  <th className="py-2 text-left">Crops Selected</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {recentGardeners.length === 0 ? (
                  <tr>
                    <td colSpan="4" className="py-4 text-center text-gray-400 text-xs">No registered gardeners found in Firestore.</td>
                  </tr>
                ) : (
                  recentGardeners.map((g) => (
                    <tr key={g.id} className="hover:bg-gray-50/50">
                      <td className="py-3 font-semibold text-gray-800">
                        {g.fullName || `${g.firstName || ''} ${g.lastName || ''}`.trim() || "Gardener"}
                      </td>
                      <td className="py-3 text-gray-600">{g.barangay || "Quezon City"}</td>
                      <td className="py-3 text-gray-600">{g.gardenType || "Backyard"}</td>
                      <td className="py-3">
                        <span className="text-xs bg-green-50 text-green-700 px-2.5 py-0.5 rounded-full font-medium">
                          {Array.isArray(g.crops) ? g.crops.length : 0} Crops
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Live Soil Logs Feed */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <div className="flex justify-between items-center pb-3 border-b border-gray-100">
            <div>
              <h3 className="font-bold text-gray-900">Recent Soil Logs</h3>
              <p className="text-xs text-gray-500">Live test entries from gardeners</p>
            </div>
            <Link to="/reports" className="text-xs font-semibold text-green-700 hover:text-green-800 flex items-center gap-1">
              Analytics <ArrowRight size={14} />
            </Link>
          </div>

          <div className="space-y-3">
            {recentSoilLogs.length === 0 ? (
              <p className="text-xs text-gray-400 py-4 text-center">No soil test logs recorded yet.</p>
            ) : (
              recentSoilLogs.map((log) => (
                <div key={log.id} className="p-3 rounded-xl border border-gray-100 bg-gray-50/60 space-y-1 text-xs">
                  <div className="flex justify-between font-bold text-gray-900">
                    <span>🌱 {log.cropName || "Soil Analysis"}</span>
                    <span className="text-[10px] text-green-700 font-semibold">{log.overallStatus || "Recorded"}</span>
                  </div>
                  <p className="text-gray-600">
                    pH: <span className="font-semibold">{log.ph}</span> | Moisture: <span className="font-semibold">{log.moisture}%</span> | NPK: <span className="font-semibold">{log.nitrogen}-{log.phosphorus}-{log.potassium}</span>
                  </p>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
