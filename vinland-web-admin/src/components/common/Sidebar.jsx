import React from "react";
import { NavLink } from "react-router-dom";
import {
  LayoutDashboard,
  Users,
  Sprout,
  Sliders,
  Layers,
  Activity,
  BarChart3,
  Settings,
  LogOut,
  Crown,
  ShieldCheck
} from "lucide-react";
import { useAuth } from "../../context/AuthContext";

export default function Sidebar() {
  const { logout, isSuperAdmin } = useAuth();

  const navItems = [
    { name: "Dashboard", path: "/dashboard", icon: LayoutDashboard },
    { name: "Account Management", path: "/accounts", icon: Users },
    { name: "Crop Management", path: "/crops", icon: Sprout },
    { name: "Recommendation Mgmt", path: "/recommendations", icon: Sliders },
    { name: "Soil Condition Mgmt", path: "/soil-condition", icon: Layers },
    { name: "System Monitoring", path: "/monitoring", icon: Activity },
    { name: "Analytics & Reports", path: "/reports", icon: BarChart3 },
    { name: "System Settings", path: "/settings", icon: Settings },
  ];

  return (
    <aside className="w-64 bg-slate-900 text-slate-100 flex flex-col h-screen sticky top-0 shadow-xl border-r border-slate-800 shrink-0">
      {/* Brand Header */}
      <div className="p-6 border-b border-slate-800 flex items-center space-x-3">
        <div className="w-10 h-10 rounded-xl bg-green-600 flex items-center justify-center font-bold text-white text-xl shadow-lg shrink-0">
          🌱
        </div>
        <div>
          <h2 className="font-bold text-lg text-white leading-tight">Vinland Admin</h2>
          <div className="flex items-center space-x-1 mt-0.5">
            {isSuperAdmin ? (
              <span className="text-[11px] text-amber-400 font-bold flex items-center gap-1">
                <Crown size={12} className="fill-amber-400" /> Super Admin Portal
              </span>
            ) : (
              <span className="text-[11px] text-green-400 font-medium flex items-center gap-1">
                <ShieldCheck size={12} /> Admin Portal
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Navigation Links */}
      <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                `flex items-center space-x-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all ${
                  isActive
                    ? "bg-green-700 text-white shadow-md font-semibold"
                    : "text-slate-300 hover:bg-slate-800 hover:text-white"
                }`
              }
            >
              <Icon size={18} />
              <span>{item.name}</span>
            </NavLink>
          );
        })}
      </nav>

      {/* Footer Logout */}
      <div className="p-4 border-t border-slate-800 space-y-2">
        <button
          onClick={logout}
          className="w-full flex items-center space-x-3 px-4 py-2.5 rounded-xl text-sm font-medium text-red-400 hover:bg-red-950/40 hover:text-red-300 transition-colors"
        >
          <LogOut size={18} />
          <span>Sign Out</span>
        </button>
      </div>
    </aside>
  );
}
