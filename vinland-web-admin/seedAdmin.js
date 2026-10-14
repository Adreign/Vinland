import { initializeApp } from "firebase/app";
import { getFirestore, doc, setDoc } from "firebase/firestore";

const firebaseConfig = {
  apiKey: "AIzaSyBeFq6Z5vgxYxtuNPONB_4lqpds_1wZ2Hg",
  authDomain: "vinland-b6e0b.firebaseapp.com",
  projectId: "vinland-b6e0b",
  storageBucket: "vinland-b6e0b.firebasestorage.app",
  messagingSenderId: "883116866803",
  appId: "1:883116866803:android:464cfbd421c721a5e38b86"
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

async function createAdminCollection() {
  console.log("Creating 'admins' collection in Firestore database (vinland-b6e0b)...");

  const adminData = {
    firstName: "System",
    lastName: "Administrator",
    fullName: "System Administrator",
    email: "admin@vinland.com",
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

  // Seed 'admins' collection
  await setDoc(doc(db, "admins", "admin_system"), adminData);
  console.log("✅ Created document 'admin_system' in 'admins' collection!");

  // Seed 'users' collection with role='admin'
  await setDoc(doc(db, "users", "admin_system"), adminData, { merge: true });
  console.log("✅ Seeded admin role profile in 'users' collection!");
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
