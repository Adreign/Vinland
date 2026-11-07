import React, { useEffect, useState } from "react";
import { collection, getDocs, doc, setDoc, deleteDoc } from "firebase/firestore";
import { db } from "../config/firebase";
import { Plus, Edit2, Trash2, Sprout, X, Check, Calendar, Sun, Droplets, Info } from "lucide-react";
import { notify, confirmAction } from "../components/common/Notifier";

export default function CropManagement() {
  const [crops, setCrops] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCrop, setEditingCrop] = useState(null);

  const emptyCrop = {
    name: "",
    scientificName: "",
    category: "Fruit Vegetable",
    emoji: "🍅",
    description: "",
    phMin: 6.0,
    phMax: 7.0,
    moistureMin: 30.0,
    moistureMax: 70.0,
    nitrogenMin: 20.0,
    nitrogenMax: 50.0,
    phosphorusMin: 15.0,
    phosphorusMax: 30.0,
    potassiumMin: 100.0,
    potassiumMax: 200.0
  };

  const [formData, setFormData] = useState(emptyCrop);

  useEffect(() => {
    fetchCrops();
  }, []);

  async function fetchCrops() {
    setLoading(true);
    try {
      const snap = await getDocs(collection(db, "crops"));
      setCrops(snap.docs.map(d => ({ id: d.id, ...d.data() })));
    } catch (e) {
      console.error("Error loading crops from Firestore:", e);
    } finally {
      setLoading(false);
    }
  }

  function openAddModal() {
    setEditingCrop(null);
    setFormData(emptyCrop);
    setIsModalOpen(true);
  }

  function openEditModal(crop) {
    setEditingCrop(crop);
    setFormData({
      ...emptyCrop,
      ...crop
    });
    setIsModalOpen(true);
  }

  async function handleSave(e) {
    e.preventDefault();
    try {
      const cropId = editingCrop ? editingCrop.id : formData.name.toLowerCase().replace(/\s+/g, '_');
      const docRef = doc(db, "crops", cropId);
      await setDoc(docRef, { ...formData, id: cropId }, { merge: true });
      setIsModalOpen(false);
      notify(editingCrop ? "Crop profile updated." : "Crop profile added.");
      fetchCrops();
    } catch (e) {
      notify("Error saving crop profile: " + e.message, "error");
    }
  }

  async function handleDelete(cropId) {
    if (await confirmAction("Are you sure you want to delete this crop profile from Firestore?", { confirmLabel: "Delete", danger: true })) {
      try {
        await deleteDoc(doc(db, "crops", cropId));
        notify("Crop profile deleted.");
        fetchCrops();
      } catch (e) {
        notify("Could not delete crop profile: " + e.message, "error");
      }
    }
  }

  return (
    <div className="p-4 sm:p-6 lg:p-8 space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Crop Management</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Accurate crop profile data stored in Firestore `crops` collection. Syncs directly with mobile app crop profiles.
          </p>
        </div>
        <button
          onClick={openAddModal}
          className="bg-green-700 hover:bg-green-800 text-white font-semibold px-4 py-2.5 rounded-xl shadow-xs transition-colors flex items-center gap-2 text-sm shrink-0"
        >
          <Plus size={18} /> Add New Crop Profile
        </button>
      </div>

      {/* Crop Cards Grid */}
      {loading ? (
        <div className="p-4 sm:p-6 lg:p-8 text-center text-gray-500 font-medium">Fetching crop profiles from Firestore...</div>
      ) : crops.length === 0 ? (
        <div className="p-12 text-center text-gray-400 bg-white rounded-2xl border">
          No crop profiles found in `crops` collection.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {crops.map((crop) => (
            <div key={crop.id} className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-4 hover:shadow-md transition-shadow flex flex-col justify-between">
              <div className="space-y-4">
                <div className="flex justify-between items-start">
                  <div className="flex items-center space-x-3">
                    <span className="text-3xl p-2 bg-green-50 rounded-xl border border-green-100">{crop.emoji || "🌱"}</span>
                    <div>
                      <h3 className="font-bold text-gray-900 text-lg">{crop.name}</h3>
                      <p className="text-xs text-gray-400 italic">{crop.scientificName || crop.category}</p>
                    </div>
                  </div>
                  <div className="flex space-x-1">
                    <button onClick={() => openEditModal(crop)} title="Edit Crop Profile" className="p-2 hover:bg-gray-100 rounded-lg text-gray-600 transition-colors">
                      <Edit2 size={16} />
                    </button>
                    <button onClick={() => handleDelete(crop.id)} title="Delete Crop Profile" className="p-2 hover:bg-red-50 text-red-600 rounded-lg transition-colors">
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>

                <p className="text-xs text-gray-600 line-clamp-2">{crop.description}</p>

                {/* Agronomic Requirements */}
                <div className="space-y-1">
                  <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider flex items-center gap-1">
                    <Sprout size={12} className="text-green-700" /> Agronomic Requirements Range
                  </p>
                  <div className="grid grid-cols-2 gap-2 text-xs bg-slate-50 p-3 rounded-xl border border-slate-100 text-slate-700">
                    <div><span className="font-semibold text-slate-900">pH Target:</span> {crop.phMin} - {crop.phMax}</div>
                    <div><span className="font-semibold text-slate-900">Moisture:</span> {crop.moistureMin}% - {crop.moistureMax}%</div>
                    <div><span className="font-semibold text-slate-900">Nitrogen (N):</span> {crop.nitrogenMin}-{crop.nitrogenMax} ppm</div>
                    <div><span className="font-semibold text-slate-900">Phosphorus (P):</span> {crop.phosphorusMin}-{crop.phosphorusMax} ppm</div>
                    <div className="col-span-2"><span className="font-semibold text-slate-900">Potassium (K):</span> {crop.potassiumMin}-{crop.potassiumMax} ppm</div>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add / Edit Crop Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-2xl w-full p-6 space-y-4 max-h-[90vh] overflow-y-auto shadow-2xl">
            <div className="flex justify-between items-center border-b pb-3">
              <h3 className="font-bold text-lg text-gray-900">
                {editingCrop ? `Edit Crop Profile: ${editingCrop.name}` : "Add New Crop Profile"}
              </h3>
              <button onClick={() => setIsModalOpen(false)} className="text-gray-400 hover:text-gray-600"><X size={20}/></button>
            </div>

            <form onSubmit={handleSave} className="space-y-4 text-sm">
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Crop Name</label>
                  <input required type="text" value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" placeholder="Tomato" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Scientific Name</label>
                  <input type="text" value={formData.scientificName} onChange={e => setFormData({...formData, scientificName: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" placeholder="Solanum lycopersicum" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Emoji Icon</label>
                  <input type="text" value={formData.emoji} onChange={e => setFormData({...formData, emoji: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm text-center" placeholder="🍅" />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Description</label>
                <textarea rows={2} value={formData.description} onChange={e => setFormData({...formData, description: e.target.value})} className="w-full border rounded-xl p-2 text-sm" placeholder="Crop overview..." />
              </div>

              {/* Requirements */}
              <div className="pt-2 border-t">
                <p className="text-xs font-bold text-green-800 uppercase tracking-wider mb-2">pH, Moisture & NPK Tolerances</p>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-gray-700">pH Level Min - Max</label>
                    <div className="flex gap-2">
                      <input type="number" step="0.1" value={formData.phMin} onChange={e => setFormData({...formData, phMin: parseFloat(e.target.value)})} className="w-1/2 border rounded-xl px-3 py-1.5" />
                      <input type="number" step="0.1" value={formData.phMax} onChange={e => setFormData({...formData, phMax: parseFloat(e.target.value)})} className="w-1/2 border rounded-xl px-3 py-1.5" />
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-700">Moisture Min - Max (%)</label>
                    <div className="flex gap-2">
                      <input type="number" value={formData.moistureMin} onChange={e => setFormData({...formData, moistureMin: parseFloat(e.target.value)})} className="w-1/2 border rounded-xl px-3 py-1.5" />
                      <input type="number" value={formData.moistureMax} onChange={e => setFormData({...formData, moistureMax: parseFloat(e.target.value)})} className="w-1/2 border rounded-xl px-3 py-1.5" />
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-700">Nitrogen Range (ppm)</label>
                    <div className="flex gap-2">
                      <input type="number" value={formData.nitrogenMin} onChange={e => setFormData({...formData, nitrogenMin: parseFloat(e.target.value)})} className="w-1/2 border rounded-xl px-3 py-1.5" />
                      <input type="number" value={formData.nitrogenMax} onChange={e => setFormData({...formData, nitrogenMax: parseFloat(e.target.value)})} className="w-1/2 border rounded-xl px-3 py-1.5" />
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-700">Phosphorus Range (ppm)</label>
                    <div className="flex gap-2">
                      <input type="number" value={formData.phosphorusMin} onChange={e => setFormData({...formData, phosphorusMin: parseFloat(e.target.value)})} className="w-1/2 border rounded-xl px-3 py-1.5" />
                      <input type="number" value={formData.phosphorusMax} onChange={e => setFormData({...formData, phosphorusMax: parseFloat(e.target.value)})} className="w-1/2 border rounded-xl px-3 py-1.5" />
                    </div>
                  </div>
                </div>
              </div>

              <div className="flex justify-end space-x-3 pt-4 border-t">
                <button type="button" onClick={() => setIsModalOpen(false)} className="px-4 py-2 border rounded-xl font-medium text-gray-600 hover:bg-gray-50">Cancel</button>
                <button type="submit" className="px-5 py-2 bg-green-700 text-white rounded-xl font-semibold hover:bg-green-800 shadow-xs">Save Crop Profile in Firestore</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
