package gg.floyd.utils.media;

import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

/** Sends standard Windows media keys, which work with Spotify and browser media sessions. */
public final class WindowsMediaKeys {
    public enum Action {
        VOLUME_UP(0xAF),
        VOLUME_DOWN(0xAE),
        NEXT(0xB0),
        PREVIOUS(0xB1),
        PLAY_PAUSE(0xB3);

        private final int virtualKey;

        Action(int virtualKey) {
            this.virtualKey = virtualKey;
        }

        public int virtualKey() {
            return virtualKey;
        }
    }

    private WindowsMediaKeys() {
    }

    public static boolean send(Action action) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return false;
        try {
            WinUser.INPUT[] inputs = (WinUser.INPUT[]) new WinUser.INPUT().toArray(2);
            configure(inputs[0], action.virtualKey, false);
            configure(inputs[1], action.virtualKey, true);
            return User32.INSTANCE.SendInput(new WinDef.DWORD(inputs.length), inputs, inputs[0].size()).intValue() == inputs.length;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void configure(WinUser.INPUT input, int virtualKey, boolean keyUp) {
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType(WinUser.KEYBDINPUT.class);
        input.input.ki = new WinUser.KEYBDINPUT();
        input.input.ki.wVk = new WinDef.WORD(virtualKey);
        input.input.ki.wScan = new WinDef.WORD(0);
        input.input.ki.dwFlags = new WinDef.DWORD(keyUp ? WinUser.KEYBDINPUT.KEYEVENTF_KEYUP : 0);
        input.input.ki.time = new WinDef.DWORD(0);
        input.input.ki.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
        input.write();
    }
}
