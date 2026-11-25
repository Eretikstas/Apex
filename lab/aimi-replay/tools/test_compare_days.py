#!/usr/bin/env python3
"""Synthetic-only unit test for compare_days.py."""
import csv
import math
import tempfile
import unittest
from pathlib import Path
import compare_days


class CompareDaysTest(unittest.TestCase):
    def test_same_synthetic_csv_is_all_similar(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "synthetic.csv"
            with path.open("w", newline="", encoding="utf-8") as stream:
                writer = csv.writer(stream)
                writer.writerow(compare_days.COLS)
                for i in range(2 * 288):
                    minute = i * 5
                    hour = (minute % 1440) / 60.0
                    bg = 115 + 18 * math.sin(2 * math.pi * hour / 24)
                    carbs, insulin = 0.0, 0.04
                    if minute % 1440 in (480, 780, 1140):
                        carbs, insulin = 35.0, 0.59
                    if 180 <= minute % 1440 < 200:
                        bg = 66.0
                    hypo = 6.0 if minute % 1440 == 185 else 0.0
                    writer.writerow([minute, "", f"{bg:.2f}", f"{insulin:.3f}",
                                     f"{carbs:.1f}", f"{hypo:.1f}"])
            report = compare_days.compare(compare_days.load_csv(path), compare_days.load_csv(path))
            verdicts = [line.rsplit("|", 2)[1].strip() for line in report.splitlines()[2:]]
            self.assertTrue(verdicts)
            self.assertTrue(all(v == "**SIMILAR**" for v in verdicts), report)


if __name__ == "__main__":
    unittest.main()
