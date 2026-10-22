import React from "react";
import { useAuth } from "../../context/AuthContext";
import { ShieldCheck, Crown, User, LogOut } from "lucide-react";

export default function Header() {
  const { user, userData, role, isSuperAdmin } = useAuth();

  return (
    <header className="bg-white border-b border-gray-200 px-8 py-4 flex justify-between items-center shadow-xs">
      <div>
        <h1 className="text-xl font-bold text-gray-800">
          {isSuperAdmin ? "Super Administrator Console" : "Administrator Console"}
        </h1>
        <p className="text-xs text-gray-500">Quezon City Urban Agriculture Management System</p>
      </div>

      <div className="flex items-center space-x-4">
        {/* Tiered Role Badge */}
        {isSuperAdmin ? (
          <div className="flex items-center space-x-1.5 px-3 py-1.5 bg-amber-50 border border-amber-300 rounded-full text-xs font-bold text-amber-900 shadow-2xs">
            <Crown size={16} className="text-amber-600 fill-amber-500" />
            <span>Super Administrator</span>
          </div>
        ) : (
          <div className="flex items-center space-x-1.5 px-3 py-1.5 bg-green-50 border border-green-200 rounded-full text-xs font-semibold text-green-800">
            <ShieldCheck size={16} className="text-green-600" />
            <span>Administrator</span>
          </div>
        )}

        <div className="flex items-center space-x-3 pl-4 border-l border-gray-200">
          <div className={`w-9 h-9 rounded-full text-white flex items-center justify-center font-bold text-sm shadow-xs ${
            isSuperAdmin ? "bg-amber-600" : "bg-slate-900"
          }`}>
            {userData?.fullName ? userData.fullName[0].toUpperCase() : <User size={18} />}
          </div>
          <div className="text-left hidden sm:block">
            <p className="text-xs font-bold text-gray-800">{userData?.fullName || "System Admin"}</p>
            <p className="text-[11px] text-gray-500">{user?.email}</p>
          </div>

          <button
            onClick={() => window.location.reload()}
            title="Refresh Role"
            className="hidden"
          />

          <button
            onClick={() => useAuth().logout()}
            title="Sign Out"
            className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors ml-1"
          >
            <LogOut size={18} />
          </button>
        </div>
      </div>
    </header>
  );
}
