# Floyd performance measurement

`GET /perf?seconds=N` samples frame times for 1–120 seconds. Add `sections=1` only for
attribution; headline A/B numbers must come from runs without sections because probes
add overhead.

- Do not call other client-thread endpoints during a measurement window.
- Keep focus state, world, camera, settings, and warmup conditions constant.
- Set `enableVsync:false` and `maxFps:260` in `options.txt`.
- Run at least three alternating A/B repeats and compare the delta with run-to-run
  spread.
- Treat sections as inclusive. Do not sum a parent with its children.
- Section percentile buckets can be slightly high; maxima and totals are exact.
- Prefer fresh-client per-feature comparisons. Long sessions accumulate GC and thermal
  noise.

Use repo scripts `scripts/perf-protocol.py`, `scripts/perf-arenas.py`, and
`scripts/perf-baseline.py` when available, resolving their paths through `local.md`.
