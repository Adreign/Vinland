import React, { useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { Lock, Mail, AlertCircle, CheckCircle2, ArrowRight, Eye, EyeOff } from "lucide-react";
import { LeafLogo } from "../components/common/Sidebar";

/** Turns Firebase error codes into plain sentences. */
function friendlyError(err) {
  switch (err?.code) {
    case "vinland/not-admin":
      return err.message;
    case "auth/invalid-credential":
    case "auth/wrong-password":
    case "auth/user-not-found":
    case "auth/invalid-email":
      return "Wrong email or password.";
    case "auth/too-many-requests":
      return "Too many attempts. Please wait a few minutes and try again.";
    case "auth/network-request-failed":
      return "No internet connection. Check your network and try again.";
    case "auth/popup-closed-by-user":
    case "auth/cancelled-popup-request":
      return "";
    case "auth/operation-not-allowed":
      return "This sign-in method is turned off. Enable it in Firebase Console → Authentication → Sign-in method.";
    default:
      return "Sign-in failed. Please try again.";
  }
}

export default function Login() {
  const [mode, setMode] = useState("signin"); // "signin" or "reset"
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  const { user, isAdmin, login, resetPassword } = useAuth();
  const navigate = useNavigate();

  if (user && isAdmin) return <Navigate to="/dashboard" replace />;

  const switchMode = (next) => {
    setMode(next);
    setError("");
    setSuccess("");
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    setLoading(true);
    try {
      if (mode === "signin") {
        await login(email.trim(), password);
        navigate("/dashboard");
      } else {
        await resetPassword(email.trim());
        setSuccess(`If ${email.trim()} has an account, a password reset link is on its way. Check your inbox.`);
      }
    } catch (err) {
      console.error("Sign-in error:", err);
      setError(friendlyError(err));
    } finally {
      setLoading(false);
    }
  };

  const inputCls =
    "w-full bg-white border border-gray-300 rounded-xl pl-10 pr-4 py-2.5 text-sm text-gray-900 placeholder-gray-400 focus:outline-none focus:border-green-600 focus:ring-2 focus:ring-green-600/20";

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      {/* Green band, like the mobile app's login */}
      <div className="bg-slate-900 rounded-b-[2.5rem] px-4 pt-12 pb-24 text-center">
        <LeafLogo className="w-16 h-16 mx-auto rounded-2xl" />
        <h1 className="mt-4 text-3xl font-extrabold text-white tracking-tight">Vinland Admin</h1>
        <p className="mt-1 text-sm text-slate-200">Quezon City Urban Agriculture Management</p>
      </div>

      <div className="flex-1 px-4 -mt-16 pb-10">
        <div className="max-w-md mx-auto bg-white border border-gray-200 rounded-3xl shadow-xl p-6 sm:p-8 space-y-5">
          <div>
            <h2 className="text-xl font-bold text-gray-900">
              {mode === "signin" ? "Welcome back" : "Reset your password"}
            </h2>
            <p className="text-sm text-gray-500 mt-1">
              {mode === "signin"
                ? "Sign in with your administrator account."
                : "Enter your email and we'll send you a reset link."}
            </p>
          </div>

          {error && (
            <div role="alert" className="p-3.5 bg-red-50 border border-red-200 rounded-xl flex items-start gap-2.5 text-red-800 text-sm">
              <AlertCircle size={18} className="shrink-0 mt-0.5 text-red-600" />
              <span>{error}</span>
            </div>
          )}

          {success && (
            <div className="p-3.5 bg-green-50 border border-green-200 rounded-xl flex items-start gap-2.5 text-green-900 text-sm">
              <CheckCircle2 size={18} className="shrink-0 mt-0.5 text-green-600" />
              <span>{success}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1.5">Email</label>
              <div className="relative">
                <Mail className="absolute left-3.5 top-3 text-gray-400" size={18} />
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="admin@vinland.com"
                  className={inputCls}
                />
              </div>
            </div>

            {mode === "signin" && (
              <div>
                <div className="flex justify-between items-center mb-1.5">
                  <label htmlFor="password" className="text-sm font-medium text-gray-700">Password</label>
                  <button
                    type="button"
                    onClick={() => switchMode("reset")}
                    className="text-sm text-green-700 hover:underline font-medium"
                  >
                    Forgot password?
                  </button>
                </div>
                <div className="relative">
                  <Lock className="absolute left-3.5 top-3 text-gray-400" size={18} />
                  <input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    autoComplete="current-password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                    className={`${inputCls} pr-11`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    className="absolute right-2.5 top-2 p-1 text-gray-500 hover:text-gray-800"
                    aria-label={showPassword ? "Hide password" : "Show password"}
                  >
                    {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-green-700 hover:bg-green-800 text-white font-semibold py-3 rounded-xl transition-colors disabled:opacity-60 text-sm flex items-center justify-center gap-2"
            >
              {loading ? (
                <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                <>
                  <span>{mode === "signin" ? "Sign in" : "Send reset link"}</span>
                  <ArrowRight size={16} />
                </>
              )}
            </button>
          </form>

          {mode === "reset" && (
            <button
              onClick={() => switchMode("signin")}
              className="w-full text-sm text-gray-600 hover:text-gray-900 font-medium"
            >
              ← Back to sign in
            </button>
          )}
        </div>

        <p className="max-w-md mx-auto mt-5 text-center text-sm text-gray-600">
          Looking for the mobile app?{" "}
          <Link to="/download" className="text-green-700 font-semibold hover:underline">Download Vinland for Android</Link>
        </p>
      </div>
    </div>
  );
}
