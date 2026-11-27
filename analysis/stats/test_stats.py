"""Tests for the T11 analysis scripts: python3 -m unittest analysis/stats/test_stats.py"""
import csv
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adherence  # noqa: E402
import assertion_quality  # noqa: E402
import build_y  # noqa: E402
import detection  # noqa: E402
import g3  # noqa: E402


class Detection(unittest.TestCase):
    def test_sign_flip_exact(self):
        p, how = detection.sign_flip([1, 1, 1, 1, 1, 1, 1])
        self.assertAlmostEqual(p, 2 / 2 ** 7)  # smallest exact two-sided p with 7 consistent differences
        self.assertIn("exact", how)
        self.assertEqual(detection.sign_flip([0, 0])[0], 1.0)

    def test_holm(self):
        h = detection.holm({"RQ1": 0.01, "RQ2": 0.04})
        self.assertTrue(h["RQ1"]["significant"])
        self.assertTrue(h["RQ2"]["significant"])  # 0.04 <= 0.05/1
        h = detection.holm({"RQ1": 0.03, "RQ2": 0.04})
        self.assertFalse(h["RQ1"]["significant"])  # 0.03 > 0.025
        self.assertFalse(h["RQ2"]["significant"])

    def test_rates_exclude_small_k_and_missing(self):
        data = {("p", "d1"): {(a, s): 1 if a == "C" else 0 for a in "ABC" for s in range(1, 6)},
                ("p", "d2"): {(a, s): (None if s <= 3 else 0) for a in "ABC" for s in range(1, 6)}}
        p, excl = detection.rates(data, ["A", "B", "C"])
        self.assertEqual(list(p), [("p", "d1")])
        self.assertEqual(excl, [(("p", "d2"), 2)])
        self.assertEqual(p[("p", "d1")]["C"], 1.0)

    def test_mcnemar_exact(self):
        data = {("p", f"d{i}"): {("A", 1): 0, ("B", 1): 1} for i in range(9)}
        data[("p", "x")] = {("A", 1): 1, ("B", 1): 0}
        m = detection.mcnemar(data, "A", "B")
        self.assertEqual((m["only_B"], m["only_A"]), (9, 1))
        self.assertLess(m["p"], 0.05)  # 9:1 discordant is significant (protocol illustration)

    def test_end_to_end_and_jaccard(self):
        data = {("pod", f"d{i}"): {(a, s): int(a == "C" and s <= 3) for a in "ABC" for s in range(1, 6)} for i in range(8)}
        r = detection.analyse(data)
        self.assertAlmostEqual(r["comparisons"]["RQ2 C-B"]["mean_D"], 0.6)
        self.assertTrue(r["comparisons"]["RQ2 C-B"]["holm"]["significant"])
        self.assertEqual(r["comparisons"]["RQ1 B-A"]["nonzero"], 0)
        self.assertAlmostEqual(r["A3_cross_seed_jaccard"]["C"], 3 / 9)  # pairs with an empty union (seeds 4, 5) are skipped
        self.assertTrue(detection.wilcoxon([1] * 8) < 0.05)


class BuildY(unittest.TestCase):
    def test_confirmed_only_and_not_evaluable(self):
        d = tempfile.mkdtemp()
        with open(os.path.join(d, "candidates-pod_1.jsonl"), "w") as f:
            f.write(json.dumps({"candidateId": "K1", "arm": "C", "seed": 2}) + "\n")
            f.write(json.dumps({"candidateId": "K2", "arm": "B", "seed": 1}) + "\n")
        for name, rows in {"defects.csv": [["project", "defect", "work"], ["podman", "pod#1", d]],
                           "verdicts.csv": [["candidateId", "verdict"], ["K1", "Confirmed"], ["K2", "Related"]],
                           "ne.csv": [["defect", "arm", "seed"], ["pod#1", "A", 3]]}.items():
            csv.writer(open(os.path.join(d, name), "w", newline="")).writerows(rows)
        rows = build_y.build(*(os.path.join(d, n) for n in ("defects.csv", "verdicts.csv", "ne.csv")), ["A", "B", "C"], [1, 2, 3])
        y = {(r["arm"], r["seed"]): r["y"] for r in rows}
        self.assertEqual(y[("C", 2)], 1)
        self.assertEqual(y[("B", 1)], 0)  # Related is not a detection in the primary analysis
        self.assertEqual(y[("A", 3)], "NA")
        rel = {(r["arm"], r["seed"]): r["y"] for r in build_y.build(*(os.path.join(d, n) for n in ("defects.csv", "verdicts.csv", "ne.csv")), ["B"], [1], related=True)}
        self.assertEqual(rel[("B", 1)], 1)


class Adherence(unittest.TestCase):
    def test_wilson_and_kappa(self):
        lo, hi = adherence.wilson(30, 60)
        self.assertAlmostEqual(lo, 0.377, places=2)
        self.assertAlmostEqual(hi, 0.623, places=2)
        self.assertEqual(adherence.kappa(["Adherent"] * 3, ["Adherent"] * 3), 1.0)
        self.assertLess(adherence.kappa(["Adherent", "Partial"], ["Partial", "Adherent"]), 0)

    def test_sample_is_stratified_excludes_defaults_and_scores(self):
        w = tempfile.mkdtemp()
        tests = json.dumps({"tests": [{"title": f"t{i}", "steps": []} for i in range(3)]})
        with open(os.path.join(w, "calls.jsonl"), "w") as f:
            for arm, strats in (("B", ["B2", "B3", "B5"]), ("C", ["C1", "C4", "C5"])):
                for k in range(30):
                    f.write(json.dumps({"callId": f"x|{arm}|{k}|GET /a", "arm": arm, "response": tests,
                                        "validation": {"schemaValid": True}, "strategies": strats,
                                        "selectionReasons": ["predicate"] * 3}) + "\n")
                f.write(json.dumps({"callId": f"x|{arm}|99|GET /d", "arm": arm, "response": tests, "validation": {"schemaValid": True},
                                    "strategies": [("B1" if arm == "B" else "C9")] * 3, "selectionReasons": ["default: no predicate fired"] * 3}) + "\n")
        out = os.path.join(w, "coding")
        adherence.sample(w, out, 60, 4545)
        key = json.load(open(os.path.join(out, "sample_key.json")))
        self.assertEqual(key["default_slots_excluded"], {"B": 3, "C": 3})
        per = {}
        for k in key["key"].values():
            per.setdefault(k["arm"], {}).setdefault(k["strategy"], 0)
            per[k["arm"]][k["strategy"]] += 1
        self.assertEqual(per["B"], {"B2": 20, "B3": 20, "B5": 20})
        rows = list(csv.DictReader(open(os.path.join(out, "coding_owner.csv"))))
        self.assertEqual(len(rows), 120)
        self.assertEqual(len(list(csv.DictReader(open(os.path.join(out, "coding_second.csv"))))), 36)
        self.assertNotIn("arm", rows[0])
        for r in rows:
            r["code"] = "Adherent" if key["key"][r["sampleId"]]["arm"] == "B" else "Partial"
        with open(os.path.join(out, "coding_owner.csv"), "w", newline="") as f:
            wr = csv.DictWriter(f, list(rows[0])); wr.writeheader(); wr.writerows(rows)
        os.remove(os.path.join(out, "coding_second.csv"))
        sec_rows = [dict(r, code=("Partial" if i % 2 else r["code"])) for i, r in enumerate(rows)]
        with open(os.path.join(out, "coding_second.csv"), "w", newline="") as f:
            wr = csv.DictWriter(f, list(rows[0])); wr.writeheader(); wr.writerows(sec_rows)
        two = adherence.score(out, two_coders=True)
        self.assertLess(two["B"]["share"], 1.0)  # stricter: both must say Adherent
        self.assertEqual(two["B"]["coder1_share"], 1.0)
        os.remove(os.path.join(out, "coding_second.csv"))
        res = adherence.score(out)
        self.assertEqual(res["B"]["share"], 1.0)
        self.assertEqual(res["C"]["share"], 0.0)
        self.assertFalse(res["gate_pass"])


class AssertionQuality(unittest.TestCase):
    def test_shares_per_arm_on_executable_tests(self):
        w = tempfile.mkdtemp()
        with open(os.path.join(w, "calls.jsonl"), "w") as f:
            for arm in "AB":
                f.write(json.dumps({"callId": f"x|{arm}|1|GET /a", "arm": arm, "validation": {"tests": [
                    {"index": 0, "assertionClass": "MEANINGFUL"}, {"index": 1, "assertionClass": "STATUS_ONLY"},
                    {"index": 2, "assertionClass": "MEANINGFUL"}]}}) + "\n")
        with open(os.path.join(w, "exec-run.jsonl"), "w") as f:
            for arm in "AB":
                f.write(json.dumps({"callId": f"x|{arm}|1|GET /a", "record": {"tests": [
                    {"index": 0, "outcome": "PASS"}, {"index": 1, "outcome": "FAIL"}, {"index": 2, "outcome": "INVALID"}]}}) + "\n")
        r = assertion_quality.shares([w], "run")
        self.assertEqual(r["A"]["executable"], 2)  # INVALID is not executable
        self.assertEqual(r["A"]["non_trivial_share"], 0.5)


class G3(unittest.TestCase):
    def test_bars_per_arm(self):
        w = tempfile.mkdtemp()
        with open(os.path.join(w, "calls.jsonl"), "w") as f:
            for k in range(20):
                ok = not (k == 0)  # arm B: 1 of 20 schema-invalid -> 95% passes T1
                for arm in "AB":
                    sv = True if arm == "A" else ok
                    f.write(json.dumps({"callId": f"x|{arm}|{k}|GET /a", "arm": arm, "totalMs": 1000, "fromCache": False,
                                        "validation": {"schemaValid": sv, "tests": [{"index": i, "structurallyValid": i < 2} for i in range(3)] if sv else []}}) + "\n")
        r = g3.g3(w, "day5")
        self.assertEqual(r["G3"], "pending")
        self.assertFalse(r["A"]["pass"]["T2"])  # 2 of 3 valid = 66.7% < 80%
        self.assertTrue(r["B"]["pass"]["T1"])
        with open(os.path.join(w, "exec-day5.jsonl"), "w") as f:
            for k in range(20):
                f.write(json.dumps({"callId": f"x|A|{k}|GET /a", "record": {"tests": [{"index": 0, "outcome": "PASS"}, {"index": 1, "outcome": "INFRA_ERROR"}]}}) + "\n")
        r = g3.g3(w, "day5")
        self.assertEqual(r["A"]["T3"], "20/40 = 50.0%")
        self.assertEqual(r["G3"], "FAIL")


if __name__ == "__main__":
    unittest.main()
