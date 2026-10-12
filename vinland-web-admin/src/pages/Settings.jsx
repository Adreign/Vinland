import React from "react";
import { Settings as SettingsIcon, Shield, Database, Bell } from "lucide-react";

export default function Settings() {
  return (
    <div className="p-8 space-y-6 max-w-7xl mx-auto">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">System Settings & Security</h1>
        <p className="text-sm text-gray-500 mt-0.5">
          System operational configurations, administrator security controls, and Firebase platform connectivity.
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-xs p-6 space-y-6">
        <div className="flex items-center space-x-3 pb-4 border-b">
          <Database size={24} className="text-green-700" />
          <div>
            <h3 className="font-bold text-gray-900">Firebase Cloud Database Connection</h3>
            <p className="text-xs text-gray-500">Connected to vinland-b6e0b.firestore.app</p>
          </div>
        </div>

        <div className="flex items-center space-x-3 pb-4 border-b">
          <Shield size={24} className="text-amber-700" />
          <div>
            <h3 className="font-bold text-gray-900">Role-Based Access Control (RBAC)</h3>
            <p className="text-xs text-gray-500">Firestore Security Rules enabled for administrator privileges.</p>
          </div>
        </div>
      </div>
    </div>
  );
}
