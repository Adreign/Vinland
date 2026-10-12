import React from "react";
import { BarChart3, PieChart, TrendingUp, Download } from "lucide-react";

export default function Reports() {
  const barangayDistribution = [
    { barangay: "Batasan Hills", count: 18, percentage: "28%" },
    { barangay: "Commonwealth", count: 14, percentage: "22%" },
    { barangay: "Holy Spirit", count: 12, percentage: "19%" },
    { barangay: "Payatas", count: 10, percentage: "15%" },
    { barangay: "Tandang Sora", count: 10, percentage: "16%" }
  ];

  const gardenTypes = [
    { type: "Backyard Garden", count: 32, percentage: "50%" },
    { type: "Rooftop Garden", count: 20, percentage: "31%" },
    { type: "Community Plot", count: 12, percentage: "19%" }
  ];

  return (
    <div className="p-8 space-y-6 max-w-7xl mx-auto">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Agricultural Reports & Analytics</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Statistical breakdown of urban gardener distribution, garden typologies, and crop adoption in Quezon City.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Barangay Distribution */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2">
            <BarChart3 size={20} className="text-green-700" /> Gardener Distribution by Barangay
          </h3>
          <div className="space-y-3">
            {barangayDistribution.map((item, idx) => (
              <div key={idx} className="space-y-1">
                <div className="flex justify-between text-xs font-semibold text-gray-700">
                  <span>📍 {item.barangay}</span>
                  <span>{item.count} Gardeners ({item.percentage})</span>
                </div>
                <div className="w-full bg-gray-100 h-2.5 rounded-full overflow-hidden">
                  <div className="bg-green-600 h-full rounded-full" style={{ width: item.percentage }}></div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Garden Typologies */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2">
            <PieChart size={20} className="text-emerald-700" /> Urban Garden Typologies
          </h3>
          <div className="space-y-4 pt-2">
            {gardenTypes.map((gt, idx) => (
              <div key={idx} className="flex justify-between items-center p-3.5 bg-gray-50 rounded-xl border border-gray-100">
                <span className="font-semibold text-sm text-gray-800">{gt.type}</span>
                <span className="text-xs font-bold px-3 py-1 bg-green-100 text-green-800 rounded-lg">{gt.count} ({gt.percentage})</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
