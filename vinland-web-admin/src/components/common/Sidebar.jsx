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
  ShieldCheck,
  X
} from "lucide-react";
import { useAuth } from "../../context/AuthContext";
import { confirmAction } from "./Notifier";

export const NAV_ITEMS = [
  { name: "Dashboard", path: "/dashboard", icon: LayoutDashboard },
  { name: "Accounts", path: "/accounts", icon: Users },
  { name: "Crops", path: "/crops", icon: Sprout },
  { name: "Recommendations", path: "/recommendations", icon: Sliders },
  { name: "Soil Conditions", path: "/soil-condition", icon: Layers },
  { name: "System Monitoring", path: "/monitoring", icon: Activity },
  { name: "Analytics & Reports", path: "/reports", icon: BarChart3 },
  { name: "Settings", path: "/settings", icon: Settings },
];

/** The Vinland leaf, same as the app icon. */
export function LeafLogo({ className = "w-10 h-10" }) {
  return (
    <svg viewBox="18 18 72 72" className={className} aria-hidden="true">
      <rect x="18" y="18" width="72" height="72" rx="18" fill="#163A25" />
      <g transform="translate(54 54) scale(0.85) translate(-54 -54)">
        <path d="M33,75 L29,79" stroke="#9CCC65" strokeWidth="3.5" strokeLinecap="round" />
        <path d="M33,75 C33,49 49,33 75,33 C75,59 59,75 33,75 Z" fill="#9CCC65" />
        <path d="M37,71 L64,44" stroke="#1F4D32" strokeOpacity="0.55" strokeWidth="2.5" strokeLinecap="round" />
      </g>
    </svg>
  );
}

export default function Sidebar({ open, onClose }) {
  const { logout, isSuperAdmin } = useAuth();

  const handleLogout = async () => {
    if (await confirmAction("Sign out of Vinland Admin?", { confirmLabel: "Sign out" })) logout();
  };

  return (
    <>
      {/* Dimmed background behind the menu on phones */}
      <div
        className={`fixed inset-0 z-40 bg-gray-900/40 lg:hidden transition-opacity ${open ? "opacity-100" : "opacity-0 pointer-events-none"}`}
        onClick={onClose}
        aria-hidden="true"
      />

      <aside
        className={`fixed lg:sticky top-0 left-0 z-50 h-screen w-64 shrink-0 bg-slate-900 text-slate-100 flex flex-col
          transition-transform duration-200 lg:translate-x-0 ${open ? "translate-x-0 shadow-2xl" : "-translate-x-full"}`}
      >
        {/* Brand */}
        <div className="px-5 py-6 flex items-center gap-3">
          <LeafLogo />
          <div className="min-w-0">
            <h2 className="font-bold text-lg text-white leading-tight">Vinland Admin</h2>
            {isSuperAdmin ? (
              <span className="text-[11px] text-amber-300 font-semibold flex items-center gap-1 mt-0.5">
                <Crown size={12} className="fill-amber-300" /> Super Admin
              </span>
            ) : (
              <span className="text-[11px] text-slate-200 font-medium flex items-center gap-1 mt-0.5">
                <ShieldCheck size={12} /> Admin
              </span>
            )}
          </div>
          <button
            onClick={onClose}
            className="ml-auto p-1.5 rounded-lg text-slate-200 hover:bg-slate-800 lg:hidden"
            aria-label="Close menu"
          >
            <X size={20} />
          </button>
        </div>

        {/* Links */}
        <nav className="flex-1 px-3 space-y-1 overflow-y-auto">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm transition-colors ${
                    isActive
                      ? "bg-white text-green-800 font-semibold shadow-sm"
                      : "text-slate-200 font-medium hover:bg-slate-800 hover:text-white"
                  }`
                }
              >
                <Icon size={18} />
                <span>{item.name}</span>
              </NavLink>
            );
          })}
        </nav>

        {/* Sign out */}
        <div className="p-3 border-t border-slate-800">
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium text-slate-200 hover:bg-slate-800 hover:text-white transition-colors"
          >
            <LogOut size={18} />
            <span>Sign out</span>
          </button>
        </div>
      </aside>
    </>
  );
}
