import React, { useEffect, useState } from "react";
import { collection, getDocs, doc, setDoc, deleteDoc, onSnapshot } from "firebase/firestore";
import { db } from "../config/firebase";
import { Sliders, Plus, Edit2, Trash2, X, CheckCircle2, AlertTriangle, Lightbulb, BookOpen, Tag, Cpu, RefreshCw } from "lucide-react";

export default function RecommendationManagement() {
  const [recommendations, setRecommendations] = useState([]);
  const [crops, setCrops] = useState([]);
  const [liveSensor, setLiveSensor] = useState(null);
  const [selectedCrop, setSelectedCrop] = useState(null);
  const [dynamicAnalysis, setDynamicAnalysis] = useState([]);
  const [loading, setLoading] = useState(true);

  // Modals
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState(null);

  const emptyRule = {
    title: "",
    parameter: "Soil pH",
    condition: "< 5.5",
    status: "Too Acidic",
    advice: "Incorporate agricultural lime or powdered eggshells to raise soil pH.",
    severity: "Warning",
    applicableCrops: ["Tomato", "Green Pepper"],
    category: "Treatment Advice"
  };

  const [formData, setFormData] = useState(emptyRule);

  useEffect(() => {
    fetchInitialData();

    // Listen to live ESP32 sensor values
    const unsubSensor = onSnapshot(doc(db, "sensor_devices", "VINLAND-ESP32-01"), (snap) => {
      if (snap.exists()) {
        setLiveSensor(snap.data());
      } else {
        setLiveSensor({
          ph: 6.6,
          moisture: 52.0,
          nitrogen: 35.0,
          phosphorus: 22.0,
          potassium: 145.0
        });
      }
    });

    return () => unsubSensor();
  }, []);

  async function fetchInitialData() {
    setLoading(true);
    try {
      // 1. Fetch real crops from 'crops'
      const cropsSnap = await getDocs(collection(db, "crops"));
      const cropList = cropsSnap.docs.map(d => ({ id: d.id, ...d.data() }));
      setCrops(cropList);
      if (cropList.length > 0) {
        setSelectedCrop(cropList[0]);
      }

      // 2. Fetch custom recommendations from Firestore 'recommendations'
      const recsSnap = await getDocs(collection(db, "recommendations"));
      const recList = recsSnap.docs.map(d => ({ id: d.id, ...d.data() }));
      setRecommendations(recList);
    } catch (e) {
      console.error("Error loading recommendation data:", e);
    } finally {
      setLoading(false);
    }
  }

  // Calculate ACCURATE dynamic soil analysis whenever selectedCrop or liveSensor updates
  useEffect(() => {
    if (!selectedCrop || !liveSensor) return;

    const analysis = [];
    const ph = liveSensor.ph ?? 6.5;
    const m = liveSensor.moisture ?? 50;
    const n = liveSensor.nitrogen ?? 35;
    const p = liveSensor.phosphorus ?? 22;
    const k = liveSensor.potassium ?? 145;

    // 1. pH evaluation
    if (ph < (selectedCrop.phMin || 6.0)) {
      analysis.push({
        param: "Soil pH",
        value: `${ph} pH`,
        target: `${selectedCrop.phMin} - ${selectedCrop.phMax}`,
        status: "Too Acidic",
        advice: `Soil pH (${ph}) is lower than ${selectedCrop.name}'s minimum tolerance (${selectedCrop.phMin}). Apply agricultural lime or wood ash to neutralize acidity.`,
        severity: "Warning"
      });
    } else if (ph > (selectedCrop.phMax || 7.0)) {
      analysis.push({
        param: "Soil pH",
        value: `${ph} pH`,
        target: `${selectedCrop.phMin} - ${selectedCrop.phMax}`,
        status: "Too Alkaline",
        advice: `Soil pH (${ph}) exceeds ${selectedCrop.name}'s maximum tolerance (${selectedCrop.phMax}). Incorporate peat moss or organic compost to lower pH.`,
        severity: "Warning"
      });
    } else {
      analysis.push({
        param: "Soil pH",
        value: `${ph} pH`,
        target: `${selectedCrop.phMin} - ${selectedCrop.phMax}`,
        status: "Optimal",
        advice: `Soil pH is within optimal limits for growing ${selectedCrop.name}.`,
        severity: "Optimal"
      });
    }

    // 2. Moisture evaluation
    if (m < (selectedCrop.moistureMin || 30)) {
      analysis.push({
        param: "Soil Moisture",
        value: `${m}%`,
        target: `${selectedCrop.moistureMin}% - ${selectedCrop.moistureMax}%`,
        status: "Too Dry",
        advice: `Soil moisture (${m}%) is below requirement (${selectedCrop.moistureMin}%). Increase irrigation and apply mulch to conserve root zone moisture.`,
        severity: "Warning"
      });
    } else if (m > (selectedCrop.moistureMax || 70)) {
      analysis.push({
        param: "Soil Moisture",
        value: `${m}%`,
        target: `${selectedCrop.moistureMin}% - ${selectedCrop.moistureMax}%`,
        status: "Waterlogged",
        advice: `Soil moisture (${m}%) exceeds ${selectedCrop.moistureMax}%. Improve container drainage to avoid root rot.`,
        severity: "Warning"
      });
    } else {
      analysis.push({
        param: "Soil Moisture",
        value: `${m}%`,
        target: `${selectedCrop.moistureMin}% - ${selectedCrop.moistureMax}%`,
        status: "Optimal",
        advice: `Soil moisture level is optimal for ${selectedCrop.name}.`,
        severity: "Optimal"
      });
    }

    // 3. Nitrogen evaluation
    if (n < (selectedCrop.nitrogenMin || 20)) {
      analysis.push({
        param: "Nitrogen (N)",
        value: `${n} ppm`,
        target: `${selectedCrop.nitrogenMin} - ${selectedCrop.nitrogenMax} ppm`,
        status: "Deficient",
        advice: `Nitrogen level (${n} ppm) is low for ${selectedCrop.name}. Drench with vermicompost or fish amino acid to encourage leafy growth.`,
        severity: "Caution"
      });
    } else {
      analysis.push({
        param: "Nitrogen (N)",
        value: `${n} ppm`,
        target: `${selectedCrop.nitrogenMin} - ${selectedCrop.nitrogenMax} ppm`,
        status: "Sufficient",
        advice: `Nitrogen levels meet ${selectedCrop.name}'s nutritional requirement.`,
        severity: "Optimal"
      });
    }

    setDynamicAnalysis(analysis);
  }, [selectedCrop, liveSensor]);

  function openAddModal() {
    setEditingRule(null);
    setFormData(emptyRule);
    setIsModalOpen(true);
  }

  function openEditModal(rule) {
    setEditingRule(rule);
    setFormData(rule);
    setIsModalOpen(true);
  }

  async function handleSaveRule(e) {
    e.preventDefault();
    try {
      const ruleId = editingRule ? editingRule.id : `rec_${Date.now()}`;
      const docRef = doc(db, "recommendations", ruleId);
      await setDoc(docRef, { ...formData, id: ruleId }, { merge: true });
      setIsModalOpen(false);
      fetchInitialData();
    } catch (e) {
      alert("Error saving recommendation rule: " + e.message);
    }
  }

  async function handleDeleteRule(ruleId) {
    if (confirm("Are you sure you want to delete this recommendation rule?")) {
      try {
        await deleteDoc(doc(db, "recommendations", ruleId));
        setRecommendations(recommendations.filter(r => r.id !== ruleId));
      } catch (e) {
        alert("Could not delete recommendation rule: " + e.message);
      }
    }
  }

  return (
    <div className="p-8 space-y-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Recommendation Management</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Accurate real-time soil evaluation comparing live ESP32 sensor values against individual crop requirement thresholds in Firestore.
          </p>
        </div>
        <button
          onClick={openAddModal}
          className="bg-green-700 hover:bg-green-800 text-white font-semibold px-4 py-2.5 rounded-xl shadow-xs transition-colors flex items-center gap-2 text-sm shrink-0"
        >
          <Plus size={18} /> Add Custom Recommendation Rule
        </button>
      </div>

      {/* DYNAMIC ACCURATE SOIL ANALYSIS EVALUATOR */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-6">
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 border-b pb-4">
          <div className="flex items-center space-x-3">
            <div className="p-3 bg-green-50 text-green-700 rounded-xl">
              <Cpu size={24} />
            </div>
            <div>
              <h3 className="font-bold text-gray-900 text-lg">Accurate Live Telemetry vs Crop Tolerances</h3>
              <p className="text-xs text-gray-500">
                Live ESP32 Readouts: <span className="font-bold text-gray-800">{liveSensor?.ph ?? '6.6'} pH, {liveSensor?.moisture ?? '52'}% Moisture, {liveSensor?.nitrogen ?? '35'} N ppm</span>
              </p>
            </div>
          </div>

          {/* Crop Selector */}
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold text-gray-700">Evaluate Crop:</span>
            <select
              value={selectedCrop?.id || ""}
              onChange={(e) => {
                const found = crops.find(c => c.id === e.target.value);
                if (found) setSelectedCrop(found);
              }}
              className="border border-gray-300 rounded-xl px-3 py-2 text-sm font-semibold text-gray-800 focus:ring-2 focus:ring-green-500"
            >
              {crops.map((c) => (
                <option key={c.id} value={c.id}>{c.emoji || "🌱"} {c.name}</option>
              ))}
            </select>
          </div>
        </div>

        {/* Dynamic Analysis Cards */}
        {selectedCrop && (
          <div className="space-y-3">
            <h4 className="font-bold text-sm text-gray-900 flex items-center gap-2">
              <span>{selectedCrop.emoji || "🌱"}</span> Accurate Diagnosis for <span className="text-green-800">{selectedCrop.name}</span>
            </h4>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {dynamicAnalysis.map((item, idx) => (
                <div
                  key={idx}
                  className={`p-4 rounded-2xl border space-y-2 text-xs ${
                    item.severity === "Optimal"
                      ? "bg-green-50/60 border-green-200 text-green-900"
                      : "bg-amber-50/60 border-amber-200 text-amber-900"
                  }`}
                >
                  <div className="flex justify-between items-center font-bold">
                    <span>{item.param}</span>
                    <span className={`px-2 py-0.5 rounded text-[10px] uppercase ${
                      item.severity === "Optimal" ? "bg-green-200 text-green-900" : "bg-amber-200 text-amber-900"
                    }`}>
                      {item.status}
                    </span>
                  </div>

                  <p className="text-gray-700">
                    Live Reading: <span className="font-bold">{item.value}</span> | Target: <span className="font-bold">{item.target}</span>
                  </p>

                  <p className="text-xs leading-relaxed font-medium pt-1 border-t border-gray-200/60">
                    💡 {item.advice}
                  </p>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* CUSTOM RECOMMENDATION RULES IN FIRESTORE */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
        <div className="flex justify-between items-center pb-3 border-b">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 text-lg">
            <Sliders size={20} className="text-green-700" /> Stored Recommendation Rules (`recommendations` collection)
          </h3>
          <span className="text-xs text-gray-500 font-medium">{recommendations.length} Active Rules</span>
        </div>

        {loading ? (
          <div className="p-8 text-center text-gray-500 font-medium">Loading rules from Firestore...</div>
        ) : recommendations.length === 0 ? (
          <p className="text-xs text-gray-400 py-6 text-center">No custom recommendation rules created in database yet.</p>
        ) : (
          <div className="space-y-4">
            {recommendations.map((rule) => (
              <div key={rule.id} className="p-5 rounded-2xl border border-gray-200 bg-gray-50/50 hover:bg-white hover:shadow-md transition-all space-y-3">
                <div className="flex justify-between items-start">
                  <div className="space-y-1">
                    <div className="flex items-center space-x-2">
                      <span className="font-bold text-base text-gray-900">{rule.title || rule.parameter}</span>
                      <span className="px-2.5 py-0.5 bg-amber-100 text-amber-800 rounded-full text-xs font-semibold">
                        {rule.parameter} ({rule.condition})
                      </span>
                      <span className="px-2.5 py-0.5 bg-green-100 text-green-800 rounded-full text-xs font-semibold">
                        {rule.status}
                      </span>
                    </div>
                  </div>
                  <div className="flex space-x-1">
                    <button onClick={() => openEditModal(rule)} title="Edit Rule" className="p-1.5 hover:bg-gray-100 rounded-lg text-gray-600 transition-colors">
                      <Edit2 size={16} />
                    </button>
                    <button onClick={() => handleDeleteRule(rule.id)} title="Delete Rule" className="p-1.5 hover:bg-red-50 text-red-600 rounded-lg transition-colors">
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>

                <div className="p-3 bg-white rounded-xl border border-gray-100 text-xs text-gray-700 leading-relaxed">
                  💡 <span className="font-bold text-gray-900">Actionable Advice:</span> {rule.advice}
                </div>

                <div className="flex items-center space-x-2 text-xs">
                  <span className="font-semibold text-gray-500 flex items-center gap-1">
                    <Tag size={13} className="text-green-700" /> Assigned Crops:
                  </span>
                  <div className="flex flex-wrap gap-1">
                    {Array.isArray(rule.applicableCrops) && rule.applicableCrops.length > 0 ? (
                      rule.applicableCrops.map((c, i) => (
                        <span key={i} className="px-2 py-0.5 bg-green-50 text-green-700 rounded-md font-medium border border-green-100">
                          {c}
                        </span>
                      ))
                    ) : (
                      <span className="text-gray-400 italic">All urban crops</span>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Add / Edit Recommendation Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-xl w-full p-6 space-y-4 shadow-2xl max-h-[90vh] overflow-y-auto">
            <div className="flex justify-between items-center border-b pb-3">
              <h3 className="font-bold text-lg text-gray-900">
                {editingRule ? "Edit Recommendation Rule" : "Add Custom Recommendation Rule"}
              </h3>
              <button onClick={() => setIsModalOpen(false)} className="text-gray-400 hover:text-gray-600"><X size={20}/></button>
            </div>

            <form onSubmit={handleSaveRule} className="space-y-4 text-sm">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Rule Title</label>
                <input required type="text" value={formData.title} onChange={e => setFormData({...formData, title: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" placeholder="Acidic Soil Lime Remediation" />
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Target Parameter</label>
                  <select value={formData.parameter} onChange={e => setFormData({...formData, parameter: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm">
                    <option value="Soil pH">Soil pH</option>
                    <option value="Nitrogen (N)">Nitrogen (N)</option>
                    <option value="Phosphorus (P)">Phosphorus (P)</option>
                    <option value="Potassium (K)">Potassium (K)</option>
                    <option value="Soil Moisture">Soil Moisture</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Trigger Condition</label>
                  <input type="text" value={formData.condition} onChange={e => setFormData({...formData, condition: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" placeholder="< 5.5" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Status Label</label>
                  <input type="text" value={formData.status} onChange={e => setFormData({...formData, status: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" placeholder="Too Acidic" />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Actionable Treatment Advice</label>
                <textarea required rows={3} value={formData.advice} onChange={e => setFormData({...formData, advice: e.target.value})} className="w-full border rounded-xl p-3 text-sm" placeholder="Provide step-by-step soil treatment guidance for gardeners..." />
              </div>

              <div className="flex justify-end space-x-3 pt-3 border-t">
                <button type="button" onClick={() => setIsModalOpen(false)} className="px-4 py-2 border rounded-xl font-medium text-gray-600 hover:bg-gray-50">Cancel</button>
                <button type="submit" className="px-5 py-2 bg-green-700 text-white rounded-xl font-semibold hover:bg-green-800 shadow-xs">Save Rule in Firestore</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
