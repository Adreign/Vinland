import React, { useEffect, useState } from "react";
import { collection, getDocs } from "firebase/firestore";
import { db } from "../config/firebase";
import { Users, Sprout, ArrowRight, Sun, CloudRain } from "lucide-react";
import { Link } from "react-router-dom";

// Turns an Open-Meteo weather code into a short label.
function weatherLabel(code) {
  if (code === 0) return "Clear Skies";
  if (code >= 1 && code <= 3) return "Partly Cloudy";
  if (code >= 51 && code <= 67) return "Light Rain Showers";
  if (code >= 80) return "Tropical Rain Showers";
  return "Partly Cloudy";
}

export default function Dashboard() {
  const [stats, setStats] = useState({
    totalGardeners: 0,
    activeGardens: 0,
    totalCrops: 0
  });

  const [recentGardeners, setRecentGardeners] = useState([]);
  const [loading, setLoading] = useState(true);

  // Live Quezon City weather from the free Open-Meteo API
  const [weather, setWeather] = useState({
    temp: "--°C",
    humidity: "--%",
    condition: "Fetching Live Weather..."
  });

  useEffect(() => {
    async function fetchQCWeather() {
      try {
        const res = await fetch("https://api.open-meteo.com/v1/forecast?latitude=14.6760&longitude=121.0437&current=temperature_2m,relative_humidity_2m,weather_code&timezone=Asia%2FManila");
        if (!res.ok) return;
        const json = await res.json();
        if (json.current) {
          setWeather({
            temp: `${Math.round(json.current.temperature_2m)}°C`,
            humidity: `${json.current.relative_humidity_2m}%`,
            condition: weatherLabel(json.current.weather_code)
          });
        }
      } catch (e) {
        console.error("Error fetching live QC weather:", e);
      }
    }
    fetchQCWeather();
  }, []);

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

        setStats({
          totalGardeners: gardeners.length,
          activeGardens: activeG.length,
          totalCrops: cropCount
        });

        setRecentGardeners(gardeners.slice(0, 5));

      } catch (err) {
        console.error("Error fetching accurate dashboard metrics:", err);
      } finally {
        setLoading(false);
      }
    }

    fetchAccurateDashboardData();
  }, []);

  if (loading) {
    return (
      <div className="p-4 sm:p-6 lg:p-8 text-center text-gray-500 font-medium">
        Connecting to Firestore & calculating accurate system metrics...
      </div>
    );
  }

  return (
    <div className="p-4 sm:p-6 lg:p-8 space-y-8 max-w-7xl mx-auto">
      {/* Title Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Admin Module Live Dashboard</h1>
        <p className="text-sm text-gray-500 mt-1">
          Accurate operational metrics calculated directly from Firestore collections (`users`, `crops`).
        </p>
      </div>

      {/* KEY ACCURATE METRICS + LIVE WEATHER */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
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

        {/* Live Quezon City Ambient Weather (Open-Meteo) */}
        <div className="sm:col-span-2 lg:col-span-1 bg-white p-6 rounded-2xl border border-gray-200 shadow-xs space-y-3">
          <p className="text-xs font-semibold uppercase text-gray-400 tracking-wider flex items-center gap-1.5">
            <Sun size={14} className="text-amber-500" /> Live Quezon City Ambient Weather (Open-Meteo)
          </p>
          <div className="p-4 bg-gradient-to-r from-blue-600 to-indigo-700 text-white rounded-xl">
            <div className="flex justify-between items-start">
              <div>
                <p className="text-xs font-semibold text-blue-200">Quezon City, Metro Manila</p>
                <p className="text-3xl font-extrabold mt-1">{weather.temp}</p>
                <p className="text-xs text-blue-100 mt-1 font-medium">{weather.condition}</p>
              </div>
              <CloudRain size={32} className="text-blue-200" />
            </div>
            <p className="text-xs text-blue-100 pt-2 mt-2 border-t border-blue-400/40">
              <span className="text-[10px] uppercase text-blue-300">Humidity</span> {weather.humidity}
            </p>
          </div>
        </div>
      </div>

      {/* Registered gardener accounts */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
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
    </div>
  );
}
