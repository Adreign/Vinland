import { initializeApp, getApps } from "firebase/app";
import { getAuth, createUserWithEmailAndPassword, sendEmailVerification, signOut } from "firebase/auth";
import { firebaseConfig } from "./firebase";

// A second Firebase connection used only for creating accounts.
// Creating a user on the main connection would sign the admin out and sign in as the new user.
function secondaryAuth() {
  const existing = getApps().find((a) => a.name === "account-creator");
  return getAuth(existing || initializeApp(firebaseConfig, "account-creator"));
}

// Creates an email/password login and sends the verification email.
// Returns the new user's uid.
export async function createAuthAccount(email, password) {
  const auth = secondaryAuth();
  const cred = await createUserWithEmailAndPassword(auth, email, password);
  try {
    await sendEmailVerification(cred.user);
  } catch (e) {
    // The account still exists; the user can ask for a new link from the app.
    console.warn("Verification email failed:", e);
  } finally {
    await signOut(auth);
  }
  return cred.user.uid;
}

// Turns Firebase error codes into plain messages.
export function authErrorMessage(e) {
  switch (e?.code) {
    case "auth/email-already-in-use": return "That email already has an account.";
    case "auth/invalid-email": return "That email address is not valid.";
    case "auth/weak-password": return "Password must be at least 6 characters.";
    case "auth/operation-not-allowed": return "Email/password sign-in is turned off in Firebase.";
    default: return e?.message || "Could not create the account.";
  }
}
