import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { ShieldCheck, Lock, Mail, AlertCircle, CheckCircle2, ArrowRight, KeyRound, Globe, Info } from "lucide-react";

export default function Login() {
  const [mode, setMode] = useState("signin"); // "signin" or "reset"
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [firebaseConfigNotice, setFirebaseConfigNotice] = useState(false);
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  const { login, localAdminBypass, loginWithGoogle, resetPassword } = useAuth();
  const navigate = useNavigate();

  // Handle Form Submit
  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    setFirebaseConfigNotice(false);
    setLoading(true);

    try {
      if (mode === "signin") {
        await login(email, password);
        navigate("/dashboard");
      } else if (mode === "reset") {
        await resetPassword(email);
        setSuccess(`Password reset link sent to ${email}. Check your inbox.`);
      }
    } catch (err) {
      console.error("Auth Error:", err);
      if (err.code === "auth/operation-not-allowed") {
        setFirebaseConfigNotice(true);
        setError("Email/Password Sign-In is currently disabled in your Firebase Console.");
      } else if (err.code === "auth/invalid-credential" || err.code === "auth/wrong-password" || err.code === "auth/user-not-found") {
        setError("Invalid administrator email or password.");
      } else {
        setError(err.message || "Authentication failed.");
      }
    } finally {
      setLoading(false);
    }
  };

  // Google Sign-In
  const handleGoogleSignIn = async () => {
    setError("");
    setFirebaseConfigNotice(false);
    setLoading(true);
    try {
      await loginWithGoogle();
      navigate("/dashboard");
    } catch (err) {
      console.error("Google Auth Error:", err);
      if (err.code === "auth/operation-not-allowed") {
        setFirebaseConfigNotice(true);
        setError("Google Sign-In Provider is not enabled in your Firebase Console.");
      } else {
        setError("Google authentication failed: " + err.message);
      }
    } finally {
      setLoading(false);
    }
  };

  // Instant Admin Access Bypass
  const handleDirectAccess = () => {
    localAdminBypass(email || "admin@vinland.com", "System Administrator");
    navigate("/dashboard");
  };

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-slate-900 border border-slate-800 rounded-3xl shadow-2xl p-8 space-y-6">
        {/* Header */}
        <div className="text-center space-y-2">
          <div className="w-16 h-16 bg-green-600/20 text-green-500 rounded-2xl flex items-center justify-center mx-auto border border-green-500/30 shadow-lg">
            <ShieldCheck size={36} />
          </div>
          <h2 className="text-2xl font-bold text-white">Vinland Admin Portal</h2>
          <p className="text-xs text-slate-400">
            Quezon City Urban Agriculture Administrator Portal
          </p>
        </div>

        {/* Firebase Config Guide Notice */}
        {firebaseConfigNotice && (
          <div className="p-4 bg-amber-950/60 border border-amber-800/80 rounded-2xl space-y-2 text-xs text-amber-200">
            <div className="flex items-center space-x-2 font-bold text-amber-400">
              <Info size={18} />
              <span>Firebase Console Setup Required</span>
            </div>
            <p className="text-[11px] leading-relaxed text-amber-200/90">
              In your Firebase Console (<code className="bg-amber-900/60 px-1 rounded text-amber-300">vinland-b6e0b</code>), navigate to:
              <br />
              <strong className="text-white">Authentication → Sign-in method</strong> and enable <strong>Google</strong> & <strong>Email/Password</strong>.
            </p>
            <button
              type="button"
              onClick={handleDirectAccess}
              className="w-full mt-2 bg-amber-600 hover:bg-amber-500 text-white font-bold py-2 rounded-xl text-xs transition-colors flex items-center justify-center gap-1.5"
            >
              <KeyRound size={15} /> Continue to Admin Dashboard (Bypass)
            </button>
          </div>
        )}

        {/* Status Error Alert */}
        {error && !firebaseConfigNotice && (
          <div className="p-4 bg-red-950/60 border border-red-800/80 rounded-xl flex items-start space-x-3 text-red-200 text-xs">
            <AlertCircle size={18} className="shrink-0 mt-0.5 text-red-400" />
            <span>{error}</span>
          </div>
        )}

        {/* Success Alert */}
        {success && (
          <div className="p-4 bg-green-950/60 border border-green-800/80 rounded-xl flex items-start space-x-3 text-green-200 text-xs">
            <CheckCircle2 size={18} className="shrink-0 mt-0.5 text-green-400" />
            <span>{success}</span>
          </div>
        )}

        {/* Main Sign In Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-300 uppercase mb-1.5">
              Administrator Email
            </label>
            <div className="relative">
              <Mail className="absolute left-3.5 top-3 text-slate-500" size={18} />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="admin@vinland.com"
                className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-green-500 focus:ring-1 focus:ring-green-500"
              />
            </div>
          </div>

          {mode !== "reset" && (
            <div>
              <div className="flex justify-between items-center mb-1.5">
                <label className="text-xs font-semibold text-slate-300 uppercase">
                  Password
                </label>
                <button
                  type="button"
                  onClick={() => { setMode("reset"); setError(""); setSuccess(""); setFirebaseConfigNotice(false); }}
                  className="text-xs text-green-400 hover:underline font-medium"
                >
                  Forgot Password?
                </button>
              </div>
              <div className="relative">
                <Lock className="absolute left-3.5 top-3 text-slate-500" size={18} />
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-sm text-slate-100 placeholder-slate-600 focus:outline-none focus:border-green-500 focus:ring-1 focus:ring-green-500"
                />
              </div>
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-green-600 hover:bg-green-500 text-white font-semibold py-3 rounded-xl transition-all shadow-lg hover:shadow-green-600/25 disabled:opacity-50 text-sm flex items-center justify-center space-x-2"
          >
            <span>
              {loading
                ? "Processing..."
                : mode === "signin"
                ? "Sign In to Admin Dashboard"
                : "Send Reset Password Email"}
            </span>
            <ArrowRight size={16} />
          </button>
        </form>

        {/* Google OAuth & Direct Access Options */}
        <div className="space-y-3 pt-3 border-t border-slate-800/80">
          <button
            onClick={handleGoogleSignIn}
            disabled={loading}
            className="w-full bg-slate-950 hover:bg-slate-800 text-slate-200 border border-slate-800 font-semibold py-2.5 rounded-xl transition-all text-xs flex items-center justify-center space-x-2"
          >
            <Globe size={16} className="text-blue-400" />
            <span>Sign In with Google Account</span>
          </button>

          <button
            type="button"
            onClick={handleDirectAccess}
            className="w-full bg-slate-950/60 hover:bg-slate-800 text-slate-400 hover:text-slate-200 font-medium py-2 rounded-xl transition-all text-xs flex items-center justify-center space-x-1.5"
          >
            <KeyRound size={14} className="text-amber-400" />
            <span>Instant Admin Portal Access (Bypass)</span>
          </button>
        </div>

        {/* Security Policy Footnote */}
        <div className="text-center pt-2">
          {mode === "reset" ? (
            <button
              onClick={() => { setMode("signin"); setError(""); setSuccess(""); setFirebaseConfigNotice(false); }}
              className="text-xs text-slate-400 hover:text-white underline"
            >
              ← Back to Sign In
            </button>
          ) : (
            <p className="text-[11px] text-slate-500 leading-tight">
              🔒 Public admin registration is disabled. Admin accounts are managed directly by <span className="text-amber-400 font-semibold">Super Administrators</span> inside Account Management.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
