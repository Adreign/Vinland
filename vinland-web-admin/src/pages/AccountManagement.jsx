import React, { useEffect, useState } from "react";
import { collection, getDocs, doc, setDoc, deleteDoc } from "firebase/firestore";
import { db } from "../config/firebase";
import { useAuth } from "../context/AuthContext";
import { Search, UserPlus, Edit3, Trash2, Eye, ShieldCheck, Crown, UserCheck, X, Check, Lock, CheckCircle2, Sprout } from "lucide-react";

export default function AccountManagement() {
  const { isSuperAdmin, user: currentUser } = useAuth();
  const [gardeners, setGardeners] = useState([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);

  // Modals state
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isViewModalOpen, setIsViewModalOpen] = useState(false);
  const [selectedGardener, setSelectedGardener] = useState(null);
  const [userSoilLogs, setUserSoilLogs] = useState([]);
  const [userHarvestLogs, setUserHarvestLogs] = useState([]);
  const [loadingLogs, setLoadingLogs] = useState(false);

  const emptyGardener = {
    firstName: "",
    lastName: "",
    email: "",
    phone: "",
    barangay: "Batasan Hills",
    gardenType: "Backyard Garden",
    crops: ["Tomato", "Green Pepper"],
    role: "gardener",
    status: "Active"
  };

  const [formData, setFormData] = useState(emptyGardener);

  useEffect(() => {
    fetchGardeners();
  }, []);

  async function fetchGardeners() {
    setLoading(true);
    try {
      const snap = await getDocs(collection(db, "users"));
      const list = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      setGardeners(list);
    } catch (e) {
      console.error("Error fetching gardeners from Firestore:", e);
    } finally {
      setLoading(false);
    }
  }

  function openAddModal() {
    setFormData(emptyGardener);
    setIsAddModalOpen(true);
  }

  function openEditModal(g) {
    setSelectedGardener(g);
    setFormData({
      firstName: g.firstName || g.fullName?.split(" ")[0] || "",
      lastName: g.lastName || g.fullName?.split(" ")[1] || "",
      email: g.email || "",
      phone: g.phone || "",
      barangay: g.barangay || "Quezon City",
      gardenType: g.gardenType || "Backyard Garden",
      crops: Array.isArray(g.crops) ? g.crops : [],
      role: g.role || "gardener",
      status: g.status || "Active"
    });
    setIsEditModalOpen(true);
  }

  async function openViewModal(g) {
    setSelectedGardener(g);
    setIsViewModalOpen(true);
    setLoadingLogs(true);
    setUserSoilLogs([]);
    setUserHarvestLogs([]);

    try {
      const soilSnap = await getDocs(collection(db, "users", g.id, "soil_logs"));
      setUserSoilLogs(soilSnap.docs.map(d => ({ id: d.id, ...d.data() })));

      const harvestSnap = await getDocs(collection(db, "users", g.id, "harvest_logs"));
      setUserHarvestLogs(harvestSnap.docs.map(d => ({ id: d.id, ...d.data() })));
    } catch (e) {
      console.error("Error loading user subcollections:", e);
    } finally {
      setLoadingLogs(false);
    }
  }

  async function handleRoleChange(userId, newRole, currentAccountRole) {
    if (currentAccountRole === "super_admin" && !isSuperAdmin) {
      alert("Only a Super Administrator can modify Super Admin accounts.");
      return;
    }

    if (confirm(`Change this account's role to ${newRole.toUpperCase()}?`)) {
      try {
        await setDoc(doc(db, "users", userId), { role: newRole }, { merge: true });
        setGardeners(prev => prev.map(g => g.id === userId ? { ...g, role: newRole } : g));
      } catch (e) {
        alert("Failed to update user role: " + e.message);
      }
    }
  }

  async function handleAddGardener(e) {
    e.preventDefault();
    try {
      const userId = `user_${Date.now()}`;
      const fullName = `${formData.firstName} ${formData.lastName}`.trim();
      const newAccount = {
        id: userId,
        ...formData,
        fullName,
        createdAt: new Date().toISOString()
      };
      await setDoc(doc(db, "users", userId), newAccount);
      setIsAddModalOpen(false);
      fetchGardeners();
    } catch (e) {
      alert("Error adding gardener account: " + e.message);
    }
  }

  async function handleUpdateGardener(e) {
    e.preventDefault();
    try {
      const fullName = `${formData.firstName} ${formData.lastName}`.trim();
      await setDoc(doc(db, "users", selectedGardener.id), {
        ...formData,
        fullName
      }, { merge: true });
      setIsEditModalOpen(false);
      fetchGardeners();
    } catch (e) {
      alert("Error updating gardener account: " + e.message);
    }
  }

  async function handleStatusChange(userId, newStatus) {
    try {
      await setDoc(doc(db, "users", userId), { status: newStatus }, { merge: true });
      setGardeners(prev => prev.map(g => g.id === userId ? { ...g, status: newStatus } : g));
    } catch (e) {
      alert("Error changing status: " + e.message);
    }
  }

  async function handleDeleteGardener(g) {
    if (g.role === "super_admin" && !isSuperAdmin) {
      alert("Only a Super Administrator can delete Super Admin accounts.");
      return;
    }

    if (confirm(`Are you sure you want to delete the account for ${g.fullName || g.email}? This will permanently remove the record from Firestore.`)) {
      try {
        // Delete document from Firestore database
        await deleteDoc(doc(db, "users", g.id));

        // Delete associated subcollections if any exist
        try {
          const soilSnap = await getDocs(collection(db, "users", g.id, "soil_logs"));
          soilSnap.docs.forEach(async (d) => await deleteDoc(d.ref));

          const harvestSnap = await getDocs(collection(db, "users", g.id, "harvest_logs"));
          harvestSnap.docs.forEach(async (d) => await deleteDoc(d.ref));
        } catch (subErr) {
          console.warn("Subcollections cleanup notice:", subErr);
        }

        // Optimistically remove from UI state immediately
        setGardeners(prev => prev.filter(item => item.id !== g.id));
      } catch (e) {
        alert("Could not delete account from Firestore: " + e.message);
      }
    }
  }

  const filtered = gardeners.filter(g =>
    (g.fullName || `${g.firstName || ''} ${g.lastName || ''}`).toLowerCase().includes(search.toLowerCase()) ||
    g.email?.toLowerCase().includes(search.toLowerCase()) ||
    g.barangay?.toLowerCase().includes(search.toLowerCase()) ||
    g.gardenType?.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="p-8 space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Account Management & RBAC</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Role-Based Access Control (RBAC): Assign Super Admin, Admin, or Gardener user roles and manage user profiles.
          </p>
        </div>

        <button
          onClick={openAddModal}
          className="bg-green-700 hover:bg-green-800 text-white font-semibold px-4 py-2.5 rounded-xl shadow-xs transition-colors flex items-center gap-2 text-sm shrink-0"
        >
          <UserPlus size={18} /> Add User Account
        </button>
      </div>

      {/* Search Bar & Filters */}
      <div className="bg-white p-4 rounded-2xl border border-gray-200 shadow-xs flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="relative w-full md:w-96">
          <Search className="absolute left-3.5 top-3 text-gray-400" size={18} />
          <input
            type="text"
            placeholder="Search account name, email, barangay..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full bg-gray-50 border border-gray-200 rounded-xl pl-10 pr-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
          />
        </div>
        <div className="text-xs text-gray-500 font-medium">
          Total Accounts: <span className="font-bold text-gray-900">{filtered.length}</span>
        </div>
      </div>

      {/* Gardeners Data Table */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xs overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500 font-medium">Fetching accounts from Firestore `users` collection...</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50/70 text-xs text-gray-500 font-semibold uppercase tracking-wider">
                <tr>
                  <th className="px-6 py-4 text-left">User Name</th>
                  <th className="px-6 py-4 text-left">Email Address</th>
                  <th className="px-6 py-4 text-left">Barangay</th>
                  <th className="px-6 py-4 text-left">Status</th>
                  <th className="px-6 py-4 text-left">Role Access Level</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 text-sm text-gray-700">
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan="6" className="px-6 py-8 text-center text-gray-400">
                      No matching accounts found in database.
                    </td>
                  </tr>
                ) : (
                  filtered.map((g) => (
                    <tr key={g.id} className="hover:bg-gray-50/60 transition-colors">
                      <td className="px-6 py-4 font-bold text-gray-900">
                        {g.fullName || `${g.firstName || ''} ${g.lastName || ''}`.trim() || "User"}
                      </td>
                      <td className="px-6 py-4 text-gray-600">{g.email || "No email"}</td>
                      <td className="px-6 py-4">
                        <span className="px-2.5 py-1 bg-gray-100 text-gray-700 rounded-lg text-xs font-medium">
                          📍 {g.barangay || "Quezon City"}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <select
                          value={g.status || "Active"}
                          onChange={(e) => handleStatusChange(g.id, e.target.value)}
                          className={`text-xs font-semibold px-2.5 py-1 rounded-full border cursor-pointer ${
                            g.status === "Active" || !g.status
                              ? "bg-green-50 text-green-700 border-green-200"
                              : g.status === "Pending"
                              ? "bg-amber-50 text-amber-700 border-amber-200"
                              : "bg-red-50 text-red-700 border-red-200"
                          }`}
                        >
                          <option value="Active">Active</option>
                          <option value="Pending">Pending</option>
                          <option value="Suspended">Suspended</option>
                        </select>
                      </td>
                      <td className="px-6 py-4 font-semibold text-xs">
                        <select
                          value={g.role || "gardener"}
                          onChange={(e) => handleRoleChange(g.id, e.target.value, g.role)}
                          disabled={g.role === "super_admin" && !isSuperAdmin}
                          className={`text-xs font-bold px-3 py-1.5 rounded-xl border cursor-pointer transition-all ${
                            g.role === "super_admin"
                              ? "bg-amber-100 text-amber-900 border-amber-300"
                              : g.role === "admin"
                              ? "bg-green-100 text-green-900 border-green-300"
                              : "bg-slate-100 text-slate-700 border-slate-200"
                          }`}
                        >
                          <option value="gardener">👤 Gardener</option>
                          <option value="admin">🛡️ Administrator</option>
                          {isSuperAdmin && <option value="super_admin">⭐ Super Administrator</option>}
                        </select>
                      </td>
                      <td className="px-6 py-4 text-right">
                        <div className="flex justify-end space-x-1">
                          <button
                            onClick={() => openViewModal(g)}
                            title="View Info & Logs"
                            className="p-1.5 hover:bg-gray-100 rounded-lg text-gray-600 transition-colors"
                          >
                            <Eye size={16} />
                          </button>
                          <button
                            onClick={() => openEditModal(g)}
                            title="Edit Account"
                            className="p-1.5 hover:bg-green-50 text-green-700 rounded-lg transition-colors"
                          >
                            <Edit3 size={16} />
                          </button>
                          <button
                            onClick={() => handleDeleteGardener(g)}
                            title="Delete Account"
                            className="p-1.5 hover:bg-red-50 text-red-600 rounded-lg transition-colors"
                          >
                            <Trash2 size={16} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* View Gardener Info Modal */}
      {isViewModalOpen && selectedGardener && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-2xl w-full p-6 space-y-4 shadow-2xl max-h-[90vh] overflow-y-auto">
            <div className="flex justify-between items-center border-b pb-3">
              <h3 className="font-bold text-lg text-gray-900">User Profile & Activity Logs</h3>
              <button onClick={() => setIsViewModalOpen(false)} className="text-gray-400 hover:text-gray-600"><X size={20}/></button>
            </div>

            <div className="grid grid-cols-2 gap-4 text-sm bg-gray-50 p-4 rounded-xl border">
              <div>
                <p className="text-[10px] text-gray-400 uppercase font-bold">User Full Name</p>
                <p className="font-bold text-gray-900 text-base">{selectedGardener.fullName || `${selectedGardener.firstName || ''} ${selectedGardener.lastName || ''}`.trim() || "User"}</p>
              </div>
              <div>
                <p className="text-[10px] text-gray-400 uppercase font-bold">Email Address</p>
                <p className="text-gray-700 font-medium">{selectedGardener.email || "N/A"}</p>
              </div>
              <div>
                <p className="text-[10px] text-gray-400 uppercase font-bold">Barangay</p>
                <p className="text-gray-700">{selectedGardener.barangay || "Quezon City"}</p>
              </div>
              <div>
                <p className="text-[10px] text-gray-400 uppercase font-bold">Role Tier</p>
                <span className="font-bold text-xs px-2.5 py-0.5 rounded-full bg-green-100 text-green-800">
                  {selectedGardener.role === "super_admin" ? "⭐ Super Admin" : selectedGardener.role === "admin" ? "🛡️ Admin" : "👤 Gardener"}
                </span>
              </div>
            </div>

            <div className="pt-3 border-t flex justify-end">
              <button onClick={() => setIsViewModalOpen(false)} className="px-4 py-2 bg-gray-100 hover:bg-gray-200 rounded-xl font-medium text-gray-700">Close</button>
            </div>
          </div>
        </div>
      )}

      {/* Edit Gardener Modal */}
      {isEditModalOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 space-y-4 shadow-2xl">
            <div className="flex justify-between items-center border-b pb-3">
              <h3 className="font-bold text-lg text-gray-900">Edit Account Details</h3>
              <button onClick={() => setIsEditModalOpen(false)} className="text-gray-400 hover:text-gray-600"><X size={20}/></button>
            </div>
            <form onSubmit={handleUpdateGardener} className="space-y-4 text-sm">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">First Name</label>
                  <input required type="text" value={formData.firstName} onChange={e => setFormData({...formData, firstName: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Last Name</label>
                  <input required type="text" value={formData.lastName} onChange={e => setFormData({...formData, lastName: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" />
                </div>
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Email Address</label>
                <input required type="email" value={formData.email} onChange={e => setFormData({...formData, email: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Barangay</label>
                  <input type="text" value={formData.barangay} onChange={e => setFormData({...formData, barangay: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Role Level</label>
                  <select value={formData.role} onChange={e => setFormData({...formData, role: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm">
                    <option value="gardener">Gardener</option>
                    <option value="admin">Administrator</option>
                    {isSuperAdmin && <option value="super_admin">Super Administrator</option>}
                  </select>
                </div>
              </div>
              <div className="flex justify-end space-x-3 pt-3 border-t">
                <button type="button" onClick={() => setIsEditModalOpen(false)} className="px-4 py-2 border rounded-xl font-medium text-gray-600 hover:bg-gray-50">Cancel</button>
                <button type="submit" className="px-5 py-2 bg-green-700 text-white rounded-xl font-semibold hover:bg-green-800 shadow-xs">Save Changes</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add User Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 space-y-4 shadow-2xl">
            <div className="flex justify-between items-center border-b pb-3">
              <h3 className="font-bold text-lg text-gray-900">Add New User Account</h3>
              <button onClick={() => setIsAddModalOpen(false)} className="text-gray-400 hover:text-gray-600"><X size={20}/></button>
            </div>
            <form onSubmit={handleAddGardener} className="space-y-4 text-sm">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">First Name</label>
                  <input required type="text" value={formData.firstName} onChange={e => setFormData({...formData, firstName: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" placeholder="Maria" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Last Name</label>
                  <input required type="text" value={formData.lastName} onChange={e => setFormData({...formData, lastName: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" placeholder="Santos" />
                </div>
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Email Address</label>
                <input required type="email" value={formData.email} onChange={e => setFormData({...formData, email: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" placeholder="maria@example.com" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Barangay</label>
                  <input type="text" value={formData.barangay} onChange={e => setFormData({...formData, barangay: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm" placeholder="Batasan Hills" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">Role Level</label>
                  <select value={formData.role} onChange={e => setFormData({...formData, role: e.target.value})} className="w-full border rounded-xl px-3 py-2 text-sm">
                    <option value="gardener">Gardener</option>
                    <option value="admin">Administrator</option>
                    {isSuperAdmin && <option value="super_admin">Super Administrator</option>}
                  </select>
                </div>
              </div>
              <div className="flex justify-end space-x-3 pt-3 border-t">
                <button type="button" onClick={() => setIsAddModalOpen(false)} className="px-4 py-2 border rounded-xl font-medium text-gray-600 hover:bg-gray-50">Cancel</button>
                <button type="submit" className="px-5 py-2 bg-green-700 text-white rounded-xl font-semibold hover:bg-green-800 shadow-xs">Create Account</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
