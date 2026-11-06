import type { CropInfo, Reading } from "./data/context";

export interface CropFit {
  crop: string;
  /** 0-100: how well the current reading fits this crop's optimal ranges. */
  score: number;
  /** e.g. "pH 5.2 below 6-7" */
  outOfRange: string[];
}

type Param = { key: keyof CropInfo["ranges"]; label: string; value: number | null };

/**
 * Plain arithmetic, no AI: gives Claude a reliable starting point so it doesn't
 * have to do range math itself. Each parameter scores 1 inside its range and
 * falls to 0 at one range-width (min 1 unit) outside it.
 */
export function scoreCrops(reading: Reading, crops: CropInfo[]): CropFit[] {
  const params: Param[] = [
    { key: "moisture", label: "Moisture", value: reading.moisture },
    { key: "ph", label: "pH", value: reading.ph },
    { key: "nitrogen", label: "Nitrogen", value: reading.nitrogen },
    { key: "phosphorus", label: "Phosphorus", value: reading.phosphorus },
    { key: "potassium", label: "Potassium", value: reading.potassium },
  ];

  const fits = crops.map((crop) => {
    let total = 0;
    let counted = 0;
    const outOfRange: string[] = [];
    for (const p of params) {
      const range = crop.ranges[p.key];
      if (!range || p.value === null) continue;
      const [lo, hi] = range;
      const width = Math.max(hi - lo, 1);
      let s = 1;
      if (p.value < lo) {
        s = Math.max(0, 1 - (lo - p.value) / width);
        outOfRange.push(`${p.label} ${p.value} below ${lo}-${hi}`);
      } else if (p.value > hi) {
        s = Math.max(0, 1 - (p.value - hi) / width);
        outOfRange.push(`${p.label} ${p.value} above ${lo}-${hi}`);
      }
      total += s;
      counted++;
    }
    const score = counted === 0 ? 0 : Math.round((total / counted) * 100);
    return { crop: crop.name, score, outOfRange };
  });

  return fits.sort((a, b) => b.score - a.score || a.crop.localeCompare(b.crop));
}
