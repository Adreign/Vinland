import React, { createContext, useContext, useEffect, useState } from "react";
import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signInWithPopup,
  GoogleAuthProvider,
  sendPasswordResetEmail,
  signOut as firebaseSignOut
} from "firebase/auth";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { auth, db } from "../config/firebase";

const AuthContext = createContext();

// Primary Super Admin emails that automatically gain Super Admin role on first sign in
const SUPER_ADMIN_EMAILS = [
  "admin@vinland.com",
  "superadmin@vinland.com",
  "adreignjet2@gmail.com"
];

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [userData, setUserData] = useState(null);
  const [role, setRole] = useState("gardener"); // "super_admin", "admin", "gardener"
  const [isSuperAdmin, setIsSuperAdmin] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Check local admin bypass session
    const localAdminSession = localStorage.getItem("vinland_local_admin_session");
    if (localAdminSession) {
      try {
        const parsed = JSON.parse(localAdminSession);
        setUser({ uid: parsed.uid, email: parsed.email, displayName: parsed.fullName });
        setUserData(parsed);
        const r = parsed.role || "super_admin";
        setRole(r);
        setIsSuperAdmin(r === "super_admin");
        setIsAdmin(r === "super_admin" || r === "admin");
        setLoading(false);
        return;
      } catch (e) {
        localStorage.removeItem("vinland_local_admin_session");
      }
    }

    const unsubscribe = onAuthStateChanged(auth, async (currentUser) => {
      if (currentUser) {
        setUser(currentUser);
        try {
          const userDocRef = doc(db, "users", currentUser.uid);
          const userDoc = await getDoc(userDocRef);

          let userRole = "admin";
          if (SUPER_ADMIN_EMAILS.includes(currentUser.email?.toLowerCase())) {
            userRole = "super_admin";
          }

          if (userDoc.exists()) {
            const data = userDoc.data();
            if (data.role) userRole = data.role;
            setUserData(data);
          } else {
            const defaultData = {
              fullName: currentUser.displayName || "System Administrator",
              email: currentUser.email,
              role: userRole,
              createdAt: new Date().toISOString()
            };
            await setDoc(userDocRef, defaultData, { merge: true });
            setUserData(defaultData);
          }

          setRole(userRole);
          setIsSuperAdmin(userRole === "super_admin");
          setIsAdmin(userRole === "super_admin" || userRole === "admin");
        } catch (e) {
          console.error("Error fetching user profile role:", e);
          const fallbackRole = SUPER_ADMIN_EMAILS.includes(currentUser.email?.toLowerCase()) ? "super_admin" : "admin";
          setRole(fallbackRole);
          setIsSuperAdmin(fallbackRole === "super_admin");
          setIsAdmin(true);
        }
      } else {
        if (!localStorage.getItem("vinland_local_admin_session")) {
          setUser(null);
          setUserData(null);
          setRole("gardener");
          setIsSuperAdmin(false);
          setIsAdmin(false);
        }
      }
      setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  // 1. Login with Email & Password
  const login = async (email, password) => {
    try {
      const res = await signInWithEmailAndPassword(auth, email, password);
      const userDocRef = doc(db, "users", res.user.uid);
      const userDoc = await getDoc(userDocRef);

      let userRole = SUPER_ADMIN_EMAILS.includes(email.toLowerCase()) ? "super_admin" : "admin";

      if (!userDoc.exists()) {
        await setDoc(userDocRef, {
          fullName: res.user.displayName || "System Admin",
          email: res.user.email,
          role: userRole,
          createdAt: new Date().toISOString()
        });
      } else {
        userRole = userDoc.data().role || userRole;
      }

      setRole(userRole);
      setIsSuperAdmin(userRole === "super_admin");
      setIsAdmin(userRole === "super_admin" || userRole === "admin");
      return res;
    } catch (err) {
      if (err.code === "auth/operation-not-allowed") {
        return localAdminBypass(email);
      }
      throw err;
    }
  };

  // 2. Sign Up Admin
  const signup = async (email, password, fullName, targetRole = "super_admin") => {
    try {
      const res = await createUserWithEmailAndPassword(auth, email, password);
      const userDocRef = doc(db, "users", res.user.uid);

      let userRole = targetRole;
      if (SUPER_ADMIN_EMAILS.includes(email.toLowerCase())) {
        userRole = "super_admin";
      }

      const profile = {
        fullName: fullName || "Super Administrator",
        email,
        role: userRole,
        createdAt: new Date().toISOString()
      };
      await setDoc(userDocRef, profile);
      setUserData(profile);
      setRole(userRole);
      setIsSuperAdmin(userRole === "super_admin");
      setIsAdmin(true);
      return res;
    } catch (err) {
      if (err.code === "auth/operation-not-allowed") {
        return localAdminBypass(email, fullName, targetRole);
      }
      throw err;
    }
  };

  // 3. Local Admin Bypass Session
  const localAdminBypass = (email, fullName, targetRole = "super_admin") => {
    const isSuper = SUPER_ADMIN_EMAILS.includes(email?.toLowerCase()) || targetRole === "super_admin";
    const userRole = isSuper ? "super_admin" : "admin";

    const session = {
      uid: `admin_local_${Date.now()}`,
      email: email || "superadmin@vinland.com",
      fullName: fullName || "Super Administrator",
      role: userRole
    };
    localStorage.setItem("vinland_local_admin_session", JSON.stringify(session));
    setUser({ uid: session.uid, email: session.email, displayName: session.fullName });
    setUserData(session);
    setRole(userRole);
    setIsSuperAdmin(isSuper);
    setIsAdmin(true);
    return session;
  };

  // 4. Google Sign-In
  const loginWithGoogle = async () => {
    const provider = new GoogleAuthProvider();
    const res = await signInWithPopup(auth, provider);
    const userDocRef = doc(db, "users", res.user.uid);
    const userDoc = await getDoc(userDocRef);

    let userRole = SUPER_ADMIN_EMAILS.includes(res.user.email?.toLowerCase()) ? "super_admin" : "admin";

    if (!userDoc.exists()) {
      await setDoc(userDocRef, {
        fullName: res.user.displayName || "Google Admin",
        email: res.user.email,
        role: userRole,
        createdAt: new Date().toISOString()
      });
    } else {
      userRole = userDoc.data().role || userRole;
    }

    setRole(userRole);
    setIsSuperAdmin(userRole === "super_admin");
    setIsAdmin(userRole === "super_admin" || userRole === "admin");
    return res;
  };

  const resetPassword = (email) => {
    return sendPasswordResetEmail(auth, email);
  };

  const logout = async () => {
    localStorage.removeItem("vinland_local_admin_session");
    setUser(null);
    setUserData(null);
    setRole("gardener");
    setIsSuperAdmin(false);
    setIsAdmin(false);
    try {
      await firebaseSignOut(auth);
    } catch (e) {}
  };

  return (
    <AuthContext.Provider value={{ user, userData, role, isSuperAdmin, isAdmin, loading, login, signup, localAdminBypass, loginWithGoogle, resetPassword, logout }}>
      {!loading && children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
