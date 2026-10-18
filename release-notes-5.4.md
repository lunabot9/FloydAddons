# Floyd Addons 5.4

## Added in this release update
- **Tom Pearl** joins Verity and Randy as a third companion, with a photo-inspired pet and separate local Natural voice.
- Four Tom expression frames cover idle, thinking, listening, and speaking. His toothless speaking mouth reacts to voice playback, with the same smooth idle movement and draggable momentum as the other pets.
- **Test Voice**, model switches, and the first world join play the original prerecorded greeting: **Hi my name is Tom Pearl**. Later server/world rejoins do not replay the join introduction.
- Tom's voice uses a continuous recording reference and revised sampling for smoother, less robotic delivery. Existing Verity and Randy voices remain unchanged.

## Original 5.4 changes: Agent companions
- New **Agent** sidebar section with ChatGPT and Claude account sign-in, connection status, and sign-out. Credit: Kirk Client.
- **Verity** and **Randy** pets (now also joined by Tom Pearl) with thinking, listening, speaking, and idle expressions; smooth bobbing, draggable momentum, and limited sideways wobble.
- Theme-matched chat history and a compact bottom-right bubble that expands smoothly for conversation, microphone previews, and spoken replies.
- Natural local speech with text revealed during playback, model-specific voices, independent voice volume, and improved Randy delivery.
- Prerecorded introductions on the first world join per launch, model switches, and Test Voice, with shorter Verity pauses and adjusted pronunciation. Later world/server rejoins do not replay the join intro.
- Hold **Left Alt** to speak, choose a connected microphone, and see live Whisper transcription. Stop cancels the current response without signing out.
- AI connections and conversation survive world/server switches; leaving a world cancels the current response or recording.
- ChatGPT login falls back from Windows credential storage when a token is too large. The bubble shows **Not signed in** when disconnected.
- Updated Codex CLI discovery and account-default model selection fix replies failing immediately after preparation. Reply errors remain visible in chat.
- Web research through the connected provider and **gameplay analysis on request**. Explicit screen-analysis requests attach one Minecraft screenshot; ordinary chat does not capture gameplay.

## Original 5.4 changes: interface
- **Midnight Silver** is now the default for new installations and appears first in the theme list. Existing theme selections remain saved.
- Pets render above HUD elements, including while editing the HUD or using Minecraft chat.
- Chat buttons use the same click sound as the main settings controls.
- Companion chat follows the selected Floyd theme, with smoother open/close transitions and corrected control bounds.

## Release maintenance
- Checked-in release notes now provide this complete summary, and CI preserves existing descriptions when a notes file is absent.
- Updated jars are attached below for all three supported Minecraft versions, with SHA-256 checksums.

## Requirements and downloads
Choose the jar matching Minecraft **26.1**, **26.1.2**, or **26.2**. Fabric API and Fabric Language Kotlin are required.

Agent account sign-in requires the corresponding **Codex CLI** or **Claude Code** installation and an eligible account. Local Natural speech and Whisper transcription require a separately installed Python runtime and model dependencies; those large dependencies are not bundled in these jars. Provider plan limits apply. See [Agent companion documentation](https://github.com/lunabot9/FloydAddons/blob/v5.4/docs/agent-companion.md) for behavior and runtime requirements.

All three targets built successfully and passed their automated checks. The 26.1.2 client was used for live companion, chat, microphone, playback, and gameplay-analysis verification during development, including Tom Pearl intros, synthesized speech, expressions, and HUD dragging; Claude gameplay analysis has not been tested with a signed-in Claude account.
