import React from "react";
import { NavLink } from "react-router-dom";
import {
  LayoutDashboard,
  Users,
  Sprout,
  Sliders,
  Activity,
  BarChart3,
  Settings,
  LogOut
} from "lucide-react";
import { useAuth } from "../../context/AuthContext";

export default function Sidebar() {
  const { logout } = useAuth();

  const navItems = [
    { name: "Dashboard", path: "/dashboard", icon: LayoutDashboard },
    { name: "Gardener Accounts", path: "/accounts", icon: Users },
    { name: "Crop Profiles", path: "/crops", icon: Sprout },
    { name: "Recommendation Rules", path: "/recommendations", icon: Sliders },
    { name: "System Monitoring", path: "/monitoring", icon: Activity },
    { name: "Reports & Analytics", path: "/reports", icon: BarChart3 },
    { name: "System Settings", path: "/settings", icon: Settings },
  ];

  return (
    <aside className="w-64 bg-slate-900 text-slate-100 flex flex-col h-screen sticky top-0 shadow-xl border-r border-slate-800">
      {/* Brand Header */}
      <div className="p-6 border-b border-slate-800 flex items-center space-x-3">
        <div className="w-10 h-10 rounded-xl bg-green-600 flex items-center justify-center font-bold text-white text-xl shadow-lg">
          🌱
        </div>
        <div>
          <h2 className="font-bold text-lg text-white leading-tight">Vinland Admin</h2>
          <p className="text-xs text-green-400 font-medium">Agronomic Admin Portal</p>
        </div>
      </div>

      {/* Navigation Links */}
      <nav className="flex-1 p-4 space-y-1.5 overflow-y-auto">
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                `flex items-center space-x-3 px-4 py-3 rounded-lg text-sm font-medium transition-all ${
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
      <div className="p-4 border-t border-slate-800">
        <button
          onClick={logout}
          className="w-full flex items-center space-x-3 px-4 py-3 rounded-lg text-sm font-medium text-red-400 hover:bg-red-950/40 hover:text-red-300 transition-colors"
        >
          <LogOut size={18} />
          <span>Sign Out Admin</span>
        </button>
      </div>
    </aside>
  );
}
