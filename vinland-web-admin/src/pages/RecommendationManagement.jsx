import React, { useState } from "react";
import { Sliders, Plus, Edit2, AlertCircle, CheckCircle2 } from "lucide-react";

export default function RecommendationManagement() {
  const [rules, setRules] = useState([
    { id: "r1", parameter: "Soil pH", condition: "< 5.5", status: "Too Acidic", advice: "Incorporate agricultural lime or powdered eggshells to raise soil pH.", severity: "Warning" },
    { id: "r2", parameter: "Soil pH", condition: "> 7.5", status: "Too Alkaline", advice: "Add organic compost, peat moss, or sulfur to lower soil pH.", severity: "Warning" },
    { id: "r3", parameter: "Nitrogen (N)", condition: "< 100 ppm", status: "Deficient", advice: "Apply nitrogen-rich organic fertilizers such as vermicompost or fish amino acid.", severity: "Caution" },
    { id: "r4", parameter: "Soil Moisture", condition: "< 35%", status: "Too Dry", advice: "Increase watering schedule and apply coconut coir mulch to preserve root moisture.", severity: "Warning" }
  ]);

  return (
    <div className="p-8 space-y-6 max-w-7xl mx-auto">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Recommendation & Soil Condition Rules</h1>
        <p className="text-sm text-gray-500 mt-0.5">
          Configure soil parameter classification limits and actionable treatment advice for gardeners.
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
        <div className="flex justify-between items-center pb-4 border-b">
          <h3 className="font-bold text-gray-900 flex items-center gap-2">
            <Sliders size={20} className="text-green-700" /> Agronomic Soil Rules
          </h3>
        </div>

        <div className="space-y-3">
          {rules.map((rule) => (
            <div key={rule.id} className="p-4 rounded-xl border border-gray-100 bg-gray-50/70 flex justify-between items-center">
              <div>
                <div className="flex items-center space-x-2">
                  <span className="font-bold text-sm text-gray-900">{rule.parameter} ({rule.condition})</span>
                  <span className="px-2.5 py-0.5 bg-amber-100 text-amber-800 rounded-full text-xs font-semibold">
                    {rule.status}
                  </span>
                </div>
                <p className="text-xs text-gray-600 mt-1">
                  💡 <span className="font-semibold text-gray-800">Actionable Advice:</span> {rule.advice}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
