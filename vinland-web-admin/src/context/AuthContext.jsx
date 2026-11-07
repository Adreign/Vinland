import React, { createContext, useContext, useEffect, useState } from "react";
import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signInWithPopup,
  GoogleAuthProvider,
  sendPasswordResetEmail,
  signOut as firebaseSignOut
} from "firebase/auth";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { auth, db } from "../config/firebase";

const AuthContext = createContext();

// These emails are always Super Admins, even before they have a profile in Firestore.
const SUPER_ADMIN_EMAILS = [
  "admin@vinland.com",
  "superadmin@vinland.com",
  "adreignjet2@gmail.com"
];

const ADMIN_ROLES = ["admin", "super_admin"];

/** Thrown when someone signs in with an account that isn't an admin (e.g. a gardener). */
export class NotAdminError extends Error {
  constructor() {
    super("This account is not an administrator. Gardeners should use the Vinland mobile app.");
    this.code = "vinland/not-admin";
  }
}

/**
 * Looks up the signed-in user's role. Only "admin" / "super_admin" (or a listed
 * super-admin email) may use the portal. Super-admin emails get a profile created
 * on first sign-in; nobody else is ever promoted automatically.
 */
async function resolveAdminProfile(firebaseUser) {
  const email = firebaseUser.email?.toLowerCase() || "";
  const ref = doc(db, "users", firebaseUser.uid);
  const snap = await getDoc(ref);
  const data = snap.exists() ? snap.data() : null;

  let role = data?.role || null;
  if (SUPER_ADMIN_EMAILS.includes(email)) role = "super_admin";
  if (!ADMIN_ROLES.includes(role)) return null;

  if (!data || data.role !== role) {
    const profile = {
      fullName: data?.fullName || firebaseUser.displayName || "Super Administrator",
      email: firebaseUser.email,
      role,
      ...(data ? {} : { createdAt: new Date().toISOString() })
    };
    await setDoc(ref, profile, { merge: true });
    return { ...data, ...profile };
  }
  return data;
}

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [userData, setUserData] = useState(null);
  const [role, setRole] = useState(null);
  const [loading, setLoading] = useState(true);

  const clear = () => {
    setUser(null);
    setUserData(null);
    setRole(null);
  };

  useEffect(() => {
    // Old versions stored a password-less "bypass" session here; remove it.
    try { localStorage.removeItem("vinland_local_admin_session"); } catch (e) { /* ignore */ }

    const unsubscribe = onAuthStateChanged(auth, async (currentUser) => {
      if (!currentUser) {
        clear();
        setLoading(false);
        return;
      }
      try {
        const profile = await resolveAdminProfile(currentUser);
        if (profile) {
          setUser(currentUser);
          setUserData(profile);
          setRole(profile.role);
        } else {
          clear();
          await firebaseSignOut(auth);
        }
      } catch (e) {
        console.error("Could not load admin profile:", e);
        clear();
      }
      setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  /** Signs in, then makes sure the account is an admin; otherwise signs out and throws NotAdminError. */
  const finishSignIn = async (res) => {
    const profile = await resolveAdminProfile(res.user);
    if (!profile) {
      await firebaseSignOut(auth);
      throw new NotAdminError();
    }
    setUser(res.user);
    setUserData(profile);
    setRole(profile.role);
    return res;
  };

  const login = async (email, password) =>
    finishSignIn(await signInWithEmailAndPassword(auth, email, password));

  const loginWithGoogle = async () =>
    finishSignIn(await signInWithPopup(auth, new GoogleAuthProvider()));

  const resetPassword = (email) => sendPasswordResetEmail(auth, email);

  const logout = async () => {
    clear();
    try {
      await firebaseSignOut(auth);
    } catch (e) { /* already signed out */ }
  };

  const isSuperAdmin = role === "super_admin";
  const isAdmin = ADMIN_ROLES.includes(role);

  return (
    <AuthContext.Provider value={{ user, userData, role, isSuperAdmin, isAdmin, loading, login, loginWithGoogle, resetPassword, logout }}>
      {loading ? (
        <div className="min-h-screen flex items-center justify-center bg-gray-100">
          <div className="text-center space-y-3">
            <div className="w-10 h-10 border-4 border-green-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
            <p className="text-sm text-gray-600">Loading Vinland Admin…</p>
          </div>
        </div>
      ) : children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
