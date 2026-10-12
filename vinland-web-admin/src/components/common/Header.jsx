import React from "react";
import { useAuth } from "../../context/AuthContext";
import { ShieldCheck, User } from "lucide-react";

export default function Header() {
  const { user, userData } = useAuth();

  return (
    <header className="bg-white border-b border-gray-200 px-8 py-4 flex justify-between items-center shadow-xs">
      <div>
        <h1 className="text-xl font-bold text-gray-800">Administrator Console</h1>
        <p className="text-xs text-gray-500">Quezon City Urban Agriculture Management System</p>
      </div>

      <div className="flex items-center space-x-4">
        <div className="flex items-center space-x-2 px-3 py-1.5 bg-green-50 border border-green-200 rounded-full text-xs font-semibold text-green-800">
          <ShieldCheck size={16} className="text-green-600" />
          <span>Verified Administrator</span>
        </div>

        <div className="flex items-center space-x-3 pl-4 border-l border-gray-200">
          <div className="w-9 h-9 rounded-full bg-slate-800 text-white flex items-center justify-center font-bold text-sm">
            {userData?.firstName ? userData.firstName[0].toUpperCase() : <User size={18} />}
          </div>
          <div className="text-left">
            <p className="text-xs font-bold text-gray-800">{userData?.fullName || "System Admin"}</p>
            <p className="text-xs text-gray-400">{user?.email}</p>
          </div>
        </div>
      </div>
    </header>
  );
}
