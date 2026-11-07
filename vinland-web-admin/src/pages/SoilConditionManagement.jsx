import React, { useEffect, useState } from "react";
import { collection, getDocs, doc, setDoc } from "firebase/firestore";
import { db } from "../config/firebase";
import { Layers, Sliders, AlertTriangle, ShieldAlert, CheckCircle2, Save, RefreshCw, Info } from "lucide-react";
import { notify } from "../components/common/Notifier";

export default function SoilConditionManagement() {
  const [loading, setLoading] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);

  // 1. Manage NPK Ranges (HIPO: Manage NPK Ranges)
  const [npkRanges, setNpkRanges] = useState({
    nitrogenLow: 20,
    nitrogenOptimalMin: 21,
    nitrogenOptimalMax: 50,
    nitrogenHigh: 51,
    phosphorusLow: 15,
    phosphorusOptimalMin: 16,
    phosphorusOptimalMax: 40,
    phosphorusHigh: 41,
    potassiumLow: 100,
    potassiumOptimalMin: 101,
    potassiumOptimalMax: 200,
    potassiumHigh: 201
  });

  // 2. Manage pH Ranges (HIPO: Manage pH Ranges)
  const [phRanges, setPhRanges] = useState({
    acidicThreshold: 5.8,
    optimalPhMin: 6.0,
    optimalPhMax: 7.0,
    alkalineThreshold: 7.5
  });

  // 3. Manage Soil Moisture Ranges (HIPO: Manage Soil Moisture Ranges)
  const [moistureRanges, setMoistureRanges] = useState({
    dryThreshold: 35,
    optimalMoistureMin: 40,
    optimalMoistureMax: 70,
    wetThreshold: 75
  });

  // 4. Set Optimal Soil Conditions (HIPO: Set Optimal Soil Conditions)
  const [optimalGlobal, setOptimalGlobal] = useState({
    targetSoilType: "Loamy Topsoil with Compost",
    organicMatterTarget: "5% - 8%",
    ecTarget: "1.2 - 2.0 dS/m",
    soilTempTarget: "24°C - 30°C (Quezon City Climate)"
  });

  // 5 & 6. Soil Deficiency & Excess Rules (HIPO: Manage Soil Deficiency Rules & Manage Soil Excess Rules)
  const [deficiencyRules, setDeficiencyRules] = useState([
    { id: "d1", param: "Nitrogen (N) Deficiency", trigger: "< 20 ppm", symptom: "Yellowing older leaves (chlorosis), stunted growth", remediation: "Apply blood meal, vermicompost, or fermented plant juice." },
    { id: "d2", param: "Phosphorus (P) Deficiency", trigger: "< 15 ppm", symptom: "Purplish underside of leaves, delayed root development", remediation: "Incorporate bone meal or rock phosphate into root zone." },
    { id: "d3", param: "Potassium (K) Deficiency", trigger: "< 100 ppm", symptom: "Browning leaf margins, weak stems in leafy vegetables", remediation: "Add wood ash, banana peel liquid fertilizer, or sulfate of potash." }
  ]);

  const [excessRules, setExcessRules] = useState([
    { id: "e1", param: "Nitrogen (N) Excess", trigger: "> 60 ppm", symptom: "Dark green brittle leaves, excessive foliage with poor root mass", remediation: "Flush soil with heavy irrigation and pause nitrogenous fertilizers." },
    { id: "e2", param: "Soil Salinity / Over-Fertilization", trigger: "EC > 2.5 dS/m", symptom: "Leaf tip burn, root damage, wilting despite moist soil", remediation: "Leach potting mix with clean water to drain excess soluble salts." }
  ]);

  useEffect(() => {
    fetchSoilConfigurations();
  }, []);

  async function fetchSoilConfigurations() {
    setLoading(true);
    try {
      const snap = await getDocs(collection(db, "soil_condition_config"));
      if (!snap.empty) {
        snap.forEach(docSnap => {
          const data = docSnap.data();
          if (docSnap.id === "npk") setNpkRanges(data);
          if (docSnap.id === "ph") setPhRanges(data);
          if (docSnap.id === "moisture") setMoistureRanges(data);
        });
      }
    } catch (e) {
      console.error("Error loading soil configs:", e);
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveGlobalConfig() {
    setLoading(true);
    try {
      await setDoc(doc(db, "soil_condition_config", "npk"), npkRanges, { merge: true });
      await setDoc(doc(db, "soil_condition_config", "ph"), phRanges, { merge: true });
      await setDoc(doc(db, "soil_condition_config", "moisture"), moistureRanges, { merge: true });
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
    } catch (e) {
      notify("Failed to save soil condition parameters: " + e.message, "error");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="p-4 sm:p-6 lg:p-8 space-y-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Soil Condition Management</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Set global NPK, pH, and soil moisture thresholds, set optimal soil benchmarks, and configure deficiency & excess remediation rules.
          </p>
        </div>

        <button
          onClick={handleSaveGlobalConfig}
          disabled={loading}
          className="bg-green-700 hover:bg-green-800 text-white font-semibold px-5 py-2.5 rounded-xl shadow-xs transition-colors flex items-center gap-2 text-sm shrink-0"
        >
          {loading ? <RefreshCw size={18} className="animate-spin" /> : <Save size={18} />}
          <span>Save Soil Parameters</span>
        </button>
      </div>

      {saveSuccess && (
        <div className="p-4 bg-green-50 border border-green-200 text-green-800 rounded-xl text-xs font-semibold flex items-center gap-2">
          <CheckCircle2 size={18} className="text-green-600" />
          Soil condition ranges and optimal limits successfully saved to Firestore!
        </div>
      )}

      {/* 1, 2, 3: NPK, pH, and Moisture Range Configurator */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* HIPO: Manage NPK Ranges */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-base">
            <Layers size={20} className="text-green-700" /> NPK Range Thresholds (ppm)
          </h3>

          <div className="space-y-3 text-xs">
            <div>
              <p className="font-semibold text-gray-700 mb-1">Nitrogen (N) Optimal Range</p>
              <div className="grid grid-cols-2 gap-2">
                <input type="number" value={npkRanges.nitrogenOptimalMin} onChange={e => setNpkRanges({...npkRanges, nitrogenOptimalMin: parseFloat(e.target.value)})} className="border rounded-lg p-2" placeholder="Min ppm" />
                <input type="number" value={npkRanges.nitrogenOptimalMax} onChange={e => setNpkRanges({...npkRanges, nitrogenOptimalMax: parseFloat(e.target.value)})} className="border rounded-lg p-2" placeholder="Max ppm" />
              </div>
            </div>

            <div>
              <p className="font-semibold text-gray-700 mb-1">Phosphorus (P) Optimal Range</p>
              <div className="grid grid-cols-2 gap-2">
                <input type="number" value={npkRanges.phosphorusOptimalMin} onChange={e => setNpkRanges({...npkRanges, phosphorusOptimalMin: parseFloat(e.target.value)})} className="border rounded-lg p-2" placeholder="Min ppm" />
                <input type="number" value={npkRanges.phosphorusOptimalMax} onChange={e => setNpkRanges({...npkRanges, phosphorusOptimalMax: parseFloat(e.target.value)})} className="border rounded-lg p-2" placeholder="Max ppm" />
              </div>
            </div>

            <div>
              <p className="font-semibold text-gray-700 mb-1">Potassium (K) Optimal Range</p>
              <div className="grid grid-cols-2 gap-2">
                <input type="number" value={npkRanges.potassiumOptimalMin} onChange={e => setNpkRanges({...npkRanges, potassiumOptimalMin: parseFloat(e.target.value)})} className="border rounded-lg p-2" placeholder="Min ppm" />
                <input type="number" value={npkRanges.potassiumOptimalMax} onChange={e => setNpkRanges({...npkRanges, potassiumOptimalMax: parseFloat(e.target.value)})} className="border rounded-lg p-2" placeholder="Max ppm" />
              </div>
            </div>
          </div>
        </div>

        {/* HIPO: Manage pH Ranges */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-base">
            <Sliders size={20} className="text-purple-700" /> Soil pH Ranges
          </h3>

          <div className="space-y-3 text-xs">
            <div>
              <label className="block font-semibold text-gray-700 mb-1">Acidic Threshold (&lt; pH)</label>
              <input type="number" step="0.1" value={phRanges.acidicThreshold} onChange={e => setPhRanges({...phRanges, acidicThreshold: parseFloat(e.target.value)})} className="w-full border rounded-lg p-2" />
            </div>

            <div>
              <label className="block font-semibold text-gray-700 mb-1">Optimal pH Min - Max</label>
              <div className="grid grid-cols-2 gap-2">
                <input type="number" step="0.1" value={phRanges.optimalPhMin} onChange={e => setPhRanges({...phRanges, optimalPhMin: parseFloat(e.target.value)})} className="border rounded-lg p-2" />
                <input type="number" step="0.1" value={phRanges.optimalPhMax} onChange={e => setPhRanges({...phRanges, optimalPhMax: parseFloat(e.target.value)})} className="border rounded-lg p-2" />
              </div>
            </div>

            <div>
              <label className="block font-semibold text-gray-700 mb-1">Alkaline Threshold (&gt; pH)</label>
              <input type="number" step="0.1" value={phRanges.alkalineThreshold} onChange={e => setPhRanges({...phRanges, alkalineThreshold: parseFloat(e.target.value)})} className="w-full border rounded-lg p-2" />
            </div>
          </div>
        </div>

        {/* HIPO: Manage Soil Moisture Ranges */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-base">
            <Sliders size={20} className="text-blue-700" /> Soil Moisture Ranges (%)
          </h3>

          <div className="space-y-3 text-xs">
            <div>
              <label className="block font-semibold text-gray-700 mb-1">Dry Alert Threshold (&lt; %)</label>
              <input type="number" value={moistureRanges.dryThreshold} onChange={e => setMoistureRanges({...moistureRanges, dryThreshold: parseFloat(e.target.value)})} className="w-full border rounded-lg p-2" />
            </div>

            <div>
              <label className="block font-semibold text-gray-700 mb-1">Optimal Moisture Min - Max (%)</label>
              <div className="grid grid-cols-2 gap-2">
                <input type="number" value={moistureRanges.optimalMoistureMin} onChange={e => setMoistureRanges({...moistureRanges, optimalMoistureMin: parseFloat(e.target.value)})} className="border rounded-lg p-2" />
                <input type="number" value={moistureRanges.optimalMoistureMax} onChange={e => setMoistureRanges({...moistureRanges, optimalMoistureMax: parseFloat(e.target.value)})} className="border rounded-lg p-2" />
              </div>
            </div>

            <div>
              <label className="block font-semibold text-gray-700 mb-1">Waterlogged Threshold (&gt; %)</label>
              <input type="number" value={moistureRanges.wetThreshold} onChange={e => setMoistureRanges({...moistureRanges, wetThreshold: parseFloat(e.target.value)})} className="w-full border rounded-lg p-2" />
            </div>
          </div>
        </div>
      </div>

      {/* 4. Set Optimal Soil Conditions (HIPO: Set Optimal Soil Conditions) */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
        <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-lg">
          <CheckCircle2 size={20} className="text-emerald-700" /> Set Global Optimal Soil Conditions for QC Urban Gardening
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 text-xs">
          <div className="p-4 bg-green-50/60 rounded-xl border border-green-100 space-y-1">
            <p className="font-bold text-green-900">Target Soil Typology</p>
            <p className="text-gray-700">{optimalGlobal.targetSoilType}</p>
          </div>
          <div className="p-4 bg-green-50/60 rounded-xl border border-green-100 space-y-1">
            <p className="font-bold text-green-900">Organic Matter Content</p>
            <p className="text-gray-700">{optimalGlobal.organicMatterTarget}</p>
          </div>
          <div className="p-4 bg-green-50/60 rounded-xl border border-green-100 space-y-1">
            <p className="font-bold text-green-900">Electrical Conductivity (EC)</p>
            <p className="text-gray-700">{optimalGlobal.ecTarget}</p>
          </div>
          <div className="p-4 bg-green-50/60 rounded-xl border border-green-100 space-y-1">
            <p className="font-bold text-green-900">Root Zone Temperature</p>
            <p className="text-gray-700">{optimalGlobal.soilTempTarget}</p>
          </div>
        </div>
      </div>

      {/* 5 & 6. Soil Deficiency & Excess Rules (HIPO: Manage Soil Deficiency Rules & Manage Soil Excess Rules) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Soil Deficiency Rules */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-lg">
            <AlertTriangle size={20} className="text-amber-600" /> Soil Deficiency Remediation Rules
          </h3>

          <div className="space-y-3">
            {deficiencyRules.map(rule => (
              <div key={rule.id} className="p-4 rounded-xl border border-amber-100 bg-amber-50/40 space-y-2">
                <div className="flex justify-between items-center text-xs">
                  <span className="font-bold text-amber-900">{rule.param}</span>
                  <span className="px-2 py-0.5 bg-amber-200 text-amber-900 rounded font-bold">{rule.trigger}</span>
                </div>
                <p className="text-xs text-gray-700"><span className="font-semibold text-gray-900">Visual Symptom:</span> {rule.symptom}</p>
                <p className="text-xs text-green-800"><span className="font-semibold text-green-900">🛠️ Remediation:</span> {rule.remediation}</p>
              </div>
            ))}
          </div>
        </div>

        {/* Soil Excess Rules */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-lg">
            <ShieldAlert size={20} className="text-red-600" /> Soil Excess & Toxicity Remediation Rules
          </h3>

          <div className="space-y-3">
            {excessRules.map(rule => (
              <div key={rule.id} className="p-4 rounded-xl border border-red-100 bg-red-50/40 space-y-2">
                <div className="flex justify-between items-center text-xs">
                  <span className="font-bold text-red-900">{rule.param}</span>
                  <span className="px-2 py-0.5 bg-red-200 text-red-900 rounded font-bold">{rule.trigger}</span>
                </div>
                <p className="text-xs text-gray-700"><span className="font-semibold text-gray-900">Visual Symptom:</span> {rule.symptom}</p>
                <p className="text-xs text-blue-800"><span className="font-semibold text-blue-900">💧 Remediation:</span> {rule.remediation}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
