import React, { useState } from "react";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";
import { db } from "../config/firebase";
import { X } from "lucide-react";
import { notify } from "./common/Notifier";
import { QC_BARANGAYS } from "../data/qcBarangays";

// Firestore document ids can't contain "/" and shouldn't start/end with "__".
// We keep it simple: letters, numbers, dash and underscore.
const DEVICE_ID_PATTERN = /^[A-Z0-9_-]{3,40}$/;

function gardenerName(g) {
  return g.fullName || `${g.firstName || ""} ${g.lastName || ""}`.trim() || g.email || "Gardener";
}

/**
 * Form that adds a new sensor device to Firestore (sensor_devices/{deviceId}).
 * The ESP32 later fills in the readings (ph, moisture, nitrogen, phosphorus,
 * potassium, timestamp) on the same document.
 */
export default function AddSensorDeviceModal({ gardeners, onClose, onSaved }) {
  const [deviceId, setDeviceId] = useState("");
  const [name, setName] = useState("");
  const [barangay, setBarangay] = useState("");
  const [assignedTo, setAssignedTo] = useState("");
  const [saving, setSaving] = useState(false);

  const idValid = DEVICE_ID_PATTERN.test(deviceId);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!idValid) {
      notify("Device ID must be 3-40 characters: letters, numbers, - or _ only.", "error");
      return;
    }

    setSaving(true);
    try {
      const ref = doc(db, "sensor_devices", deviceId);
      if ((await getDoc(ref)).exists()) {
        notify(`A sensor device with ID ${deviceId} already exists.`, "error");
        return;
      }

      const gardener = gardeners.find(g => g.id === assignedTo);
      await setDoc(ref, {
        deviceId,
        name: name.trim() || deviceId,
        barangay: barangay || null,
        assignedTo: gardener ? gardener.id : null,
        assignedToName: gardener ? gardenerName(gardener) : null,
        status: "Active",
        createdAt: Date.now()
      });

      // Link the device to the gardener so the app reads this sensor for them.
      if (gardener) {
        await updateDoc(doc(db, "users", gardener.id), { sensorDeviceId: deviceId });
      }

      notify(gardener
        ? `Sensor ${deviceId} added and assigned to ${gardenerName(gardener)}.`
        : `Sensor ${deviceId} added.`);
      onSaved?.();
      onClose();
    } catch (err) {
      notify("Could not add sensor device: " + err.message, "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg p-6 space-y-4 max-h-[90vh] overflow-y-auto">
        <div className="flex justify-between items-center border-b pb-3">
          <h3 className="font-bold text-lg text-gray-900">Add Sensor Device</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 text-sm">
          <div>
            <label className="block text-xs font-semibold text-gray-700 mb-1">Device ID</label>
            <input
              required
              type="text"
              value={deviceId}
              onChange={e => setDeviceId(e.target.value.toUpperCase().replace(/\s+/g, "-"))}
              className={`w-full border rounded-xl px-3 py-2 text-sm font-mono ${deviceId && !idValid ? "border-red-400" : ""}`}
              placeholder="VINLAND-ESP32-02"
            />
            <p className={`text-[11px] mt-1 ${deviceId && !idValid ? "text-red-600" : "text-gray-400"}`}>
              Must match the ID in the ESP32 code. Letters, numbers, - and _ only.
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-700 mb-1">Device Name <span className="font-normal text-gray-400">(optional)</span></label>
            <input
              type="text"
              value={name}
              onChange={e => setName(e.target.value)}
              className="w-full border rounded-xl px-3 py-2 text-sm"
              placeholder="Maria's backyard sensor"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1">Barangay <span className="font-normal text-gray-400">(optional)</span></label>
              <select value={barangay} onChange={e => setBarangay(e.target.value)} className="w-full border rounded-xl px-3 py-2 text-sm">
                <option value="">Not set</option>
                {QC_BARANGAYS.map(b => <option key={b} value={b}>{b}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1">Assign to Gardener <span className="font-normal text-gray-400">(optional)</span></label>
              <select value={assignedTo} onChange={e => setAssignedTo(e.target.value)} className="w-full border rounded-xl px-3 py-2 text-sm">
                <option value="">Not assigned</option>
                {gardeners.map(g => <option key={g.id} value={g.id}>{gardenerName(g)}</option>)}
              </select>
            </div>
          </div>

          {assignedTo && (
            <p className="text-[11px] text-gray-500 bg-gray-50 border rounded-xl p-2.5">
              This gardener's app will read soil data from this sensor from now on.
            </p>
          )}

          <div className="flex justify-end space-x-3 pt-3 border-t">
            <button type="button" onClick={onClose} className="px-4 py-2 border rounded-xl font-medium text-gray-600 hover:bg-gray-50">Cancel</button>
            <button type="submit" disabled={saving || !idValid} className="px-5 py-2 bg-green-700 text-white rounded-xl font-semibold hover:bg-green-800 shadow-xs disabled:opacity-60">
              {saving ? "Adding..." : "Add Device"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
