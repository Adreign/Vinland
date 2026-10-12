import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

const firebaseConfig = {
  apiKey: "AIzaSyBeFq6Z5vgxYxtuNPONB_4lqpds_1wZ2Hg",
  authDomain: "vinland-b6e0b.firebaseapp.com",
  projectId: "vinland-b6e0b",
  storageBucket: "vinland-b6e0b.firebasestorage.app",
  messagingSenderId: "883116866803",
  appId: "1:883116866803:android:464cfbd421c721a5e38b86"
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
