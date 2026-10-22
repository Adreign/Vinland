import React, { useEffect, useState } from "react";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { db } from "../config/firebase";
import { Settings as SettingsIcon, Shield, Database, Bell, Cpu, Sun, Users, Save, CheckCircle2, Lock, RefreshCw } from "lucide-react";

export default function Settings() {
  const [loading, setLoading] = useState(false);
  const [saveMessage, setSaveMessage] = useState(false);

  // 1. System Configuration
  const [sysConfig, setSysConfig] = useState({
    systemTitle: "Vinland Urban Leaf Vegetables Gardening Portal",
    targetLocation: "Quezon City, Metro Manila"
  });

  // 2. Sensor Configuration
  const [sensorConfig, setSensorConfig] = useState({
    primaryDeviceId: "VINLAND-ESP32-01",
    telemetryIntervalSeconds: 10,
    phCalibrationOffset: 0.0
  });

  // 3. Notification Settings
  const [notifSettings, setNotifSettings] = useState({
    fcmPushEnabled: true,
    soilDeficiencyAlerts: true
  });

  useEffect(() => {
    async function loadSettingsFromFirestore() {
      setLoading(true);
      try {
        const snap = await getDoc(doc(db, "system_settings", "config"));
        if (snap.exists()) {
          const data = snap.data();
          if (data.sysConfig) setSysConfig(data.sysConfig);
          if (data.sensorConfig) setSensorConfig(data.sensorConfig);
          if (data.notifSettings) setNotifSettings(data.notifSettings);
        }
      } catch (e) {
        console.error("Error loading system settings:", e);
      } finally {
        setLoading(false);
      }
    }

    loadSettingsFromFirestore();
  }, []);

  async function handleSaveAll() {
    setLoading(true);
    try {
      await setDoc(doc(db, "system_settings", "config"), {
        sysConfig,
        sensorConfig,
        notifSettings,
        updatedAt: new Date().toISOString()
      }, { merge: true });

      setSaveMessage(true);
      setTimeout(() => setSaveMessage(false), 3000);
    } catch (e) {
      alert("Failed to save settings: " + e.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="p-8 space-y-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">System Settings & Controls</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Accurate system configuration controls synced directly to Firestore `system_settings/config` document.
          </p>
        </div>

        <button
          onClick={handleSaveAll}
          disabled={loading}
          className="bg-green-700 hover:bg-green-800 text-white font-semibold px-5 py-2.5 rounded-xl shadow-xs transition-colors flex items-center gap-2 text-sm shrink-0"
        >
          {loading ? <RefreshCw size={18} className="animate-spin" /> : <Save size={18} />}
          <span>Save Settings to Firestore</span>
        </button>
      </div>

      {saveMessage && (
        <div className="p-4 bg-green-50 border border-green-200 text-green-800 rounded-xl text-xs font-semibold flex items-center gap-2">
          <CheckCircle2 size={18} className="text-green-600" />
          System configurations successfully saved to Firestore!
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* System Configuration */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-base">
            <SettingsIcon size={20} className="text-emerald-700" /> System Portal Configuration
          </h3>
          <div className="space-y-3 text-xs">
            <div>
              <label className="block font-semibold text-gray-700 mb-1">System Portal Title</label>
              <input type="text" value={sysConfig.systemTitle} onChange={e => setSysConfig({...sysConfig, systemTitle: e.target.value})} className="w-full border rounded-xl p-2.5" />
            </div>
            <div>
              <label className="block font-semibold text-gray-700 mb-1">Target Municipality Zone</label>
              <input type="text" value={sysConfig.targetLocation} onChange={e => setSysConfig({...sysConfig, targetLocation: e.target.value})} className="w-full border rounded-xl p-2.5" />
            </div>
          </div>
        </div>

        {/* ESP32 Hardware Config */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-base">
            <Cpu size={20} className="text-blue-700" /> ESP32 Hardware Sensor Settings
          </h3>
          <div className="space-y-3 text-xs">
            <div>
              <label className="block font-semibold text-gray-700 mb-1">Target ESP32 Device Document ID</label>
              <input type="text" value={sensorConfig.primaryDeviceId} onChange={e => setSensorConfig({...sensorConfig, primaryDeviceId: e.target.value})} className="w-full border rounded-xl p-2.5" />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="block font-semibold text-gray-700 mb-1">Telemetry Delay (sec)</label>
                <input type="number" value={sensorConfig.telemetryIntervalSeconds} onChange={e => setSensorConfig({...sensorConfig, telemetryIntervalSeconds: parseInt(e.target.value)})} className="w-full border rounded-xl p-2.5" />
              </div>
              <div>
                <label className="block font-semibold text-gray-700 mb-1">pH Calibration Offset</label>
                <input type="number" step="0.1" value={sensorConfig.phCalibrationOffset} onChange={e => setSensorConfig({...sensorConfig, phCalibrationOffset: parseFloat(e.target.value)})} className="w-full border rounded-xl p-2.5" />
              </div>
            </div>
          </div>
        </div>

        {/* Notifications Config */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-base">
            <Bell size={20} className="text-purple-700" /> Push Notification Triggers
          </h3>
          <div className="space-y-3 text-xs">
            <label className="flex items-center justify-between p-3 bg-gray-50 rounded-xl cursor-pointer">
              <span className="font-semibold text-gray-800">Enable Firebase Cloud Messaging (FCM) Push</span>
              <input type="checkbox" checked={notifSettings.fcmPushEnabled} onChange={e => setNotifSettings({...notifSettings, fcmPushEnabled: e.target.checked})} className="accent-green-700 w-4 h-4" />
            </label>
            <label className="flex items-center justify-between p-3 bg-gray-50 rounded-xl cursor-pointer">
              <span className="font-semibold text-gray-800">Alert Gardeners on Soil Deficiency</span>
              <input type="checkbox" checked={notifSettings.soilDeficiencyAlerts} onChange={e => setNotifSettings({...notifSettings, soilDeficiencyAlerts: e.target.checked})} className="accent-green-700 w-4 h-4" />
            </label>
          </div>
        </div>

        {/* Database Management */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4">
          <h3 className="font-bold text-gray-900 flex items-center gap-2 border-b pb-3 text-base">
            <Database size={20} className="text-emerald-700" /> Firebase Cloud Connection
          </h3>
          <div className="p-3.5 bg-green-50 rounded-xl text-xs space-y-1">
            <p className="font-bold text-green-900">Project ID: vinland-b6e0b</p>
            <p className="text-green-700">Firestore Instance: vinland-b6e0b.firebaseapp.com</p>
          </div>
        </div>
      </div>
    </div>
  );
}
