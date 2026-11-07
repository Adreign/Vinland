import React from "react";
import { useLocation } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { Crown, LogOut, Menu, ShieldCheck, User } from "lucide-react";
import { NAV_ITEMS } from "./Sidebar";
import { confirmAction } from "./Notifier";

export default function Header({ onMenu }) {
  const { user, userData, isSuperAdmin, logout } = useAuth();
  const { pathname } = useLocation();
  const page = NAV_ITEMS.find((item) => pathname.startsWith(item.path));

  const handleLogout = async () => {
    if (await confirmAction("Sign out of Vinland Admin?", { confirmLabel: "Sign out" })) logout();
  };

  return (
    <header className="sticky top-0 z-30 bg-white/90 backdrop-blur border-b border-gray-200 px-4 sm:px-6 lg:px-8 py-3 flex items-center gap-3">
      <button
        onClick={onMenu}
        className="p-2 -ml-2 rounded-xl text-gray-700 hover:bg-gray-100 lg:hidden"
        aria-label="Open menu"
      >
        <Menu size={22} />
      </button>

      <div className="min-w-0">
        <h1 className="text-base sm:text-lg font-bold text-gray-900 truncate">{page?.name || "Vinland Admin"}</h1>
        <p className="text-xs text-gray-500 hidden sm:block">Quezon City Urban Agriculture Management</p>
      </div>

      <div className="ml-auto flex items-center gap-3">
        {isSuperAdmin ? (
          <span className="hidden md:flex items-center gap-1.5 px-3 py-1 bg-amber-50 border border-amber-200 rounded-full text-xs font-semibold text-amber-900">
            <Crown size={14} className="text-amber-600 fill-amber-500" /> Super Admin
          </span>
        ) : (
          <span className="hidden md:flex items-center gap-1.5 px-3 py-1 bg-green-50 border border-green-200 rounded-full text-xs font-semibold text-green-800">
            <ShieldCheck size={14} className="text-green-600" /> Admin
          </span>
        )}

        <div className="flex items-center gap-3 md:pl-3 md:border-l border-gray-200">
          <div className="w-9 h-9 rounded-full bg-green-800 text-white flex items-center justify-center font-semibold text-sm shrink-0">
            {userData?.fullName ? userData.fullName[0].toUpperCase() : <User size={18} />}
          </div>
          <div className="hidden sm:block leading-tight">
            <p className="text-xs font-semibold text-gray-900">{userData?.fullName || "Administrator"}</p>
            <p className="text-[11px] text-gray-500">{user?.email}</p>
          </div>
          <button
            onClick={handleLogout}
            title="Sign out"
            aria-label="Sign out"
            className="p-2 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded-xl transition-colors"
          >
            <LogOut size={18} />
          </button>
        </div>
      </div>
    </header>
  );
}
