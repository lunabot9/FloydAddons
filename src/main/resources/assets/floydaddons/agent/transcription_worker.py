"""CPU-only local Whisper recognition with VAD and rolling partial drafts."""
import contextlib
import json
import sys
import wave

model = None
for line in sys.stdin:
    try:
        job = json.loads(line)
        with contextlib.redirect_stdout(sys.stderr):
            import numpy as np
            from faster_whisper import WhisperModel
            if model is None:
                try:
                    model = WhisperModel("small.en", device="cpu", compute_type="int8", cpu_threads=2, num_workers=1, local_files_only=True)
                except Exception:
                    model = WhisperModel("small.en", device="cpu", compute_type="int8", cpu_threads=2, num_workers=1)
            text = ""
            if not job.get("warmup"):
                with wave.open(job["audio"], "rb") as wav:
                    if wav.getframerate() != 16000 or wav.getnchannels() != 1 or wav.getsampwidth() != 2:
                        raise ValueError("Expected 16k mono PCM16")
                    audio = np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2").astype(np.float32) / 32768.0
                if len(audio) >= 4800 and float(np.sqrt(np.mean(audio ** 2))) >= .0015:
                    segments, _ = model.transcribe(audio, language="en", beam_size=1 if job.get("partial") else 5,
                        temperature=0, condition_on_previous_text=False, vad_filter=True,
                        vad_parameters={"min_silence_duration_ms": 300, "speech_pad_ms": 120},
                        initial_prompt="Minecraft, Hypixel, SkyBlock, Floyd Addons, Verity.",
                        no_speech_threshold=.6, log_prob_threshold=-1.0)
                    text = " ".join(s.text.strip() for s in segments if s.no_speech_prob < .6 and s.avg_logprob > -1.0).strip()
        print(json.dumps({"ok": True, "text": text}), flush=True)
    except Exception:
        print(json.dumps({"ok": False, "error": "Whisper could not transcribe this recording."}), flush=True)
