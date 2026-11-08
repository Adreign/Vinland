import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import ProtectedRoute from "./components/ProtectedRoute";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import AccountManagement from "./pages/AccountManagement";
import CropManagement from "./pages/CropManagement";
import RecommendationManagement from "./pages/RecommendationManagement";
import SoilConditionManagement from "./pages/SoilConditionManagement";
import Reports from "./pages/Reports";
import Settings from "./pages/Settings";
import Notifier from "./components/common/Notifier";

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />

          <Route element={<ProtectedRoute />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/accounts" element={<AccountManagement />} />
            <Route path="/crops" element={<CropManagement />} />
            <Route path="/recommendations" element={<RecommendationManagement />} />
            <Route path="/soil-condition" element={<SoilConditionManagement />} />
            <Route path="/reports" element={<Reports />} />
            <Route path="/settings" element={<Settings />} />
          </Route>

          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
        <Notifier />
      </BrowserRouter>
    </AuthProvider>
  );
}
