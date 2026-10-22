import React, { useEffect, useState } from "react";
import { collection, collectionGroup, getDocs, query, orderBy, limit } from "firebase/firestore";
import { db } from "../config/firebase";
import { BarChart3, PieChart, Sprout, Users, Layers, History, Download, FileSpreadsheet, Calendar, Scale } from "lucide-react";

export default function Reports() {
  const [loading, setLoading] = useState(true);
  const [totalGardeners, setTotalGardeners] = useState(0);
  const [barangayDistribution, setBarangayDistribution] = useState([]);
  const [gardenTypes, setGardenTypes] = useState([]);
  const [cropAnalytics, setCropAnalytics] = useState([]);

  // Soil Analytics from real Firestore soil_logs
  const [soilStats, setSoilStats] = useState({
    avgPh: "0.0",
    avgMoisture: "0.0%",
    avgNitrogen: "0.0 ppm",
    avgPhosphorus: "0.0 ppm",
    avgPotassium: "0.0 ppm",
    totalTests: 0,
    optimalCount: 0,
    alertCount: 0
  });

  // Harvest Yield Analytics from real Firestore harvest_logs
  const [harvestStats, setHarvestStats] = useState({
    totalHarvestKg: 0,
    harvestByCrop: []
  });

  // Recent Soil Test History
  const [recentSoilLogs, setRecentSoilLogs] = useState([]);

  useEffect(() => {
    fetchLiveAccurateAnalytics();
  }, []);

  async function fetchLiveAccurateAnalytics() {
    setLoading(true);
    try {
      // 1. Fetch Real Gardeners from 'users'
      const usersSnap = await getDocs(collection(db, "users"));
      const users = usersSnap.docs.map(doc => doc.data());
      const totalG = users.length;
      setTotalGardeners(totalG);

      if (totalG > 0) {
        // Barangay Location Grouping
        const brgyCounts = {};
        users.forEach(u => {
          const brgy = u.barangay || "Quezon City";
          brgyCounts[brgy] = (brgyCounts[brgy] || 0) + 1;
        });
        const brgyList = Object.keys(brgyCounts).map(b => ({
          barangay: b,
          count: brgyCounts[b],
          percentage: `${Math.round((brgyCounts[b] / totalG) * 100)}%`
        })).sort((a, b) => b.count - a.count);
        setBarangayDistribution(brgyList);

        // Garden Typology Grouping
        const typeCounts = {};
        users.forEach(u => {
          const gt = u.gardenType || "Backyard Garden";
          typeCounts[gt] = (typeCounts[gt] || 0) + 1;
        });
        const typeList = Object.keys(typeCounts).map(t => ({
          type: t,
          count: typeCounts[t],
          percentage: `${Math.round((typeCounts[t] / totalG) * 100)}%`
        })).sort((a, b) => b.count - a.count);
        setGardenTypes(typeList);

        // Crop Selection Grouping
        const cropCounts = {};
        users.forEach(u => {
          if (Array.isArray(u.crops)) {
            u.crops.forEach(c => {
              if (c) cropCounts[c] = (cropCounts[c] || 0) + 1;
            });
          }
        });
        const cropList = Object.keys(cropCounts).map(cName => ({
          crop: cName,
          count: cropCounts[cName],
          percentage: `${Math.round((cropCounts[cName] / totalG) * 100)}%`
        })).sort((a, b) => b.count - a.count);
        setCropAnalytics(cropList);
      }

      // 2. Compute Exact Soil Averages from collectionGroup('soil_logs')
      try {
        const soilSnap = await getDocs(collectionGroup(db, "soil_logs"));
        const logs = soilSnap.docs.map(d => ({ id: d.id, ...d.data() }));

        if (logs.length > 0) {
          let sumPh = 0, sumM = 0, sumN = 0, sumP = 0, sumK = 0;
          let opt = 0, alert = 0;

          logs.forEach(l => {
            sumPh += Number(l.ph) || 0;
            sumM += Number(l.moisture) || 0;
            sumN += Number(l.nitrogen) || 0;
            sumP += Number(l.phosphorus) || 0;
            sumK += Number(l.potassium) || 0;

            const st = (l.overallStatus || "").toLowerCase();
            if (st.includes("optimal") || st.includes("good")) opt++;
            else alert++;
          });

          const count = logs.length;
          setSoilStats({
            avgPh: (sumPh / count).toFixed(1),
            avgMoisture: `${(sumM / count).toFixed(1)}%`,
            avgNitrogen: `${(sumN / count).toFixed(1)} ppm`,
            avgPhosphorus: `${(sumP / count).toFixed(1)} ppm`,
            avgPotassium: `${(sumK / count).toFixed(1)} ppm`,
            totalTests: count,
            optimalCount: opt,
            alertCount: alert
          });

          setRecentSoilLogs(logs.slice(0, 10));
        }
      } catch (e) {
        console.warn("Error reading collectionGroup('soil_logs'):", e);
      }

      // 3. Compute Exact Harvest Total Yield from collectionGroup('harvest_logs')
      try {
        const harvestSnap = await getDocs(collectionGroup(db, "harvest_logs"));
        const hLogs = harvestSnap.docs.map(d => ({ id: d.id, ...d.data() }));

        let totalKg = 0;
        const cropKgMap = {};

        hLogs.forEach(h => {
          const qty = Number(h.quantityKg) || 0;
          totalKg += qty;
          const cName = h.cropName || "General Crop";
          cropKgMap[cName] = (cropKgMap[cName] || 0) + qty;
        });

        const cropKgList = Object.keys(cropKgMap).map(c => ({
          cropName: c,
          totalKg: Math.round(cropKgMap[c] * 10) / 10
        })).sort((a, b) => b.totalKg - a.totalKg);

        setHarvestStats({
          totalHarvestKg: Math.round(totalKg * 10) / 10,
          harvestByCrop: cropKgList
        });
      } catch (e) {
        console.warn("Error reading collectionGroup('harvest_logs'):", e);
      }

    } catch (e) {
      console.error("Error calculating live accurate analytics:", e);
    } finally {
      setLoading(false);
    }
  }

  // Export CSV Report with exact numbers
  function handleExportCSV() {
    const csvRows = [
      ["Vinland Quezon City Agricultural Report"],
      ["Report Date", new Date().toLocaleDateString()],
      ["Total Registered Gardeners", totalGardeners],
      ["Total Soil Tests Conducted", soilStats.totalTests],
      ["Average Soil pH", soilStats.avgPh],
      ["Average Soil Moisture", soilStats.avgMoisture],
      ["Average Nitrogen (N)", soilStats.avgNitrogen],
      ["Average Phosphorus (P)", soilStats.avgPhosphorus],
      ["Average Potassium (K)", soilStats.avgPotassium],
      ["Total Harvest Yield Logged (kg)", harvestStats.totalHarvestKg]
    ];

    const csvContent = "data:text/csv;charset=utf-8," + csvRows.map(e => e.join(",")).join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `Vinland_Accurate_Report_${new Date().toISOString().slice(0,10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  return (
    <div className="p-8 space-y-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Analytics & Reports</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Accurate soil & harvest statistical metrics computed dynamically from Firestore collection groups (`soil_logs` and `harvest_logs`).
          </p>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          <button
            onClick={handleExportCSV}
            className="bg-white hover:bg-gray-50 text-gray-800 font-semibold px-4 py-2.5 rounded-xl border border-gray-200 shadow-xs transition-colors flex items-center gap-2 text-sm"
          >
            <Download size={18} className="text-green-700" /> Export CSV Report
          </button>
          <button
            onClick={() => window.print()}
            className="bg-green-700 hover:bg-green-800 text-white font-semibold px-4 py-2.5 rounded-xl shadow-xs transition-colors flex items-center gap-2 text-sm"
          >
            <FileSpreadsheet size={18} /> Print System Report
          </button>
        </div>
      </div>

      {loading ? (
        <div className="p-12 text-center text-gray-500 font-medium">
          Calculating accurate real-time statistical metrics from Firestore...
        </div>
      ) : (
        <>
          {/* 1. EXACT SOIL ANALYTICS METRICS */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
            <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-lg">
              <Layers size={20} className="text-green-700" /> Exact Soil Health Averages (`soil_logs`)
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-5 gap-4 text-xs">
              <div className="p-4 bg-purple-50/80 rounded-xl border border-purple-100 text-center space-y-1">
                <p className="font-bold text-purple-700 uppercase text-[10px]">Avg Soil pH</p>
                <p className="text-2xl font-extrabold text-purple-900">{soilStats.avgPh}</p>
              </div>
              <div className="p-4 bg-blue-50/80 rounded-xl border border-blue-100 text-center space-y-1">
                <p className="font-bold text-blue-700 uppercase text-[10px]">Avg Moisture</p>
                <p className="text-2xl font-extrabold text-blue-900">{soilStats.avgMoisture}</p>
              </div>
              <div className="p-4 bg-green-50/80 rounded-xl border border-green-100 text-center space-y-1">
                <p className="font-bold text-green-700 uppercase text-[10px]">Avg Nitrogen (N)</p>
                <p className="text-2xl font-extrabold text-green-900">{soilStats.avgNitrogen}</p>
              </div>
              <div className="p-4 bg-amber-50/80 rounded-xl border border-amber-100 text-center space-y-1">
                <p className="font-bold text-amber-700 uppercase text-[10px]">Avg Phosphorus (P)</p>
                <p className="text-2xl font-extrabold text-amber-900">{soilStats.avgPhosphorus}</p>
              </div>
              <div className="p-4 bg-emerald-50/80 rounded-xl border border-emerald-100 text-center space-y-1">
                <p className="font-bold text-emerald-700 uppercase text-[10px]">Avg Potassium (K)</p>
                <p className="text-2xl font-extrabold text-emerald-900">{soilStats.avgPotassium}</p>
              </div>
            </div>
          </div>

          {/* 2. HARVEST YIELD ANALYTICS & GARDENER DISTRIBUTION */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Harvest Yield Summary */}
            <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
              <div className="flex justify-between items-center border-b pb-3">
                <h3 className="font-bold text-gray-900 flex items-center gap-2 text-lg">
                  <Scale size={20} className="text-emerald-700" /> Recorded Harvest Yields (`harvest_logs`)
                </h3>
                <span className="text-sm font-extrabold text-emerald-800 bg-emerald-100 px-3 py-1 rounded-xl">
                  Total: {harvestStats.totalHarvestKg} kg
                </span>
              </div>

              {harvestStats.harvestByCrop.length === 0 ? (
                <p className="text-xs text-gray-400 py-6 text-center">No harvest yield logs recorded by gardeners yet.</p>
              ) : (
                <div className="space-y-2">
                  {harvestStats.harvestByCrop.map((item, idx) => (
                    <div key={idx} className="flex justify-between items-center p-3 bg-emerald-50/50 rounded-xl border border-emerald-100 text-xs">
                      <span className="font-bold text-emerald-900">🌱 {item.cropName}</span>
                      <span className="font-extrabold text-emerald-800">{item.totalKg} kg</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Gardener Barangay Distribution */}
            <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
              <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-lg">
                <BarChart3 size={20} className="text-green-700" /> Registered Gardener Locations (`users`)
              </h3>

              {barangayDistribution.length === 0 ? (
                <p className="text-xs text-gray-400 py-6 text-center">No gardener accounts found.</p>
              ) : (
                <div className="space-y-3">
                  {barangayDistribution.map((item, idx) => (
                    <div key={idx} className="space-y-1">
                      <div className="flex justify-between text-xs font-semibold text-gray-700">
                        <span>📍 {item.barangay}</span>
                        <span>{item.count} Gardener{item.count > 1 ? 's' : ''} ({item.percentage})</span>
                      </div>
                      <div className="w-full bg-gray-100 h-2 rounded-full overflow-hidden">
                        <div className="bg-green-600 h-full rounded-full" style={{ width: item.percentage }}></div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* 3. RECENT RECORDED SOIL TESTS TABLE */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
            <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-lg">
              <History size={20} className="text-green-700" /> Recorded Soil Test Log Records
            </h3>

            {recentSoilLogs.length === 0 ? (
              <p className="text-xs text-gray-400 py-6 text-center">No soil test logs recorded in database yet.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full text-xs text-left divide-y divide-gray-100">
                  <thead className="bg-gray-50 font-semibold uppercase text-gray-400">
                    <tr>
                      <th className="p-3">Crop Name</th>
                      <th className="p-3">pH</th>
                      <th className="p-3">Moisture</th>
                      <th className="p-3">Nitrogen (N)</th>
                      <th className="p-3">Phosphorus (P)</th>
                      <th className="p-3">Potassium (K)</th>
                      <th className="p-3">Overall Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {recentSoilLogs.map((log) => (
                      <tr key={log.id} className="hover:bg-gray-50/50">
                        <td className="p-3 font-bold text-gray-900">{log.cropName || "Soil Analysis"}</td>
                        <td className="p-3 text-gray-700">{log.ph}</td>
                        <td className="p-3 text-gray-700">{log.moisture}%</td>
                        <td className="p-3 text-gray-700">{log.nitrogen} ppm</td>
                        <td className="p-3 text-gray-700">{log.phosphorus} ppm</td>
                        <td className="p-3 text-gray-700">{log.potassium} ppm</td>
                        <td className="p-3 font-bold text-green-700">{log.overallStatus || "Recorded"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
