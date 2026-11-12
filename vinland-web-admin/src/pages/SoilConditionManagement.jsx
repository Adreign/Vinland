import React, { useEffect, useState } from "react";
import { collection, getDocs, doc, writeBatch } from "firebase/firestore";
import { db } from "../config/firebase";
import { Layers, Sliders, AlertTriangle, ShieldAlert, CheckCircle2, Save, RefreshCw, Info, RotateCcw } from "lucide-react";
import { notify, confirmAction } from "../components/common/Notifier";

// The Android app's Soil Monitoring screen reads these documents from
// soil_condition_config and labels each reading Low / OK / High.
// Defaults match the limits the app used before they could be changed here.
const DEFAULT_NPK = {
  nitrogenOptimalMin: 5, nitrogenOptimalMax: 80,
  phosphorusOptimalMin: 5, phosphorusOptimalMax: 60,
  potassiumOptimalMin: 15, potassiumOptimalMax: 150,
};
const DEFAULT_PH = { optimalPhMin: 5.5, optimalPhMax: 7.5 };
const DEFAULT_MOISTURE = { optimalMoistureMin: 35, optimalMoistureMax: 80 };

const RANGE_FIELDS = [
  { group: "npk", label: "Nitrogen (N)", unit: "ppm", min: "nitrogenOptimalMin", max: "nitrogenOptimalMax", step: "1" },
  { group: "npk", label: "Phosphorus (P)", unit: "ppm", min: "phosphorusOptimalMin", max: "phosphorusOptimalMax", step: "1" },
  { group: "npk", label: "Potassium (K)", unit: "ppm", min: "potassiumOptimalMin", max: "potassiumOptimalMax", step: "1" },
  { group: "ph", label: "Soil pH", unit: "", min: "optimalPhMin", max: "optimalPhMax", step: "0.1" },
  { group: "moisture", label: "Soil Moisture", unit: "%", min: "optimalMoistureMin", max: "optimalMoistureMax", step: "1" },
];

// Keep only the range fields, as numbers; fill gaps with defaults.
function pick(data, defaults) {
  const out = { ...defaults };
  Object.keys(defaults).forEach(key => {
    const n = typeof data?.[key] === "number" ? data[key] : parseFloat(data?.[key]);
    if (Number.isFinite(n)) out[key] = n;
  });
  return out;
}

const optimalGlobal = {
  targetSoilType: "Loamy Topsoil with Compost",
  organicMatterTarget: "5% - 8%",
  ecTarget: "1.2 - 2.0 dS/m",
  soilTempTarget: "24°C - 30°C (Quezon City Climate)"
};

// Defined outside the page so inputs keep focus while typing.
function RangeCard({ title, icon, fields, ranges, onChange }) {
  const v = (group, key) => ranges[group][key];
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
      <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-base">
        {icon} {title}
      </h3>
      <div className="space-y-3 text-xs">
        {fields.map(f => (
          <div key={f.min}>
            <p className="font-semibold text-gray-700 mb-1">
              {f.label} Good Range{f.unit ? ` (${f.unit})` : ""}
            </p>
            <div className="grid grid-cols-2 gap-2">
              <input type="number" step={f.step} value={v(f.group, f.min)} onChange={e => onChange(f.group, f.min, e.target.value)} className="border rounded-lg p-2" placeholder="Min" aria-label={`${f.label} minimum`} />
              <input type="number" step={f.step} value={v(f.group, f.max)} onChange={e => onChange(f.group, f.max, e.target.value)} className="border rounded-lg p-2" placeholder="Max" aria-label={`${f.label} maximum`} />
            </div>
            <p className="text-[11px] text-gray-500 mt-1">
              Below {v(f.group, f.min)}{f.unit === "%" ? "%" : ""} = Low · above {v(f.group, f.max)}{f.unit === "%" ? "%" : ""} = High
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}

export default function SoilConditionManagement() {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  // Which of the npk / ph / moisture documents are not in Firestore yet
  const [missingDocs, setMissingDocs] = useState([]);
  const [creating, setCreating] = useState(false);
  // True when the page couldn't read soil_condition_config (e.g. blocked by security rules)
  const [loadFailed, setLoadFailed] = useState(false);

  // Values are kept as typed (strings allowed) and checked on save.
  const [ranges, setRanges] = useState({ npk: DEFAULT_NPK, ph: DEFAULT_PH, moisture: DEFAULT_MOISTURE });

  useEffect(() => {
    fetchSoilConfigurations();
  }, []);

  async function fetchSoilConfigurations() {
    setLoading(true);
    setLoadFailed(false);
    try {
      const snap = await getDocs(collection(db, "soil_condition_config"));
      const found = {};
      snap.forEach(d => { found[d.id] = d.data(); });
      setMissingDocs(["npk", "ph", "moisture"].filter(id => !found[id]));
      setRanges({
        npk: pick(found.npk, DEFAULT_NPK),
        ph: pick(found.ph, DEFAULT_PH),
        moisture: pick(found.moisture, DEFAULT_MOISTURE),
      });
    } catch (e) {
      console.error("Error loading soil configs:", e);
      setLoadFailed(true);
      notify("Could not load saved soil ranges (showing defaults): " + e.message, "error");
    } finally {
      setLoading(false);
    }
  }

  function setValue(group, key, value) {
    setRanges(prev => ({ ...prev, [group]: { ...prev[group], [key]: value } }));
  }

  // Returns an error message, or null if every range is valid.
  function validate() {
    for (const f of RANGE_FIELDS) {
      const min = parseFloat(ranges[f.group][f.min]);
      const max = parseFloat(ranges[f.group][f.max]);
      if (!Number.isFinite(min) || !Number.isFinite(max)) return `${f.label}: enter both a minimum and a maximum.`;
      if (min < 0) return `${f.label}: the minimum can't be negative.`;
      if (min >= max) return `${f.label}: the minimum must be lower than the maximum.`;
      if (f.group === "ph" && max > 14) return "Soil pH: the maximum can't be above 14.";
      if (f.group === "moisture" && max > 100) return "Soil Moisture: the maximum can't be above 100%.";
    }
    return null;
  }

  async function handleSaveGlobalConfig() {
    const error = validate();
    if (error) {
      notify(error, "error");
      return;
    }
    setSaving(true);
    try {
      const clean = {
        npk: pick(ranges.npk, DEFAULT_NPK),
        ph: pick(ranges.ph, DEFAULT_PH),
        moisture: pick(ranges.moisture, DEFAULT_MOISTURE),
      };
      const batch = writeBatch(db);
      batch.set(doc(db, "soil_condition_config", "npk"), clean.npk);
      batch.set(doc(db, "soil_condition_config", "ph"), clean.ph);
      batch.set(doc(db, "soil_condition_config", "moisture"), clean.moisture);
      await batch.commit();
      setRanges(clean);
      setMissingDocs([]);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
    } catch (e) {
      notify("Failed to save soil condition parameters: " + e.message, "error");
    } finally {
      setSaving(false);
    }
  }

  // Writes the whole soil_condition_config collection (npk, ph, moisture) with the
  // default ranges. Documents that already exist are left alone, so saved values
  // are never overwritten.
  async function handleCreateCollection() {
    if (!loadFailed && missingDocs.length === 0) {
      notify("Soil settings already exist in Firestore (soil_condition_config). Edit the ranges below and click Save Soil Parameters.");
      return;
    }
    const toCreate = loadFailed ? ["npk", "ph", "moisture"] : missingDocs;
    const ok = await confirmAction(
      `Create the soil_condition_config collection in Firestore with the default ranges (${toCreate.join(", ")})?`,
      { confirmLabel: "Create" }
    );
    if (!ok) return;

    setCreating(true);
    try {
      const defaults = { npk: DEFAULT_NPK, ph: DEFAULT_PH, moisture: DEFAULT_MOISTURE };
      const batch = writeBatch(db);
      toCreate.forEach(id => batch.set(doc(db, "soil_condition_config", id), defaults[id]));
      await batch.commit();
      notify("Soil settings created in Firestore (soil_condition_config) with the default ranges.");
      await fetchSoilConfigurations();
    } catch (e) {
      const blocked = e.code === "permission-denied";
      notify(
        blocked
          ? "Firebase blocked this. Add the soil_condition_config rule in Firebase (Firestore Database → Rules), click Publish, then try again."
          : "Could not create soil settings: " + e.message,
        "error"
      );
    } finally {
      setCreating(false);
    }
  }

  async function handleResetDefaults() {
    if (await confirmAction("Put back the default ranges? Click Save Soil Parameters afterwards to apply them in the app.", { confirmLabel: "Reset" })) {
      setRanges({ npk: DEFAULT_NPK, ph: DEFAULT_PH, moisture: DEFAULT_MOISTURE });
    }
  }

  const v = (group, key) => ranges[group][key];

  const deficiencyRules = [
    { id: "d1", param: "Nitrogen (N) Deficiency", trigger: `< ${v("npk", "nitrogenOptimalMin")} ppm`, symptom: "Yellowing older leaves (chlorosis), stunted growth", remediation: "Apply blood meal, vermicompost, or fermented plant juice." },
    { id: "d2", param: "Phosphorus (P) Deficiency", trigger: `< ${v("npk", "phosphorusOptimalMin")} ppm`, symptom: "Purplish underside of leaves, delayed root development", remediation: "Incorporate bone meal or rock phosphate into root zone." },
    { id: "d3", param: "Potassium (K) Deficiency", trigger: `< ${v("npk", "potassiumOptimalMin")} ppm`, symptom: "Browning leaf margins, weak stems in leafy vegetables", remediation: "Add wood ash, banana peel liquid fertilizer, or sulfate of potash." }
  ];

  const excessRules = [
    { id: "e1", param: "Nitrogen (N) Excess", trigger: `> ${v("npk", "nitrogenOptimalMax")} ppm`, symptom: "Dark green brittle leaves, excessive foliage with poor root mass", remediation: "Flush soil with heavy irrigation and pause nitrogenous fertilizers." },
    { id: "e2", param: "Soil Salinity / Over-Fertilization", trigger: "EC > 2.5 dS/m", symptom: "Leaf tip burn, root damage, wilting despite moist soil", remediation: "Leach potting mix with clean water to drain excess soluble salts." }
  ];

  return (
    <div className="p-4 sm:p-6 lg:p-8 space-y-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Soil Condition Management</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Set the general good ranges for soil moisture, pH and NPK. The mobile app uses them to label gardeners' soil readings as Low, OK or High.
          </p>
        </div>

        <div className="flex flex-wrap gap-2 shrink-0">
          <button
            onClick={handleCreateCollection}
            disabled={creating || loading}
            className="border border-amber-500 text-amber-800 hover:bg-amber-50 disabled:opacity-60 font-semibold px-4 py-2.5 rounded-xl transition-colors flex items-center gap-2 text-sm"
          >
            {creating ? <RefreshCw size={16} className="animate-spin" /> : <Layers size={16} />} Create Soil Settings
          </button>
          <button
            onClick={handleResetDefaults}
            disabled={saving || loading}
            className="border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:opacity-60 font-semibold px-4 py-2.5 rounded-xl transition-colors flex items-center gap-2 text-sm"
          >
            <RotateCcw size={16} /> Reset to Defaults
          </button>
          <button
            onClick={handleSaveGlobalConfig}
            disabled={saving || loading}
            className="bg-green-700 hover:bg-green-800 disabled:opacity-60 text-white font-semibold px-5 py-2.5 rounded-xl shadow-xs transition-colors flex items-center gap-2 text-sm"
          >
            {saving ? <RefreshCw size={18} className="animate-spin" /> : <Save size={18} />}
            <span>Save Soil Parameters</span>
          </button>
        </div>
      </div>

      {saveSuccess && (
        <div className="p-4 bg-green-50 border border-green-200 text-green-800 rounded-xl text-xs font-semibold flex items-center gap-2">
          <CheckCircle2 size={18} className="text-green-600" />
          Soil ranges saved. Gardeners will see them the next time they open Soil Monitoring.
        </div>
      )}

      {!loading && missingDocs.length > 0 && (
        <div className="p-4 bg-amber-50 border border-amber-200 text-amber-900 rounded-xl text-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-start gap-2">
            <AlertTriangle size={18} className="shrink-0 mt-0.5 text-amber-600" />
            <span>
              {missingDocs.length === 3
                ? <>The <b>soil_condition_config</b> collection isn't in your database yet. The app is using the default ranges.</>
                : <>Some soil settings are missing from your database ({missingDocs.join(", ")}). The app is using defaults for those.</>}
            </span>
          </div>
          <button
            onClick={handleCreateCollection}
            disabled={creating}
            className="bg-amber-600 hover:bg-amber-700 disabled:opacity-60 text-white font-semibold px-4 py-2 rounded-xl text-sm flex items-center justify-center gap-2 shrink-0"
          >
            {creating ? <RefreshCw size={16} className="animate-spin" /> : <Layers size={16} />}
            Create Soil Settings
          </button>
        </div>
      )}

      <div className="p-4 bg-blue-50 border border-blue-100 text-blue-900 rounded-xl text-xs flex items-start gap-2">
        <Info size={16} className="shrink-0 mt-0.5" />
        <span>
          These ranges control the general soil results on the app's <b>Soil Monitoring</b> screen (parameter results, summary and soil report).
          Checks for one specific crop use that crop's own ranges from <b>Crop Profiles</b>.
        </span>
      </div>

      {loading ? (
        <div className="p-6 text-center text-gray-500 font-medium">Loading soil ranges...</div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <RangeCard title="NPK Ranges (ppm)" icon={<Layers size={20} className="text-green-700" />} ranges={ranges} onChange={setValue} fields={RANGE_FIELDS.filter(f => f.group === "npk")} />
          <RangeCard title="Soil pH Range" icon={<Sliders size={20} className="text-purple-700" />} ranges={ranges} onChange={setValue} fields={RANGE_FIELDS.filter(f => f.group === "ph")} />
          <RangeCard title="Soil Moisture Range (%)" icon={<Sliders size={20} className="text-blue-700" />} ranges={ranges} onChange={setValue} fields={RANGE_FIELDS.filter(f => f.group === "moisture")} />
        </div>
      )}

      {/* Optimal Soil Conditions (reference only) */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
        <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-lg">
          <CheckCircle2 size={20} className="text-emerald-700" /> Optimal Soil Conditions for QC Urban Gardening
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

      {/* Soil Deficiency & Excess guide (triggers follow the ranges above) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-lg">
            <AlertTriangle size={20} className="text-amber-600" /> Soil Deficiency Guide
          </h3>

          <div className="space-y-3">
            {deficiencyRules.map(rule => (
              <div key={rule.id} className="p-4 rounded-xl border border-amber-100 bg-amber-50/40 space-y-2">
                <div className="flex justify-between items-center text-xs gap-2">
                  <span className="font-bold text-amber-900">{rule.param}</span>
                  <span className="px-2 py-0.5 bg-amber-200 text-amber-900 rounded font-bold whitespace-nowrap">{rule.trigger}</span>
                </div>
                <p className="text-xs text-gray-700"><span className="font-semibold text-gray-900">Visual Symptom:</span> {rule.symptom}</p>
                <p className="text-xs text-green-800"><span className="font-semibold text-green-900">🛠️ Remediation:</span> {rule.remediation}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-lg">
            <ShieldAlert size={20} className="text-red-600" /> Soil Excess & Toxicity Guide
          </h3>

          <div className="space-y-3">
            {excessRules.map(rule => (
              <div key={rule.id} className="p-4 rounded-xl border border-red-100 bg-red-50/40 space-y-2">
                <div className="flex justify-between items-center text-xs gap-2">
                  <span className="font-bold text-red-900">{rule.param}</span>
                  <span className="px-2 py-0.5 bg-red-200 text-red-900 rounded font-bold whitespace-nowrap">{rule.trigger}</span>
                </div>
                <p className="text-xs text-gray-700"><span className="font-semibold text-gray-900">Visual Symptom:</span> {rule.symptom}</p>
                <p className="text-xs text-blue-800"><span className="font-semibold text-blue-900">💧 Remediation:</span> {rule.remediation}</p>
              </div>
            ))}
          </div>
          <p className="text-[11px] text-gray-500">
            To show custom advice to gardeners in the app, add rules on the <b>Recommendations</b> page.
          </p>
        </div>
      </div>
    </div>
  );
}
