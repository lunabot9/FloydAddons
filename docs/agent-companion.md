# Agent companion

Open **Agent** in Floyd's sidebar and enable **Companion**. The feature credits Kirk Client in its description.

New installs use Midnight Silver for the whole main menu, and it appears first in the preset list; saved theme choices are preserved. The companion bubble and expanded conversation use solid backgrounds and colors from the selected main menu theme, with a soft outer shadow. They have no separate appearance controls.

**Stop** cancels the current response and audio without signing out or closing the provider connection. Recognized microphone drafts reveal one word at a time and retain the stable prefix when recognition revises the remaining words.

Choose **Sign in with ChatGPT** or **Sign in with Claude**. These use the official Codex CLI and Claude Code account flows, respectively; the corresponding CLI must be installed. Finish the browser login yourself. Connected screens show the provider name and **Sign out**. The companion keeps its provider profile separate from your usual CLI profile and does not request an API key.

**Open Chat** focuses the local bottom-right conversation. Assign **Open Chat Key** for quicker access. Escape returns to the previous screen; **Show Chat History** keeps a small Companion bubble during gameplay. Opening Minecraft chat expands it smoothly, and closing chat scales it back into the bubble; click its message field before typing. Chat history is saved locally in `config/floydaddons/agent/chat-history.json`. Clear Chat removes that transcript. Messages are sent only to the selected AI provider, not Minecraft server chat. The companion can search the web and read public webpages through its connected provider. It can answer questions, reason, translate and write code as text. It receives one Minecraft screenshot for requested analysis or an enabled occasional Gameplay Comment. It cannot execute local commands or perform game actions. These are companion capabilities, not every tool available in the ChatGPT app. Status and replies stay inside the chat; there is no top bar and no private model reasoning is displayed. Incoming sentences queue for speech while the provider is still writing. Reply text reveals progressively from audio playback position instead of appearing all at once.

Select a microphone from the **Microphone** dropdown. Hold **Push to Talk Key** (Left Alt by default) to record; release it to finalize local Whisper recognition and send the resulting text. The pet and chat show Listening while held. The Mic buttons are replaced by a key hint. **Stop**, disabling Companion, leaving the world, or losing Minecraft focus cancels capture. Recording never starts automatically. Whisper small.en runs locally on CPU with voice-activity detection and English recognition. It shows a revisable live draft while the key is held, both inside the expanded chat and in the listening bubble during gameplay. Silence is rejected; only the final transcript is sent to the provider. Microphone WAV files are temporary and deleted after recognition. The speech runtime includes faster-whisper and downloads its model on first use.

Drag the yellow pet in Minecraft chat, companion chat, or HUD edit. Chat and bubble headings use the selected Pet Model name (Verity or Randy). Its speaking mouth has no teeth. It follows a spring with momentum and rotates around the grab point, swinging back when stopped abruptly, with stronger swivel from faster movement and a damped settle after release. Rotation is limited to 60 degrees left or right, so it cannot flip upside down. Expressions distinguish thinking, listening, speaking, and idle states; speech loudness drives its mouth.

## Voice

**Verity** uses the actor reference locally. The project owner confirmed permission to use JustWhispy's voice; the reference is a 24-second excerpt from <https://www.youtube.com/watch?v=81rTVsZreMM>, with the greeting clip at <https://www.youtube.com/watch?v=qn2l9_KBHpQ> also used during comparison. Responses are synthesized, not prerecorded. Reference audio and reply text are not uploaded to a voice service.

**Natural** is the Verity voice engine: Chatterbox Turbo provides the voice the owner selected after listening to comparison samples. It runs on up to four CPU threads with GPU use disabled. The mod warms the voice engine on enable and loads cached weights without a network check. Short streamed sentences start playback earlier. ChatGPT uses the provider-advertised default model with low reasoning effort for conversation; Claude uses Haiku. Model names are not guessed from size labels, because advertised mini models may be unavailable to the signed-in account. Natural speech still requires local synthesis time, so instant audio is not guaranteed. The pet uses its thinking expression while voice generation is pending, then switches to its talking expression during playback. Model and voice conditioning stay loaded between replies; disabling Companion releases the worker. Chatterbox's native generated-audio watermark is preserved.

The local voice dependencies must already be installed under the current user's `.floydaddons/agent-voice-hq` directory. Use **Test Voice** to check bundled introduction playback. Generating ordinary spoken replies downloads/loads the official ResembleAI Chatterbox Turbo model on first use; subsequent replies reuse the worker. Pet position is controlled by dragging; coordinate fields, the runtime installer button, and the expression preview are not exposed in the menu.

The companion always uses Natural speech with the selected pet's voice. Voice Engine and Windows voice selection controls are removed. Voice Volume is independent of Minecraft audio by default; enable Respect Minecraft Volume to also follow the game's master volume.

During gameplay the compact bubble smoothly grows while listening and displays the live transcript. Spoken reply text also appears there as playback progresses, stays visible for twelve seconds after completion, then shrinks back. Verity gently tilts and bobs while idle; stronger drag impulses and slower damping make movement and release wobble more expressive without exceeding the sideways tilt limit.

Idle movement retains fractional pixels inside the NanoVG pet drawing so slow bobbing does not snap between GUI pixels. Idle motion fades in and out with frame-time-based smoothing when the pet changes activity.

## Randy

Select **Randy** under **Pet Model** to use the purple-cardigan, floral-dress character with glasses and a pumpkin candy pail. The transparent four-frame sprite has idle, thinking, listening, and toothless speaking expressions; speech loudness selects the open-mouth frame. Shared smooth bobbing, tilt, drag momentum, and saved positioning apply to both models. Chat headings, reply labels, and the assistant name use the selected model. Changing models cancels the current response so voices do not mix.

The Natural voice engine automatically chooses the selected pet's voice. Randy now uses a 7.78-second clean spoken excerpt from the user-supplied `Voice Over Sample.mp3.mp3` recording (SoundCloud Voice Over Sample by Robert Hill). The original pitch and timing are preserved; the app synthesizes new replies locally with CPU-only Chatterbox and retains its native generated-audio watermark. Reference audio is not sent to the AI account provider. Verity retains its existing reference and sound.

The sprite was generated with the built-in image generator from the user's outfit reference. Asset: `src/main/resources/assets/floydaddons/agent/big-d-randy.png`. Prompt: four equal columns, matching front-facing chibi adult in purple cardigan, pastel floral dress, round glasses, white sneakers, pumpkin pail; idle / thinking / listening / speaking; transparent background, no labels, no teeth. Voice reference: `randy-original-reference.wav`, user-supplied spoken sample (the filename is retained for existing installations).

Randy renders at 96 GUI pixels tall (Verity remains 58), with matching drag bounds and screen clamping. Its Natural voice is tuned for deeper bass and a warm, relaxed delivery. Switching models starts a fresh provider conversation so the assistant uses the newly selected name.


The former saved Pet Model label `Big D Randy` migrates to `Randy` when settings are loaded. Display names and the Natural voice profile now use Randy. Existing sprite and reference asset filenames remain compatible.

Both models keep their smooth idle tilt and bob during thinking and voice preparation, while retaining the thinking facial expression. Dragging still overrides the ambient idle motion.

## Gameplay analysis on request

Ask **Look at my gameplay**, **Analyze my inventory**, **What am I looking at?**, or use **Analyze Gameplay** in Agent settings. One snapshot of the Minecraft window is sent to the selected connected AI account with that message. Ordinary conversation stays text-only, and requests such as **Do not look at my screen** do not attach a screenshot. Both typed and push-to-talk messages use the same request detection.

This captures Minecraft's framebuffer, including any visible game GUI/HUD, never the desktop or other applications. The frame is resized to a maximum 1280-pixel edge, stored temporarily, and removed after the provider finishes, on Stop, or on error. It is a snapshot rather than a continuous video feed. ChatGPT receives a local-image input through the account app server; Claude receives a PNG image block through its account CLI. The agent can explain visible gameplay but cannot perform game actions.

Switching Pet Model while Companion is enabled in a world plays the selected model's fixed introduction through its own Natural voice and adds it to the conversation. Introductions reveal with speech in the chat/bubble, require no AI request, and respect Speak Replies and Voice Volume. Switching again or pressing Stop cancels the current introduction. On the first world join with Companion enabled after launching Minecraft, the selected model plays its introduction once the game is ready. Later server switches and world rejoins keep the provider connection and conversation, cancel only the current response/recording, and do not replay the join introduction. Model-switch and Test Voice introductions remain available. Choosing the already selected model does not replay an introduction.

Introductions are bundled PCM audio generated ahead of time in each pet's Natural voice. Model switches and Test Voice play the same full clip directly, without a provider request, Python worker, model loading, or per-sentence synthesis. Test Voice plays the introduction even when Speak Replies is disabled; automatic model-switch speech respects that toggle. Stop and another model switch cancel playback.

Introduction playback has a dedicated audio executor, so ordinary speech-model warmup cannot delay it. Verity's fixed introduction has shortened sentence breaks (long quiet gaps reduced to roughly 140 ms).

Randy uses a shorter, fluent 1.17-8.95 second section of the supplied reference, without pitch shifting. His Natural sampling uses temperature 0.8 and top-p 0.95 for more varied delivery. The synthesis input expands "I'm" to I am to avoid clipped contraction pronunciation; displayed replies retain the original contraction. Randy has separate warm, relaxed, streetwise conversational instructions; Verity retains her cheerful helper personality. Intro audio remains prerecorded and is not generated during playback.

The companion renders in a final foreground HUD layer, above ordinary HUD text and items. Its pet drawing also starts a new GUI stratum in Minecraft chat, companion chat, and HUD edit.

ChatGPT login uses Codex automatic credential storage: existing OS-keyring logins are preserved, with a provider-owned account/auth.json fallback when Windows cannot store a large token. The mod never reads token contents. The compact bubble shows Not signed in while disconnected.

Chat buttons use the same OneConfig CLICK sound event as the main settings controls, respecting existing UI sound preferences and replacing the vanilla click. On Windows, Codex discovery checks CODEX_CLI_PATH and the installed desktop CLI before PATH/npm fallbacks, so launchers do not silently pick an outdated CLI. Failed replies remain visible as a System message and in the bubble until retry or Stop.

## Tom Pearl

Select **Tom Pearl** as the third Pet Model. The photo-inspired transparent portrait has idle, thinking, listening, and toothless speaking frames. It uses the same 96-pixel height, smooth idle movement, mouth-driven speech animation, and draggable momentum as Randy. Verity and Randy keep their existing settings and voices.

Tom uses a separate local Natural voice profile from the supplied [voice clip](https://www.youtube.com/watch?v=0AtW4M6MWes). The bundled voice reference uses a continuous 5.9-second spoken excerpt without internal cuts or repeated phrases, with Chatterbox Turbo natural sampling (temperature 0.8, top-p 0.95). The greeting is extracted separately for instant playback. Test Voice, model switches, and the first world join per launch play exactly **Hi my name is Tom Pearl**. Later world/server rejoins do not repeat the join introduction.

The sprite was generated with the built-in image generator from the supplied face photo, using the existing companion sprite as a style reference. Prompt: a transparent four-cell front-facing portrait bust with receding dark hair, blue eyes, teal striped shirt, consistent proportions, and idle/thinking/listening/speaking expressions; the final edit removes teeth from the speaking mouth. Saved asset: `src/main/resources/assets/floydaddons/agent/tom-pearl.png`.

Tom Pearl has a fictional gross-out comedy personality centered on an absurd poop-eating obsession. Casual conversation and Minecraft replies include varied, brief callbacks while still answering the question. Health and safety answers remain factual, and the fixed prerecorded introduction stays unchanged.

## Reply latency

All three pets share a pipeline that prepares the next sentence while the previous sentence plays. Reply text remains tied to actual playback. The Natural engine warms the selected voice independently of reply cancellation, and voice conditioning is cached per pet in the same CPU worker. Introductions and normal message starts do not discard an in-progress warmup. Replies begin with a short direct sentence and remain concise unless more detail is requested. Stop cancels current response/audio and discards queued prepared audio; disabling Companion still releases the local models. Voice quality, pitch, and synthesis sampling stay unchanged.

## Randy personality

Randy uses blunt, profane, sarcastic banter and playful gameplay roasts while still answering questions. His style applies only to Randy; Verity and Tom Pearl keep their existing personalities. The prerecorded introduction and voice profile are unchanged.

## Occasional gameplay comments

Gameplay Comments enables occasional snapshot-based reactions from every pet: encouragement from Verity, gameplay roasts from Randy, and absurd poop/diarrhea comparisons from fictional Tom Pearl. Each comment shares one current Minecraft framebuffer with the connected AI account and uses the existing speech and chat bubble. It never captures the desktop. No artificial user message is added to local chat history.

Comment Interval defaults to about three minutes (adjustable from one to ten), with randomized timing. Comments require new movement, camera, held-item, or health activity and an active game window. They pause during menus, chat, microphone capture, transcription, existing replies, errors, or disconnection. User messages, Stop, model changes, and world joins reset the cooldown; idle players receive no repeated comments. Disable Gameplay Comments to keep screenshot sharing on request only. These optional requests use the connected provider's account limits.

## Pet conversations

Start Pet Conversation shows Verity, Randy, and Tom Pearl together with labels and lets them speak in turn about Conversation Topic (default: Banter). Each speaker receives the named dialogue so far and their own personality instructions. Only the current speaker animates their mouth; the others keep idling. Each turn uses the existing Natural voice and progressively revealed chat reply. Names in saved history remain attached to the actual speaker.

Conversation Turns defaults to six and is bounded to three through twelve. Each exchange ends at that limit. Stop Pet Conversation, the chat Stop button, a user message or push-to-talk, a model change, or leaving the world cancels the exchange. Menus and losing focus pause new turns. Gameplay comments wait until the conversation ends. Show All Companions controls the group display independently; turning it off also stops the exchange. Pet conversations use the connected provider account and do not automatically share screenshots.

The group display carries fractional idle offsets inside each pet framebuffer, keeping small idle movements and swaying continuous rather than rounding them to whole pixels. Labels remain anchored below the group.

The group shares one NanoVG/PIP render pass, retains subpixel animation, and leaves FPS limits and voice quality untouched. Client shutdown explicitly releases local speech and transcription workers. Golden Aurora skips masked-out curtain and star work without changing shader resolution or visible formulas.
