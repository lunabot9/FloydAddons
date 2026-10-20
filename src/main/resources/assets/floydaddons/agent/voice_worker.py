"""Local, CPU-only Natural voice generation. No chat or reference audio is uploaded."""
import contextlib
import json
import os
import sys

model = None
conditioned_profile = None
conditionals = {}
threads_ready = False
for line in sys.stdin:
    try:
        job = json.loads(line)
        with contextlib.redirect_stdout(sys.stderr):
            import torch
            import scipy.io.wavfile
            if not threads_ready:
                torch.set_num_threads(max(1, min(4, (os.cpu_count() or 4) // 2)))
                torch.set_num_interop_threads(1)
                threads_ready = True
            from chatterbox.tts_turbo import ChatterboxTurboTTS
            if model is None:
                from huggingface_hub import snapshot_download
                try:
                    cached = snapshot_download("ResembleAI/chatterbox-turbo", local_files_only=True,
                        allow_patterns=["*.safetensors", "*.json", "*.txt", "*.pt", "*.model"])
                except Exception:
                    cached = None
                candidate = ChatterboxTurboTTS.from_local(cached, device="cpu") if cached else ChatterboxTurboTTS.from_pretrained(device="cpu")
                model = candidate
            references = {"Verity": sys.argv[1], "Randy": sys.argv[2]}
            if len(sys.argv) > 3:
                references["Tom Pearl"] = sys.argv[3]
            profile = job.get("profile", "Verity")
            if profile == "Big D Randy":
                profile = "Randy"
            if profile not in references:
                raise ValueError("Unknown companion voice profile")
            if profile != conditioned_profile:
                if profile not in conditionals:
                    with torch.inference_mode():
                        model.prepare_conditionals(references[profile])
                    conditionals[profile] = model.conds
                else:
                    model.conds = conditionals[profile]
                conditioned_profile = profile
            if not job.get("warmup"):
                # Retain Chatterbox's native generated-audio watermark.
                with torch.inference_mode():
                    # Expand isolated contractions before synthesis; this avoids a clipped
                    # "I'm" without changing the visible conversation text.
                    import re
                    spoken = re.sub(r"\bI['\u2019]m\b", "I am", job["text"][:6000], flags=re.IGNORECASE)
                    natural_prosody = profile in ("Randy", "Tom Pearl")
                    audio = model.generate(spoken, temperature=.8 if natural_prosody else .7,
                        top_p=.95 if natural_prosody else .9).squeeze()
                samples = (audio.detach().cpu().numpy().clip(-1, 1) * 32767).astype("int16")
                scipy.io.wavfile.write(job["output"], model.sr, samples)
        print(json.dumps({"ok": True}), flush=True)
    except Exception:
        print(json.dumps({"ok": False, "error": "Natural voice could not load. Check the local voice setup and model access."}), flush=True)
