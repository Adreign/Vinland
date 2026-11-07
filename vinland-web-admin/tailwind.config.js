/** @type {import('tailwindcss').Config} */

// Vinland palette, matching the mobile app. The standard color names are
// re-mapped so every page picks up the new look without per-page edits:
//   green -> Vinland greens   gray -> warm sage greys   slate -> deep forest greens
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "-apple-system", "Segoe UI", "Roboto", "sans-serif"],
      },
      colors: {
        green: {
          50: "#F1F7EE", 100: "#E1EFDB", 200: "#C6E0BD", 300: "#A3CC96", 400: "#7DB46E",
          500: "#4E9A47", 600: "#2E7D32", 700: "#256B33", 800: "#1F4D32", 900: "#183D28", 950: "#0F2619",
        },
        gray: {
          50: "#F8F9F5", 100: "#F3F5EF", 200: "#E2E7DC", 300: "#CBD3C5", 400: "#8E9C91",
          500: "#6B7A6E", 600: "#5C6B5F", 700: "#3F4D43", 800: "#2A3A2E", 900: "#1C2A20", 950: "#121C15",
        },
        slate: {
          50: "#F1F6F2", 100: "#E3EEE5", 200: "#CDE8D0", 300: "#B2CDB7", 400: "#8FAE96",
          500: "#6E8F76", 600: "#4E7058", 700: "#3A6247", 800: "#2A5A3D", 900: "#1F4D32", 950: "#163A25",
        },
      },
      boxShadow: {
        xs: "0 1px 2px 0 rgb(28 42 32 / 0.05)",
        "2xs": "0 1px 1px 0 rgb(28 42 32 / 0.04)",
      },
    },
  },
  plugins: [],
};
