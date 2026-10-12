import React, { useEffect, useState } from "react";
import { collection, getDocs, doc, setDoc } from "firebase/firestore";
import { db } from "../config/firebase";
import { Search, Shield, ShieldCheck, UserCheck } from "lucide-react";

export default function AccountManagement() {
  const [gardeners, setGardeners] = useState([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);

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
      console.error("Error fetching gardeners:", e);
    } finally {
      setLoading(false);
    }
  }

  async function toggleAdminRole(userId, currentRole) {
    const newRole = currentRole === "admin" ? "gardener" : "admin";
    if (confirm(`Are you sure you want to change this account's role to ${newRole.toUpperCase()}?`)) {
      try {
        await setDoc(doc(db, "users", userId), { role: newRole }, { merge: true });
        fetchGardeners();
      } catch (e) {
        alert("Failed to update user role: " + e.message);
      }
    }
  }

  const filtered = gardeners.filter(g =>
    (g.fullName || `${g.firstName} ${g.lastName}`).toLowerCase().includes(search.toLowerCase()) ||
    g.email?.toLowerCase().includes(search.toLowerCase()) ||
    g.barangay?.toLowerCase().includes(search.toLowerCase()) ||
    g.gardenType?.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="p-8 space-y-6 max-w-7xl mx-auto">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Gardener Account Management</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            View registered gardener profiles, barangay locations, garden types, and crop selections.
          </p>
        </div>

        {/* Search Box */}
        <div className="relative w-full md:w-80">
          <Search className="absolute left-3.5 top-3 text-gray-400" size={18} />
          <input
            type="text"
            placeholder="Search gardener, email, barangay..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full bg-white border border-gray-200 rounded-xl pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-green-500 shadow-xs"
          />
        </div>
      </div>

      {/* Gardeners Data Table */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xs overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500 font-medium">Fetching gardener accounts from Firestore...</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50/70 text-xs text-gray-500 font-semibold uppercase tracking-wider">
                <tr>
                  <th className="px-6 py-4 text-left">Gardener Name</th>
                  <th className="px-6 py-4 text-left">Email Address</th>
                  <th className="px-6 py-4 text-left">Barangay Location</th>
                  <th className="px-6 py-4 text-left">Garden Type</th>
                  <th className="px-6 py-4 text-left">Selected Crops</th>
                  <th className="px-6 py-4 text-left">Account Role</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 text-sm text-gray-700">
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan="7" className="px-6 py-8 text-center text-gray-400">
                      No matching gardener accounts found.
                    </td>
                  </tr>
                ) : (
                  filtered.map((g) => (
                    <tr key={g.id} className="hover:bg-gray-50/60 transition-colors">
                      <td className="px-6 py-4 font-bold text-gray-900">
                        {g.fullName || `${g.firstName || ''} ${g.lastName || ''}`.trim() || "Gardener"}
                      </td>
                      <td className="px-6 py-4 text-gray-600">{g.email}</td>
                      <td className="px-6 py-4">
                        <span className="px-2.5 py-1 bg-gray-100 text-gray-700 rounded-lg text-xs font-medium">
                          📍 {g.barangay || "Quezon City"}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-gray-600">{g.gardenType || "Backyard"}</td>
                      <td className="px-6 py-4">
                        <div className="flex flex-wrap gap-1">
                          {g.crops && g.crops.length > 0 ? (
                            g.crops.map((crop, idx) => (
                              <span key={idx} className="px-2 py-0.5 bg-green-50 text-green-700 rounded-md text-xs font-semibold">
                                {crop}
                              </span>
                            ))
                          ) : (
                            <span className="text-xs text-gray-400 italic">None selected</span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4 font-semibold text-xs">
                        {g.role === "admin" ? (
                          <span className="text-amber-700 bg-amber-50 px-2.5 py-1 rounded-full inline-flex items-center gap-1 border border-amber-200">
                            <ShieldCheck size={14} /> Admin
                          </span>
                        ) : (
                          <span className="text-slate-600 bg-slate-100 px-2.5 py-1 rounded-full inline-flex items-center gap-1">
                            <UserCheck size={14} /> Gardener
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-4 text-right">
                        <button
                          onClick={() => toggleAdminRole(g.id, g.role)}
                          className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-gray-200 hover:bg-gray-100 transition-colors"
                        >
                          {g.role === "admin" ? "Demote to Gardener" : "Promote to Admin"}
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
