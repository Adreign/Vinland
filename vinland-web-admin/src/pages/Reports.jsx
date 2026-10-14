import React, { useEffect, useState } from "react";
import { collection, getDocs } from "firebase/firestore";
import { db } from "../config/firebase";
import { BarChart3, PieChart, Sprout, Users } from "lucide-react";

export default function Reports() {
  const [loading, setLoading] = useState(true);
  const [totalGardeners, setTotalGardeners] = useState(0);
  const [barangayDistribution, setBarangayDistribution] = useState([]);
  const [gardenTypes, setGardenTypes] = useState([]);
  const [cropAnalytics, setCropAnalytics] = useState([]);

  useEffect(() => {
    fetchLiveAnalytics();
  }, []);

  async function fetchLiveAnalytics() {
    setLoading(true);
    try {
      const snap = await getDocs(collection(db, "users"));
      const users = snap.docs.map(doc => doc.data());
      const total = users.length;
      setTotalGardeners(total);

      if (total === 0) {
        setBarangayDistribution([]);
        setGardenTypes([]);
        setCropAnalytics([]);
        setLoading(false);
        return;
      }

      // 1. Group dynamically by Barangay Location
      const barangayCounts = {};
      users.forEach(u => {
        const brgy = u.barangay || "Unspecified Barangay";
        barangayCounts[brgy] = (barangayCounts[brgy] || 0) + 1;
      });

      const barangayList = Object.keys(barangayCounts).map(brgy => {
        const count = barangayCounts[brgy];
        const pct = Math.round((count / total) * 100);
        return { barangay: brgy, count, percentage: `${pct}%` };
      }).sort((a, b) => b.count - a.count);

      setBarangayDistribution(barangayList);

      // 2. Group dynamically by Garden Type (Backyard, Rooftop, Community Plot)
      const typeCounts = {};
      users.forEach(u => {
        const gType = u.gardenType || "Backyard";
        typeCounts[gType] = (typeCounts[gType] || 0) + 1;
      });

      const typeList = Object.keys(typeCounts).map(gType => {
        const count = typeCounts[gType];
        const pct = Math.round((count / total) * 100);
        return { type: gType, count, percentage: `${pct}%` };
      }).sort((a, b) => b.count - a.count);

      setGardenTypes(typeList);

      // 3. Group dynamically by Crops Selected by Gardeners
      const cropCounts = {};
      users.forEach(u => {
        if (Array.isArray(u.crops)) {
          u.crops.forEach(c => {
            if (c) {
              cropCounts[c] = (cropCounts[c] || 0) + 1;
            }
          });
        }
      });

      const cropList = Object.keys(cropCounts).map(cropName => {
        const count = cropCounts[cropName];
        const pct = Math.round((count / total) * 100);
        return { crop: cropName, count, percentage: `${pct}%` };
      }).sort((a, b) => b.count - a.count);

      setCropAnalytics(cropList);

    } catch (e) {
      console.error("Error computing live agricultural analytics:", e);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="p-8 space-y-6 max-w-7xl mx-auto">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Agricultural Reports & Analytics</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Real-time dynamic analytics calculated live from registered gardeners in Firestore.
          </p>
        </div>
        <div className="px-4 py-2.5 bg-green-50 text-green-800 rounded-xl text-xs font-semibold border border-green-200 flex items-center gap-2 shadow-xs">
          <Users size={16} /> Total Registered Gardeners: <span className="text-sm font-extrabold text-green-900">{totalGardeners}</span>
        </div>
      </div>

      {loading ? (
        <div className="p-12 text-center text-gray-500 font-medium">
          Calculating real-time agricultural analytics from Firestore...
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Barangay Distribution */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
            <h3 className="font-bold text-gray-900 flex items-center gap-2">
              <BarChart3 size={20} className="text-green-700" /> Gardener Distribution by Barangay
            </h3>

            {barangayDistribution.length === 0 ? (
              <p className="text-xs text-gray-400 py-4 text-center">No gardener location data found in database.</p>
            ) : (
              <div className="space-y-3">
                {barangayDistribution.map((item, idx) => (
                  <div key={idx} className="space-y-1">
                    <div className="flex justify-between text-xs font-semibold text-gray-700">
                      <span>📍 {item.barangay}</span>
                      <span>{item.count} Gardener{item.count > 1 ? 's' : ''} ({item.percentage})</span>
                    </div>
                    <div className="w-full bg-gray-100 h-2.5 rounded-full overflow-hidden">
                      <div className="bg-green-600 h-full rounded-full transition-all duration-500" style={{ width: item.percentage }}></div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Garden Typologies */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
            <h3 className="font-bold text-gray-900 flex items-center gap-2">
              <PieChart size={20} className="text-emerald-700" /> Urban Garden Typologies
            </h3>

            {gardenTypes.length === 0 ? (
              <p className="text-xs text-gray-400 py-4 text-center">No garden typology data found in database.</p>
            ) : (
              <div className="space-y-3.5 pt-1">
                {gardenTypes.map((gt, idx) => (
                  <div key={idx} className="flex justify-between items-center p-3.5 bg-gray-50 rounded-xl border border-gray-100">
                    <span className="font-semibold text-sm text-gray-800">{gt.type}</span>
                    <span className="text-xs font-bold px-3 py-1 bg-green-100 text-green-800 rounded-lg">
                      {gt.count} Gardener{gt.count > 1 ? 's' : ''} ({gt.percentage})
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Crop Adoption Analytics */}
          <div className="lg:col-span-2 bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
            <h3 className="font-bold text-gray-900 flex items-center gap-2">
              <Sprout size={20} className="text-emerald-700" /> Most Popular Crops Adopted by Registered Gardeners
            </h3>

            {cropAnalytics.length === 0 ? (
              <p className="text-xs text-gray-400 py-4 text-center">No crop selection records found in database.</p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
                {cropAnalytics.map((c, idx) => (
                  <div key={idx} className="p-3.5 bg-green-50/60 rounded-xl border border-green-100 flex justify-between items-center">
                    <span className="font-semibold text-sm text-gray-800">🌱 {c.crop}</span>
                    <span className="text-xs font-bold bg-green-700 text-white px-2.5 py-1 rounded-lg">
                      {c.count} ({c.percentage})
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
