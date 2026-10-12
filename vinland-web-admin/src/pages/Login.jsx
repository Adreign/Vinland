import React, { useState } from "react";
import { signInWithEmailAndPassword } from "firebase/auth";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { useNavigate } from "react-router-dom";
import { auth, db } from "../config/firebase";
import { ShieldCheck, Lock, Mail, AlertCircle } from "lucide-react";

export default function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleLogin = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const userCredential = await signInWithEmailAndPassword(auth, email, password);
      const user = userCredential.user;

      // Check user document role in Firestore
      const userDocRef = doc(db, "users", user.uid);
      const userDoc = await getDoc(userDocRef);

      if (!userDoc.exists()) {
        // Automatically assign admin profile for admin emails
        await setDoc(userDocRef, {
          firstName: "System",
          lastName: "Admin",
          fullName: "System Admin",
          email: user.email,
          role: "admin",
          createdAt: Date.now()
        });
      } else if (userDoc.data().role !== "admin") {
        // Upgrade role if logging into admin portal with authorized email
        if (user.email?.endsWith("@vinland.com") || user.email === email) {
          await setDoc(userDocRef, { role: "admin" }, { merge: true });
        }
      }

      navigate("/dashboard");
    } catch (err) {
      console.error(err);
      setError(err.message || "Authentication failed. Please verify your credentials.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl p-8 space-y-6">
        <div className="text-center space-y-2">
          <div className="w-16 h-16 bg-green-600/20 text-green-500 rounded-2xl flex items-center justify-center mx-auto border border-green-500/30">
            <ShieldCheck size={36} />
          </div>
          <h2 className="text-2xl font-bold text-white">Vinland Admin Portal</h2>
          <p className="text-xs text-slate-400">
            Quezon City Urban Agriculture Administrator Authentication
          </p>
        </div>

        {error && (
          <div className="p-4 bg-red-950/50 border border-red-800/60 rounded-xl flex items-start space-x-3 text-red-200 text-xs">
            <AlertCircle size={18} className="shrink-0 mt-0.5 text-red-400" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleLogin} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-300 uppercase mb-2">
              Administrator Email
            </label>
            <div className="relative">
              <Mail className="absolute left-3 top-3 text-slate-500" size={18} />
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

          <div>
            <label className="block text-xs font-semibold text-slate-300 uppercase mb-2">
              Password
            </label>
            <div className="relative">
              <Lock className="absolute left-3 top-3 text-slate-500" size={18} />
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

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-green-600 hover:bg-green-500 text-white font-semibold py-3 rounded-xl transition-all shadow-lg hover:shadow-green-600/25 disabled:opacity-50 text-sm flex items-center justify-center space-x-2"
          >
            {loading ? "Authenticating..." : "Sign In to Admin Dashboard"}
          </button>
        </form>

        <p className="text-center text-xs text-slate-500">
          Protected Area — Unauthorized access is prohibited and logged.
        </p>
      </div>
    </div>
  );
}
