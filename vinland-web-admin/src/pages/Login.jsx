import React, { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
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

  const { user, isAdmin, login, loginWithGoogle, resetPassword } = useAuth();
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

  const handleGoogleSignIn = async () => {
    setError("");
    setLoading(true);
    try {
      await loginWithGoogle();
      navigate("/dashboard");
    } catch (err) {
      console.error("Google sign-in error:", err);
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

          {mode === "signin" ? (
            <>
              <div className="flex items-center gap-3 text-xs text-gray-400">
                <span className="flex-1 h-px bg-gray-200" /> or <span className="flex-1 h-px bg-gray-200" />
              </div>
              <button
                onClick={handleGoogleSignIn}
                disabled={loading}
                className="w-full bg-white hover:bg-gray-50 text-gray-800 border border-gray-300 font-semibold py-2.5 rounded-xl transition-colors text-sm flex items-center justify-center gap-2.5 disabled:opacity-60"
              >
                <svg viewBox="0 0 48 48" className="w-[18px] h-[18px]" aria-hidden="true">
                  <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" />
                  <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
                  <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z" />
                  <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" />
                </svg>
                <span>Continue with Google</span>
              </button>
              <p className="text-xs text-gray-500 text-center leading-relaxed">
                Only administrator accounts can sign in. Admins are added by a Super Admin in Accounts.
              </p>
            </>
          ) : (
            <button
              onClick={() => switchMode("signin")}
              className="w-full text-sm text-gray-600 hover:text-gray-900 font-medium"
            >
              ← Back to sign in
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
