import React, { useEffect, useState } from "react";
import { collection, getDocs } from "firebase/firestore";
import { db } from "../config/firebase";
import { Users, Sprout, Cpu, FileText, ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";

export default function Dashboard() {
  const [stats, setStats] = useState({
    totalGardeners: 0,
    totalCrops: 0,
    activeSensors: 0,
    recentLogCount: 0
  });
  const [recentGardeners, setRecentGardeners] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchDashboardData() {
      try {
        const usersSnap = await getDocs(collection(db, "users"));
        const cropsSnap = await getDocs(collection(db, "crops"));
        const sensorsSnap = await getDocs(collection(db, "sensor_devices"));

        const gardeners = usersSnap.docs.map(doc => ({ id: doc.id, ...doc.data() }));

        setStats({
          totalGardeners: gardeners.length,
          totalCrops: cropsSnap.size,
          activeSensors: sensorsSnap.size,
          recentLogCount: 42
        });

        setRecentGardeners(gardeners.slice(0, 5));
      } catch (err) {
        console.error("Error loading dashboard metrics:", err);
      } finally {
        setLoading(false);
      }
    }

    fetchDashboardData();
  }, []);

  if (loading) {
    return (
      <div className="p-8 text-center text-gray-500 font-medium">
        Loading system indicators...
      </div>
    );
  }

  return (
    <div className="p-8 space-y-8 max-w-7xl mx-auto">
      {/* Title Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">System Dashboard Overview</h1>
        <p className="text-sm text-gray-500 mt-1">
          High-level operational indicators, gardener registrations, and crop metrics for Vinland Quezon City.
        </p>
      </div>

      {/* Metrics Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase text-gray-400 tracking-wider">Registered Gardeners</p>
            <p className="text-3xl font-extrabold text-gray-900 mt-1">{stats.totalGardeners}</p>
          </div>
          <div className="p-3.5 bg-green-50 text-green-700 rounded-xl">
            <Users size={26} />
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase text-gray-400 tracking-wider">Managed Crops</p>
            <p className="text-3xl font-extrabold text-gray-900 mt-1">{stats.totalCrops}</p>
          </div>
          <div className="p-3.5 bg-emerald-50 text-emerald-700 rounded-xl">
            <Sprout size={26} />
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase text-gray-400 tracking-wider">ESP32 Telemetry Nodes</p>
            <p className="text-3xl font-extrabold text-gray-900 mt-1">{stats.activeSensors}</p>
          </div>
          <div className="p-3.5 bg-blue-50 text-blue-700 rounded-xl">
            <Cpu size={26} />
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase text-gray-400 tracking-wider">Soil Analyses Conducted</p>
            <p className="text-3xl font-extrabold text-gray-900 mt-1">{stats.recentLogCount}</p>
          </div>
          <div className="p-3.5 bg-amber-50 text-amber-700 rounded-xl">
            <FileText size={26} />
          </div>
        </div>
      </div>

      {/* Quick Action Tables & Shortcuts */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Recent Registered Gardeners */}
        <div className="lg:col-span-2 bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <div className="flex justify-between items-center pb-3 border-b border-gray-100">
            <div>
              <h3 className="font-bold text-gray-900">Recently Registered Gardeners</h3>
              <p className="text-xs text-gray-500">Latest gardener sign-ups in Quezon City barangays</p>
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
                  <th className="py-2 text-left">Crops Grown</th>
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
                      <td className="py-3 font-semibold text-gray-800">{g.fullName || `${g.firstName} ${g.lastName}`}</td>
                      <td className="py-3 text-gray-600">{g.barangay || "N/A"}</td>
                      <td className="py-3 text-gray-600">{g.gardenType || "Backyard"}</td>
                      <td className="py-3">
                        <span className="text-xs bg-green-50 text-green-700 px-2 py-0.5 rounded font-medium">
                          {g.crops?.length || 0} Crops
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* System Quick Controls */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 border-b border-gray-100 pb-3">Administrator Shortcuts</h3>

          <div className="space-y-3">
            <Link to="/crops" className="block p-4 rounded-xl border border-gray-100 bg-gray-50/50 hover:bg-green-50/50 hover:border-green-200 transition-all">
              <p className="font-semibold text-sm text-gray-800">🌱 Add/Edit Crop Profiles</p>
              <p className="text-xs text-gray-500 mt-1">Manage pH, moisture, and NPK thresholds.</p>
            </Link>

            <Link to="/recommendations" className="block p-4 rounded-xl border border-gray-100 bg-gray-50/50 hover:bg-green-50/50 hover:border-green-200 transition-all">
              <p className="font-semibold text-sm text-gray-800">🎛️ Update Soil Condition Rules</p>
              <p className="text-xs text-gray-500 mt-1">Configure advice triggers for acidic or deficient soil.</p>
            </Link>

            <Link to="/monitoring" className="block p-4 rounded-xl border border-gray-100 bg-gray-50/50 hover:bg-green-50/50 hover:border-green-200 transition-all">
              <p className="font-semibold text-sm text-gray-800">📡 ESP32 Live Telemetry</p>
              <p className="text-xs text-gray-500 mt-1">View real-time soil readings from VINLAND-ESP32-01.</p>
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
