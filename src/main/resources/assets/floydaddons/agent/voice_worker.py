"""Persistent local OmniVoice synthesis; only model weights are downloaded."""
import contextlib
import hashlib
import json
import os
import sys
import time
from pathlib import Path

model = None
prompts = {}
threads_ready = False


def voice_prompt(model, reference):
    import numpy as np
    import soundfile as sf
    import torch
    from omnivoice import VoiceClonePrompt
    key = hashlib.sha256(Path(reference).read_bytes() + b"omnivoice-0.2.1-10s-v1").hexdigest()
    cache = Path(reference).parent / "omnivoice-prompts"
    cache.mkdir(exist_ok=True)
    prompt_path = cache / (key + ".pt")
    if prompt_path.is_file():
        return VoiceClonePrompt.load(str(prompt_path))
    samples, rate = sf.read(reference, dtype="float32", always_2d=True)
    samples = samples[:rate * 10].mean(axis=1)
    from faster_whisper import WhisperModel
    try:
        recognizer = WhisperModel("small.en", device="cpu", compute_type="int8", cpu_threads=2, local_files_only=True)
    except Exception:
        recognizer = WhisperModel("small.en", device="cpu", compute_type="int8", cpu_threads=2)
    import scipy.signal
    from math import gcd
    divisor = gcd(rate, 16000)
    speech = scipy.signal.resample_poly(samples, 16000 // divisor, rate // divisor)
    segments, _ = recognizer.transcribe(speech, language="en", beam_size=5, vad_filter=True)
    transcript = " ".join(s.text.strip() for s in segments).strip()
    del recognizer
    if not transcript:
        raise ValueError("Voice reference could not be transcribed locally")
    prompt = model.create_voice_clone_prompt(ref_audio=(torch.from_numpy(samples), rate), ref_text=transcript)
    prompt.save(str(prompt_path))
    return prompt


for line in sys.stdin:
    started = time.perf_counter()
    try:
        job = json.loads(line)
        with contextlib.redirect_stdout(sys.stderr):
            import torch
            import soundfile as sf
            if not threads_ready:
                torch.set_num_threads(max(1, min(4, (os.cpu_count() or 4) // 2)))
                torch.set_num_interop_threads(1)
                threads_ready = True
            profile = job.get("profile", "Verity")
            if profile == "Big D Randy":
                profile = "Randy"
            references = dict(zip(("Verity", "Randy", "Tom Pearl"), sys.argv[1:4]))
            if profile not in references:
                raise ValueError("Unknown companion voice profile")
            if model is None:
                from omnivoice import OmniVoice
                model = OmniVoice.from_pretrained("k2-fsa/OmniVoice", device_map="cpu", dtype=torch.float32)
                model.eval()
            if profile not in prompts:
                with torch.inference_mode():
                    prompts[profile] = voice_prompt(model, references[profile])
            audio_seconds = 0
            if not job.get("warmup"):
                text = job.get("text", "").strip()[:6000]
                if not text:
                    raise ValueError("Speech text is empty")
                with torch.inference_mode():
                    audio = model.generate(text=text, voice_clone_prompt=prompts[profile], language_id="en", num_step=16)[0]
                sf.write(job["output"], audio, model.sampling_rate, subtype="PCM_16")
                audio_seconds = len(audio) / model.sampling_rate
        print(json.dumps({"ok": True, "engine": "OmniVoice", "device": "CPU", "elapsedMs": round((time.perf_counter()-started)*1000), "audioSeconds": audio_seconds}), flush=True)
    except Exception as error:
        import traceback
        traceback.print_exc(file=sys.stderr)
        print(json.dumps({"ok": False, "error": "OmniVoice could not generate speech. Check the local runtime and model files.", "detail": type(error).__name__}), flush=True)
