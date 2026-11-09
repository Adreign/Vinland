import React, { useEffect, useState } from "react";
import { collection, getDocs } from "firebase/firestore";
import { db } from "../config/firebase";
import { BarChart3, Download, FileSpreadsheet } from "lucide-react";

export default function Reports() {
  const [loading, setLoading] = useState(true);
  const [totalGardeners, setTotalGardeners] = useState(0);
  const [barangayDistribution, setBarangayDistribution] = useState([]);
  const [gardenTypes, setGardenTypes] = useState([]);
  const [cropAnalytics, setCropAnalytics] = useState([]);

  useEffect(() => {
    fetchReportData();
  }, []);

  async function fetchReportData() {
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

    } catch (e) {
      console.error("Error loading report data:", e);
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
      [],
      ["Barangay", "Gardeners", "Share"],
      ...barangayDistribution.map(b => [`"${b.barangay}"`, b.count, b.percentage])
    ];

    const csvContent = "data:text/csv;charset=utf-8," + csvRows.map(e => e.join(",")).join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `Vinland_Report_${new Date().toISOString().slice(0,10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  return (
    <div className="p-4 sm:p-6 lg:p-8 space-y-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Reports</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Registered gardeners and where they are in Quezon City.
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
          Loading report...
        </div>
      ) : (
        <>
          {/* Registered gardener locations */}
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
        </>
      )}
    </div>
  );
}
