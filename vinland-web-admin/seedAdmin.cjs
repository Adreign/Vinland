const { initializeApp } = require("firebase/app");
const { getAuth, signInWithEmailAndPassword, createUserWithEmailAndPassword } = require("firebase/auth");
const { getFirestore, doc, setDoc } = require("firebase/firestore");

const firebaseConfig = {
  apiKey: "AIzaSyBeFq6Z5vgxYxtuNPONB_4lqpds_1wZ2Hg",
  authDomain: "vinland-b6e0b.firebaseapp.com",
  projectId: "vinland-b6e0b",
  storageBucket: "vinland-b6e0b.firebasestorage.app",
  messagingSenderId: "883116866803",
  appId: "1:883116866803:android:464cfbd421c721a5e38b86"
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

async function createAdminCollection() {
  const email = "admin@vinland.com";
  const password = "AdminPassword123!";

  console.log("Authenticating administrator credentials with Firebase...");
  let userCredential;
  try {
    userCredential = await signInWithEmailAndPassword(auth, email, password);
    console.log("Existing admin account signed in.");
  } catch (err) {
    console.log("Creating new admin user account in Firebase Auth...");
    userCredential = await createUserWithEmailAndPassword(auth, email, password);
    console.log("Created new admin account in Firebase Auth.");
  }

  const user = userCredential.user;
  console.log("Authenticated User UID:", user.uid);

  const adminData = {
    firstName: "System",
    lastName: "Administrator",
    fullName: "System Administrator",
    email: email,
    role: "admin",
    permissions: [
      "manage_gardeners",
      "manage_crops",
      "manage_recommendations",
      "system_monitoring",
      "view_reports"
    ],
    createdAt: Date.now()
  };

  // Create document in 'admins' collection
  await setDoc(doc(db, "admins", user.uid), adminData);
  console.log("✅ Successfully created document in 'admins' collection with ID:", user.uid);

  // Seed role in 'users' collection
  await setDoc(doc(db, "users", user.uid), adminData, { merge: true });
  console.log("✅ Successfully seeded admin profile in 'users' collection!");
}

createAdminCollection()
  .then(() => {
    console.log("All admin Firestore documents initialized successfully.");
    process.exit(0);
  })
  .catch((err) => {
    console.error("Error creating collection:", err);
    process.exit(1);
  });
