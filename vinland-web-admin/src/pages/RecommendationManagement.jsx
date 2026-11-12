import React, { useEffect, useState } from "react";
import { collection, getDocs, doc, setDoc, deleteDoc, onSnapshot } from "firebase/firestore";
import { db } from "../config/firebase";
import { Sliders, Plus, Edit2, Trash2, X, CheckCircle2, AlertTriangle, Lightbulb, BookOpen, Tag, Cpu, RefreshCw } from "lucide-react";
import { notify, confirmAction } from "../components/common/Notifier";

const PARAMS = [
  { key: "ph", label: "Soil pH", unit: " pH", min: "phMin", max: "phMax",
    low: ["Too Acidic", (c, v) => `Soil pH (${v}) is below ${c.name}'s minimum (${c.phMin}). Apply agricultural lime or wood ash to raise pH.`],
    high: ["Too Alkaline", (c, v) => `Soil pH (${v}) is above ${c.name}'s maximum (${c.phMax}). Mix in compost or peat moss to lower pH.`] },
  { key: "moisture", label: "Soil Moisture", unit: "%", min: "moistureMin", max: "moistureMax",
    low: ["Too Dry", (c, v) => `Moisture (${v}%) is below ${c.name}'s need (${c.moistureMin}%). Water more often and mulch the soil.`],
    high: ["Waterlogged", (c, v) => `Moisture (${v}%) is above ${c.moistureMax}%. Water less and improve drainage to avoid root rot.`] },
  { key: "nitrogen", label: "Nitrogen (N)", unit: " ppm", min: "nitrogenMin", max: "nitrogenMax",
    low: ["Deficient", (c, v) => `Nitrogen (${v} ppm) is low for ${c.name}. Add vermicompost or fermented plant juice (FPJ).`],
    high: ["Excessive", (c, v) => `Nitrogen (${v} ppm) is high for ${c.name}. Hold off on nitrogen fertilizer.`] },
  { key: "phosphorus", label: "Phosphorus (P)", unit: " ppm", min: "phosphorusMin", max: "phosphorusMax",
    low: ["Deficient", (c, v) => `Phosphorus (${v} ppm) is low for ${c.name}. Add bone meal or fermented fruit juice (FFJ).`],
    high: ["Excessive", (c, v) => `Phosphorus (${v} ppm) is high for ${c.name}. Skip phosphorus fertilizer until it drops.`] },
  { key: "potassium", label: "Potassium (K)", unit: " ppm", min: "potassiumMin", max: "potassiumMax",
    low: ["Deficient", (c, v) => `Potassium (${v} ppm) is low for ${c.name}. Add banana peel tea or wood ash.`],
    high: ["Excessive", (c, v) => `Potassium (${v} ppm) is high for ${c.name}. Skip potassium fertilizer until it drops.`] },
];

const PARAMETER_OPTIONS = PARAMS.map(p => p.label);
const SEVERITY_OPTIONS = ["Info", "Caution", "Warning", "Critical"];
// Same formats the Android app understands: "< 5.5", "<= 30", "> 7", ">= 80", "5.5 - 6.5"
const CONDITION_PATTERN = /^\s*((<=|>=|<|>|==?)\s*-?\d+(\.\d+)?|-?\d+(\.\d+)?\s*(-|–|to)\s*-?\d+(\.\d+)?)\s*%?\s*(ppm)?\s*$/i;

function toNumber(v) {
  const n = typeof v === "number" ? v : parseFloat(v);
  return Number.isFinite(n) ? n : null;
}

function analyze(crop, sensor) {
  return PARAMS.map((p) => {
    const value = toNumber(sensor?.[p.key]);
    const min = toNumber(crop[p.min]);
    const max = toNumber(crop[p.max]);
    const target = min != null && max != null ? `${min} - ${max}${p.unit}` : "not set";
    if (value == null) {
      return { param: p.label, value: "No reading", target, status: "No Data", advice: "The sensor has not sent this value yet.", severity: "None" };
    }
    const shown = `${value}${p.unit}`;
    if (min == null || max == null) {
      return { param: p.label, value: shown, target, status: "No Range", advice: `Set a ${p.label} range for ${crop.name} in Crop Profiles.`, severity: "None" };
    }
    if (value < min) return { param: p.label, value: shown, target, status: p.low[0], advice: p.low[1](crop, value), severity: "Warning" };
    if (value > max) return { param: p.label, value: shown, target, status: p.high[0], advice: p.high[1](crop, value), severity: "Warning" };
    return { param: p.label, value: shown, target, status: "Optimal", advice: `${p.label} is within ${crop.name}'s ideal range.`, severity: "Optimal" };
  });
}

export default function RecommendationManagement() {
  const [recommendations, setRecommendations] = useState([]);
  const [crops, setCrops] = useState([]);
  const [devices, setDevices] = useState([]);
  const [deviceId, setDeviceId] = useState("VINLAND-ESP32-01");
  const [liveSensor, setLiveSensor] = useState(null);
  const [sensorMissing, setSensorMissing] = useState(false);
  const [selectedCrop, setSelectedCrop] = useState(null);
  const [loading, setLoading] = useState(true);

  // Modals
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState(null);

  const emptyRule = {
    title: "",
    parameter: "Soil pH",
    condition: "< 5.5",
    status: "Too Acidic",
    advice: "",
    severity: "Warning",
    applicableCrops: [],
    category: "Treatment Advice"
  };

  const [formData, setFormData] = useState(emptyRule);

  useEffect(() => {
    fetchInitialData();
  }, []);

  // Listen to the chosen sensor device's latest readings
  useEffect(() => {
    if (!deviceId) return undefined;
    const unsub = onSnapshot(
      doc(db, "sensor_devices", deviceId),
      (snap) => {
        setLiveSensor(snap.exists() ? snap.data() : null);
        setSensorMissing(!snap.exists());
      },
      () => {
        setLiveSensor(null);
        setSensorMissing(true);
      }
    );
    return () => unsub();
  }, [deviceId]);

  async function fetchInitialData() {
    setLoading(true);
    try {
      const [cropsSnap, recsSnap, devicesSnap] = await Promise.all([
        getDocs(collection(db, "crops")),
        getDocs(collection(db, "recommendations")),
        getDocs(collection(db, "sensor_devices")),
      ]);
      const cropList = cropsSnap.docs
        .map(d => ({ id: d.id, ...d.data() }))
        .sort((a, b) => (a.name || "").localeCompare(b.name || ""));
      setCrops(cropList);
      setSelectedCrop(prev => (prev && cropList.find(c => c.id === prev.id)) || cropList[0] || null);

      setRecommendations(recsSnap.docs.map(d => ({ id: d.id, ...d.data() })));

      const deviceList = devicesSnap.docs.map(d => ({ id: d.id, ...d.data() }));
      setDevices(deviceList);
      if (deviceList.length > 0 && !deviceList.some(d => d.id === deviceId)) {
        setDeviceId(deviceList[0].id);
      }
    } catch (e) {
      console.error("Error loading recommendation data:", e);
      notify("Could not load recommendation data: " + e.message, "error");
    } finally {
      setLoading(false);
    }
  }

  const dynamicAnalysis = selectedCrop ? analyze(selectedCrop, liveSensor) : [];
  const fmt = (v, unit) => (toNumber(v) == null ? "—" : `${toNumber(v)}${unit}`);

  function openAddModal() {
    setEditingRule(null);
    setFormData(emptyRule);
    setIsModalOpen(true);
  }

  function openEditModal(rule) {
    setEditingRule(rule);
    setFormData({ ...emptyRule, ...rule, applicableCrops: Array.isArray(rule.applicableCrops) ? rule.applicableCrops : [] });
    setIsModalOpen(true);
  }

  async function handleSaveRule(e) {
    e.preventDefault();
    if (!CONDITION_PATTERN.test(formData.condition || "")) {
      notify('Trigger condition must look like "< 5.5", ">= 80" or "5.5 - 6.5" so the app can check it.', "error");
      return;
    }
    try {
      const ruleId = editingRule ? editingRule.id : `rec_${Date.now()}`;
      const docRef = doc(db, "recommendations", ruleId);
      await setDoc(docRef, { ...formData, id: ruleId }, { merge: true });
      setIsModalOpen(false);
      notify(editingRule ? "Recommendation rule updated." : "Recommendation rule added.");
      fetchInitialData();
    } catch (e) {
      notify("Error saving recommendation rule: " + e.message, "error");
    }
  }

  async function handleDeleteRule(ruleId) {
    if (await confirmAction("Are you sure you want to delete this recommendation rule?", { confirmLabel: "Delete", danger: true })) {
      try {
        await deleteDoc(doc(db, "recommendations", ruleId));
        setRecommendations(recommendations.filter(r => r.id !== ruleId));
        notify("Recommendation rule deleted.");
      } catch (e) {
        notify("Could not delete recommendation rule: " + e.message, "error");
      }
    }
  }

  return (
    <div className="p-4 sm:p-6 lg:p-8 space-y-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Recommendation Management</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Write soil advice rules for gardeners. The app shows a rule on the Soil Monitoring screen and uses it in AI Recommendations when the gardener's reading meets the rule's condition.
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
              <h3 className="font-bold text-gray-900 text-lg">Live Sensor Readings vs Crop Ranges</h3>
              {sensorMissing || !liveSensor ? (
                <p className="text-xs text-amber-700 font-semibold">
                  No readings yet from {deviceId || "a sensor"}. Add a sensor device in Account Management or wait for it to send data.
                </p>
              ) : (
                <p className="text-xs text-gray-500">
                  <span className="font-bold text-gray-800">
                    {fmt(liveSensor.ph, " pH")} · {fmt(liveSensor.moisture, "% moisture")} · N {fmt(liveSensor.nitrogen, " ppm")} · P {fmt(liveSensor.phosphorus, " ppm")} · K {fmt(liveSensor.potassium, " ppm")}
                  </span>
                </p>
              )}
            </div>
          </div>

          <div className="flex flex-col sm:flex-row gap-3">
          {/* Sensor Selector */}
          {devices.length > 0 && (
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold text-gray-700">Sensor:</span>
              <select
                value={deviceId}
                onChange={(e) => setDeviceId(e.target.value)}
                className="border border-gray-300 rounded-xl px-3 py-2 text-sm font-semibold text-gray-800 focus:ring-2 focus:ring-green-500"
              >
                {devices.map((d) => (
                  <option key={d.id} value={d.id}>{d.name ? `${d.name} (${d.id})` : d.id}</option>
                ))}
              </select>
            </div>
          )}

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
        </div>

        {/* Dynamic Analysis Cards */}
        {selectedCrop && (
          <div className="space-y-3">
            <h4 className="font-bold text-sm text-gray-900 flex items-center gap-2">
              <span>{selectedCrop.emoji || "🌱"}</span> Soil check for <span className="text-green-800">{selectedCrop.name}</span>
            </h4>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {dynamicAnalysis.map((item, idx) => (
                <div
                  key={idx}
                  className={`p-4 rounded-2xl border space-y-2 text-xs ${
                    item.severity === "Optimal"
                      ? "bg-green-50/60 border-green-200 text-green-900"
                      : item.severity === "None"
                        ? "bg-gray-50 border-gray-200 text-gray-700"
                        : "bg-amber-50/60 border-amber-200 text-amber-900"
                  }`}
                >
                  <div className="flex justify-between items-center font-bold">
                    <span>{item.param}</span>
                    <span className={`px-2 py-0.5 rounded text-[10px] uppercase ${
                      item.severity === "Optimal" ? "bg-green-200 text-green-900" : item.severity === "None" ? "bg-gray-200 text-gray-700" : "bg-amber-200 text-amber-900"
                    }`}>
                      {item.status}
                    </span>
                  </div>

                  <p className="text-gray-700">
                    Reading: <span className="font-bold">{item.value}</span> | Target: <span className="font-bold">{item.target}</span>
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
          <div className="p-4 sm:p-6 lg:p-8 text-center text-gray-500 font-medium">Loading rules from Firestore...</div>
        ) : recommendations.length === 0 ? (
          <p className="text-xs text-gray-400 py-6 text-center">No custom recommendation rules created in database yet.</p>
        ) : (
          <div className="space-y-4">
            {recommendations.map((rule) => (
              <div key={rule.id} className="p-5 rounded-2xl border border-gray-200 bg-gray-50/50 hover:bg-white hover:shadow-md transition-all space-y-3">
                <div className="flex justify-between items-start">
                  <div className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-bold text-base text-gray-900">{rule.title || rule.parameter}</span>
                      <span className="px-2.5 py-0.5 bg-amber-100 text-amber-800 rounded-full text-xs font-semibold">
                        {rule.parameter} ({rule.condition})
                      </span>
                      <span className="px-2.5 py-0.5 bg-green-100 text-green-800 rounded-full text-xs font-semibold">
                        {rule.status}
                      </span>
                      {rule.severity && (
                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                          rule.severity === "Critical" ? "bg-red-100 text-red-800"
                          : rule.severity === "Warning" ? "bg-orange-100 text-orange-800"
                          : rule.severity === "Caution" ? "bg-yellow-100 text-yellow-800"
                          : "bg-blue-100 text-blue-800"
                        }`}>
                          {rule.severity}
                        </span>
                      )}
                      {!CONDITION_PATTERN.test(rule.condition || "") && (
                        <span className="px-2.5 py-0.5 bg-red-100 text-red-800 rounded-full text-xs font-semibold" title='The app can only read conditions like "< 5.5" or "5.5 - 6.5". Edit this rule.'>
                          Condition not readable by app
                        </span>
                      )}
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
                      <span className="text-gray-400 italic">All crops</span>
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

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Target Parameter</label>
                  <select value={formData.parameter} onChange={e => setFormData({...formData, parameter: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm">
                    {PARAMETER_OPTIONS.map(o => <option key={o} value={o}>{o}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Trigger Condition</label>
                  <input required type="text" value={formData.condition} onChange={e => setFormData({...formData, condition: e.target.value})} className={`w-full border rounded-xl px-3 py-2 text-sm ${CONDITION_PATTERN.test(formData.condition || "") ? "" : "border-red-400"}`} placeholder="< 5.5" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Status Label</label>
                  <input type="text" value={formData.status} onChange={e => setFormData({...formData, status: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" placeholder="Too Acidic" />
                </div>
              </div>
              <p className="text-xs text-gray-500 -mt-2">
                Condition examples: <code>&lt; 5.5</code>, <code>&gt;= 80</code>, <code>5.5 - 6.5</code>. The rule shows when the gardener's reading matches.
              </p>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Severity</label>
                <select value={formData.severity || "Warning"} onChange={e => setFormData({...formData, severity: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm">
                  {SEVERITY_OPTIONS.map(o => <option key={o} value={o}>{o}</option>)}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  Applies to Crops <span className="font-normal text-gray-500">(leave all unticked for every crop)</span>
                </label>
                <div className="border rounded-xl p-3 max-h-40 overflow-y-auto grid grid-cols-2 sm:grid-cols-3 gap-2">
                  {crops.length === 0 && <span className="text-xs text-gray-400 col-span-full">No crops in Crop Profiles yet.</span>}
                  {crops.map(c => {
                    const list = Array.isArray(formData.applicableCrops) ? formData.applicableCrops : [];
                    const checked = list.some(x => x.toLowerCase() === (c.name || "").toLowerCase());
                    return (
                      <label key={c.id} className="flex items-center gap-2 text-xs text-gray-800 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => setFormData({
                            ...formData,
                            applicableCrops: checked
                              ? list.filter(x => x.toLowerCase() !== (c.name || "").toLowerCase())
                              : [...list, c.name],
                          })}
                          className="accent-green-700"
                        />
                        {c.emoji || "🌱"} {c.name}
                      </label>
                    );
                  })}
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
