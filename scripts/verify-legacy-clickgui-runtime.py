#!/usr/bin/env python3
"""Verify the old /fa ClickGUI row semantics against a running client."""

from __future__ import annotations

import argparse
import base64
import importlib.util
import json
import os
import sys
import time
from pathlib import Path
from typing import Any


def load_bridge_helpers() -> Any:
    path = Path(__file__).with_name("verify-live-hypixel-acquisition.py")
    spec = importlib.util.spec_from_file_location("live_hypixel_verifier", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Drive /fa through local-control and verify old module-row click behavior."
    )
    parser.add_argument(
        "--config",
        default=os.environ.get("FLOYD_CONTROL_CONFIG", "run/config/floydaddons/control-bridge.json"),
        help="Path to control-bridge.json. Default: run/config/floydaddons/control-bridge.json",
    )
    parser.add_argument("--host", default=os.environ.get("FLOYD_CONTROL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=None)
    parser.add_argument("--token", default=os.environ.get("FLOYD_CONTROL_TOKEN"))
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument("--json", action="store_true", help="Print machine-readable proof JSON on success.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    bridge = load_bridge_helpers()
    bridge.validate_loopback_host(args.host)
    bridge.validate_timeout(args.timeout)
    port, token = bridge.load_bridge(args)
    client = BridgeClient(bridge, args.host, port, token, args.timeout)

    proof = verify_legacy_clickgui(client, Path(args.config))
    if args.json:
        print(json.dumps(proof, indent=2, sort_keys=True))
    else:
        print("legacy /fa ClickGUI runtime behavior verified")
    return 0


def verify_legacy_clickgui(client: "BridgeClient", runtime_config_path: Path | None = None) -> dict[str, Any]:
    client.post("/action", {"action": "fullscreen", "enabled": True})
    prepare_cape_verifier_assets(runtime_config_path)
    xray_proof = verify_xray_left_click(client)
    xray_blocks_proof = verify_xray_edit_blocks_extra_input(client)
    mob_proof = verify_mob_esp_right_click(client)
    mob_filters_proof = verify_mob_esp_edit_filters_extra_input(client)
    instance_title_proof = verify_instance_title_string_edit(client)
    camera_proof = verify_camera_popup_controls(client)
    hiders_no_armor_proof = verify_hiders_no_armor_selector(client)
    player_size_proof = verify_player_size_selector_and_number(client)
    stalk_target_proof = verify_stalk_target_extra_input(client)
    nick_names_proof = verify_neck_hider_edit_names_extra_input(client)
    cape_proof = verify_cape_image_cycle_and_action(client)
    cone_proof = verify_cone_hat_image_cycle_and_action(client)
    skin_proof = verify_custom_skin_popup_controls(client)
    gui_style_proof = verify_gui_style_color_picker(client)
    render_page_proof = verify_render_page_time_controls(client)
    xray_page_proof = verify_xray_page_controls(client)
    hiders_page_proof = verify_hiders_page_controls(client)
    animations_page_proof = verify_animations_page_controls(client)
    mob_esp_page_proof = verify_mob_esp_page_controls(client)
    mob_esp_filters_page_proof = verify_mob_esp_filters_page_controls(client)
    camera_page_proof = verify_camera_page_controls(client)
    cosmetic_page_proof = verify_cosmetic_page_controls(client)
    skin_page_proof = verify_skin_page_controls(client)
    cape_page_proof = verify_cape_page_controls(client)
    cone_page_proof = verify_cone_hat_page_controls(client)
    player_size_page_proof = verify_player_size_page_controls(client)
    name_mappings_page_proof = verify_name_mappings_page_controls(client)
    hud_layout_proof = verify_hud_edit_layout_extra(client)
    return {
        "screen": client.get("/state").get("screen"),
        "xrayLeftClick": xray_proof,
        "xrayEditBlocksExtraInput": xray_blocks_proof,
        "mobEspRightClick": mob_proof,
        "mobEspEditFiltersExtraInput": mob_filters_proof,
        "instanceTitleStringEdit": instance_title_proof,
        "cameraPopupControls": camera_proof,
        "hidersNoArmorSelector": hiders_no_armor_proof,
        "playerSizeSelectorAndNumber": player_size_proof,
        "stalkTargetExtraInput": stalk_target_proof,
        "neckHiderEditNamesExtraInput": nick_names_proof,
        "capeImageCycleAndAction": cape_proof,
        "coneHatImageCycleAndAction": cone_proof,
        "customSkinPopupControls": skin_proof,
        "guiStyleColorPicker": gui_style_proof,
        "renderPageTimeControls": render_page_proof,
        "xrayPageControls": xray_page_proof,
        "hidersPageControls": hiders_page_proof,
        "animationsPageControls": animations_page_proof,
        "mobEspPageControls": mob_esp_page_proof,
        "mobEspFiltersPageControls": mob_esp_filters_page_proof,
        "cameraPageControls": camera_page_proof,
        "cosmeticPageControls": cosmetic_page_proof,
        "skinPageControls": skin_page_proof,
        "capePageControls": cape_page_proof,
        "coneHatPageControls": cone_page_proof,
        "playerSizePageControls": player_size_page_proof,
        "nameMappingsPageControls": name_mappings_page_proof,
        "hudEditLayoutExtra": hud_layout_proof,
    }


def prepare_cape_verifier_assets(runtime_config_path: Path | None) -> None:
    if runtime_config_path is None:
        return
    config_dir = runtime_config_path.expanduser().resolve().parent
    cape_dir = config_dir / "capes"
    cape_dir.mkdir(parents=True, exist_ok=True)
    png = base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
    )
    for name in ("zz_floyd_verify_a.png", "zz_floyd_verify_b.png"):
        target = cape_dir / name
        if not target.exists():
            target.write_bytes(png)
    cone_dir = config_dir / "cone-hats"
    cone_dir.mkdir(parents=True, exist_ok=True)
    for name in ("zz_floyd_cone_verify_a.png", "zz_floyd_cone_verify_b.png"):
        target = cone_dir / name
        if not target.exists():
            target.write_bytes(png)
    skin_dir = config_dir / "skins"
    skin_dir.mkdir(parents=True, exist_ok=True)
    for name in ("zz_floyd_skin_verify_a.png", "zz_floyd_skin_verify_b.png"):
        target = skin_dir / name
        if not target.exists():
            target.write_bytes(png)


def verify_xray_left_click(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_browser(client)
    initial = bool(nested(state, "render", "xray", "xrayEnabled"))

    if initial:
        click_browser_entry(client, state, "X-Ray", button=0)
        state = open_legacy_browser(client)
        if nested(state, "render", "xray", "xrayEnabled") is not False:
            raise SystemExit("Could not prepare X-Ray disabled state before left-click verification")

    before = bool(nested(state, "render", "xray", "xrayEnabled"))
    click_result = click_browser_entry(client, state, "X-Ray", button=0)
    state = client.get("/state")
    after = bool(nested(state, "render", "xray", "xrayEnabled"))
    popup = require_popup(state, "X-Ray")
    entries = popup_setting_names(popup)
    extras = popup_extra_labels(popup)
    opacity_before = popup_setting_value(popup, "Opacity")
    opacity_fraction = 0.25 if isinstance(opacity_before, (int, float)) and opacity_before > 0.5 else 0.75
    opacity_click = click_popup_number_at_fraction(client, popup, "Opacity", opacity_fraction)
    state = client.get("/state")
    popup_after_opacity = require_popup(state, "X-Ray")
    opacity_after = popup_setting_value(popup_after_opacity, "Opacity")

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append("X-Ray left click was not handled")
    if opacity_click.get("handled") is not True:
        failures.append("X-Ray Opacity slider click was not handled")
    if before is not False or after is not True:
        failures.append(f"X-Ray left click expected false -> true, got {before!r} -> {after!r}")
    if entries != ["Opacity"]:
        failures.append(f"X-Ray popup entries expected ['Opacity'], got {entries!r}")
    if extras != ["Edit Blocks"]:
        failures.append(f"X-Ray popup extras expected ['Edit Blocks'], got {extras!r}")
    if not isinstance(opacity_before, (int, float)) or not isinstance(opacity_after, (int, float)):
        failures.append(f"X-Ray Opacity popup value expected numbers, got {opacity_before!r} -> {opacity_after!r}")
    elif abs(float(opacity_after) - float(opacity_before)) < 0.01:
        failures.append(f"X-Ray Opacity slider click did not change value {opacity_before!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    if isinstance(opacity_before, (int, float)):
        restore_fraction = ((float(opacity_before) - 0.05) / 0.95)
        click_popup_number_at_fraction(client, popup_after_opacity, "Opacity", restore_fraction)

    if initial is False:
        click_browser_entry(client, state, "X-Ray", button=0)

    return {
        "handled": click_result.get("handled"),
        "before": before,
        "after": after,
        "popup": popup.get("displayName"),
        "entries": entries,
        "extras": extras,
        "opacitySliderClick": {
            "handled": opacity_click.get("handled"),
            "before": opacity_before,
            "after": opacity_after,
        },
    }


def verify_xray_edit_blocks_extra_input(client: "BridgeClient") -> dict[str, Any]:
    test_block = "minecraft:barrier"
    state = open_legacy_browser(client)
    original_blocks = xray_opaque_blocks(state)
    if test_block in original_blocks:
        remove_xray_block_via_popup(client, test_block)
        state = open_legacy_browser(client)
        original_blocks = xray_opaque_blocks(state)
        if test_block in original_blocks:
            raise SystemExit(f"Could not prepare X-Ray opaque block state without {test_block!r}")

    click_result = click_browser_entry(client, state, "X-Ray", button=1)
    state = client.get("/state")
    popup = require_popup(state, "X-Ray")
    expand_result = click_popup_extra(client, popup, "Edit Blocks")
    state = client.get("/state")
    popup_after_expand = require_popup(state, "X-Ray")
    expanded_extra = popup_after_expand.get("expandedExtra")
    focus_result = click_xray_block_input(client, popup_after_expand, submit=False)
    type_result = client.post("/type", {"clear": True, "text": test_block, "submit": True})
    time.sleep(0.15)
    state = client.get("/state")
    blocks_after_add = xray_opaque_blocks(state)
    popup_after_add = require_popup(state, "X-Ray")

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append("X-Ray right click for Edit Blocks was not handled")
    if expand_result.get("handled") is not True:
        failures.append("X-Ray Edit Blocks extra expand click was not handled")
    if focus_result.get("handled") is not True:
        failures.append("X-Ray Add Block input focus click was not handled")
    if type_result.get("handled") is not True:
        failures.append("X-Ray Add Block input type submit was not handled")
    if expanded_extra != "XRAY_BLOCKS":
        failures.append(f"X-Ray Edit Blocks extra expected expanded XRAY_BLOCKS, got {expanded_extra!r}")
    if test_block not in blocks_after_add:
        failures.append(f"X-Ray Edit Blocks add expected {test_block!r} in opaque block list, got {blocks_after_add!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    remove_result = click_xray_block_row(client, popup_after_add, test_block, add=False)
    state = client.get("/state")
    blocks_after_remove = xray_opaque_blocks(state)

    failures = []
    if remove_result.get("handled") is not True:
        failures.append("X-Ray opaque block remove click was not handled")
    if test_block in blocks_after_remove:
        failures.append(f"X-Ray Edit Blocks remove expected {test_block!r} absent, got {blocks_after_remove!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    return {
        "handled": click_result.get("handled"),
        "popup": popup.get("displayName"),
        "expandHandled": expand_result.get("handled"),
        "focusHandled": focus_result.get("handled"),
        "typeHandled": type_result.get("handled"),
        "removeHandled": remove_result.get("handled"),
        "expandedExtra": expanded_extra,
        "block": test_block,
        "beforeCount": len(original_blocks),
        "afterAddContains": test_block in blocks_after_add,
        "afterRemoveContains": test_block in blocks_after_remove,
    }


def verify_mob_esp_right_click(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_browser(client)
    before = bool(nested(state, "render", "mobEsp", "enabled"))
    click_result = click_browser_entry(client, state, "Mob ESP", button=1)
    state = client.get("/state")
    after = bool(nested(state, "render", "mobEsp", "enabled"))
    popup = require_popup(state, "Mob ESP")
    entries = popup_setting_names(popup)
    extras = popup_extra_labels(popup)
    expected_entries = ["Tracers", "Hitboxes", "Star Mobs", "Default ESP Color"]
    tracer_before = popup_setting_value(popup, "Tracers")
    tracer_click = click_popup_setting(client, popup, "Tracers")
    state = client.get("/state")
    popup_after_tracer = require_popup(state, "Mob ESP")
    tracer_after = popup_setting_value(popup_after_tracer, "Tracers")
    color_expand = click_popup_setting(client, popup_after_tracer, "Default ESP Color")
    state = client.get("/state")
    popup_after_color_expand = require_popup(state, "Mob ESP")
    chroma_before = popup_setting_chroma(popup_after_color_expand, "Default ESP Color")
    chroma_click = click_popup_color_chroma(client, popup_after_color_expand, "Default ESP Color")
    state = client.get("/state")
    popup_after_chroma = require_popup(state, "Mob ESP")
    chroma_after = popup_setting_chroma(popup_after_chroma, "Default ESP Color")

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append("Mob ESP right click was not handled")
    if tracer_click.get("handled") is not True:
        failures.append("Mob ESP Tracers popup click was not handled")
    if color_expand.get("handled") is not True:
        failures.append("Mob ESP Default ESP Color popup expand click was not handled")
    if chroma_click.get("handled") is not True:
        failures.append("Mob ESP Default ESP Color chroma click was not handled")
    if after != before:
        failures.append(f"Mob ESP right click must not toggle enabled state, got {before!r} -> {after!r}")
    if entries != expected_entries:
        failures.append(f"Mob ESP popup entries expected {expected_entries!r}, got {entries!r}")
    if extras != ["Edit Filters"]:
        failures.append(f"Mob ESP popup extras expected ['Edit Filters'], got {extras!r}")
    if not isinstance(tracer_before, bool) or not isinstance(tracer_after, bool):
        failures.append(f"Mob ESP Tracers popup value expected booleans, got {tracer_before!r} -> {tracer_after!r}")
    elif tracer_after == tracer_before:
        failures.append(f"Mob ESP Tracers popup click did not toggle value {tracer_before!r}")
    if not isinstance(chroma_before, bool) or not isinstance(chroma_after, bool):
        failures.append(f"Mob ESP Default ESP Color chroma expected booleans, got {chroma_before!r} -> {chroma_after!r}")
    elif chroma_after == chroma_before:
        failures.append(f"Mob ESP Default ESP Color chroma click did not toggle value {chroma_before!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    click_popup_color_chroma(client, popup_after_chroma, "Default ESP Color")
    restored_state = client.get("/state")
    click_popup_setting(client, require_popup(restored_state, "Mob ESP"), "Tracers")

    return {
        "handled": click_result.get("handled"),
        "before": before,
        "after": after,
        "popup": popup.get("displayName"),
        "entries": entries,
        "extras": extras,
        "tracersPopupClick": {
            "handled": tracer_click.get("handled"),
            "before": tracer_before,
            "after": tracer_after,
        },
        "defaultEspColorChromaClick": {
            "expandHandled": color_expand.get("handled"),
            "handled": chroma_click.get("handled"),
            "before": chroma_before,
            "after": chroma_after,
        },
    }


def verify_mob_esp_edit_filters_extra_input(client: "BridgeClient") -> dict[str, Any]:
    test_name = "FloydVerifierMob"
    state = open_legacy_browser(client)
    original_names = mob_name_filters(state)
    if test_name in original_names:
        remove_mob_name_filter_via_popup(client, test_name)
        state = open_legacy_browser(client)
        original_names = mob_name_filters(state)
        if test_name in original_names:
            raise SystemExit(f"Could not prepare Mob ESP name filter state without {test_name!r}")

    click_result = click_browser_entry(client, state, "Mob ESP", button=1)
    state = client.get("/state")
    popup = require_popup(state, "Mob ESP")
    expand_result = click_popup_extra(client, popup, "Edit Filters")
    state = client.get("/state")
    popup_after_expand = require_popup(state, "Mob ESP")
    expanded_extra = popup_after_expand.get("expandedExtra")
    focus_result = click_mob_filter_input(client, popup_after_expand, "ADD_MANUAL_NAME", submit=False)
    type_result = client.post("/type", {"clear": True, "text": test_name, "submit": True})
    time.sleep(0.15)
    state = client.get("/state")
    names_after_add = mob_name_filters(state)
    popup_after_add = require_popup(state, "Mob ESP")

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append("Mob ESP right click for Edit Filters was not handled")
    if expand_result.get("handled") is not True:
        failures.append("Mob ESP Edit Filters extra expand click was not handled")
    if focus_result.get("handled") is not True:
        failures.append("Mob ESP Add Name input focus click was not handled")
    if type_result.get("handled") is not True:
        failures.append("Mob ESP Add Name input type submit was not handled")
    if expanded_extra != "MOB_FILTERS":
        failures.append(f"Mob ESP Edit Filters extra expected expanded MOB_FILTERS, got {expanded_extra!r}")
    if test_name not in names_after_add:
        failures.append(f"Mob ESP Edit Filters add expected {test_name!r} in name filters, got {names_after_add!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    remove_result = click_mob_filter_row(client, popup_after_add, test_name, "REMOVE_NAME")
    state = client.get("/state")
    names_after_remove = mob_name_filters(state)

    failures = []
    if remove_result.get("handled") is not True:
        failures.append("Mob ESP name filter remove click was not handled")
    if test_name in names_after_remove:
        failures.append(f"Mob ESP Edit Filters remove expected {test_name!r} absent, got {names_after_remove!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    return {
        "handled": click_result.get("handled"),
        "popup": popup.get("displayName"),
        "expandHandled": expand_result.get("handled"),
        "focusHandled": focus_result.get("handled"),
        "typeHandled": type_result.get("handled"),
        "removeHandled": remove_result.get("handled"),
        "expandedExtra": expanded_extra,
        "name": test_name,
        "beforeCount": len(original_names),
        "afterAddContains": test_name in names_after_add,
        "afterRemoveContains": test_name in names_after_remove,
    }


def verify_instance_title_string_edit(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_browser(client)
    original = nested(state, "render", "core", "windowTitle")
    if not isinstance(original, str):
        raise SystemExit(f"Render core windowTitle expected string, got {original!r}")

    click_result = click_browser_entry(client, state, "Instance Name", button=1)
    state = client.get("/state")
    popup = require_popup(state, "Instance Name")
    entries = popup_setting_names(popup)
    value_before = popup_setting_value(popup, "Instance Title")
    edit_click = click_popup_setting(client, popup, "Instance Title")
    typed_value = "Floyd Runtime Title"
    type_result = client.post("/type", {"clear": True, "text": typed_value, "submit": True})
    time.sleep(0.15)
    state = client.get("/state")
    value_after = nested(state, "render", "core", "windowTitle")

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append("Instance Name right click was not handled")
    if edit_click.get("handled") is not True:
        failures.append("Instance Title popup edit click was not handled")
    if type_result.get("handled") is not True:
        failures.append("Instance Title popup type submit was not handled")
    if entries != ["Instance Title"]:
        failures.append(f"Instance Name popup entries expected ['Instance Title'], got {entries!r}")
    if value_after != typed_value:
        failures.append(f"Instance Title edit expected {typed_value!r}, got {value_after!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_instance_title(client, original)

    return {
        "handled": click_result.get("handled"),
        "popup": popup.get("displayName"),
        "entries": entries,
        "editClickHandled": edit_click.get("handled"),
        "typeHandled": type_result.get("handled"),
        "before": value_before,
        "after": value_after,
    }


def restore_instance_title(client: "BridgeClient", original: str) -> None:
    state = open_legacy_browser(client)
    if original:
        click_browser_entry(client, state, "Instance Name", button=1)
        state = client.get("/state")
        popup = require_popup(state, "Instance Name")
        click_popup_setting(client, popup, "Instance Title")
        client.post("/type", {"clear": True, "text": original, "submit": True})
        time.sleep(0.15)
    else:
        click_browser_entry(client, state, "Instance Name", button=0)
        time.sleep(0.15)


def verify_camera_popup_controls(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_browser(client)
    initial_speed = nested(state, "camera", "features", "freecamSpeed")
    initial_distance = nested(state, "camera", "features", "freelookDistance")
    initial_f5_bools = {
        "Disable Front Cam": nested(state, "camera", "features", "f5", "disableFront"),
        "Disable Back Cam": nested(state, "camera", "features", "f5", "disableBack"),
        "No Third-Person Clipping": nested(state, "camera", "features", "f5", "noClip"),
        "Scrolling Changes Distance": nested(state, "camera", "features", "f5", "scrollEnabled"),
        "Reset F5 Scrolling": nested(state, "camera", "features", "f5", "resetOnToggle"),
    }
    initial_f5_distance = nested(state, "camera", "features", "f5", "distance")
    if not isinstance(initial_speed, (int, float)):
        raise SystemExit(f"Camera freecamSpeed expected number, got {initial_speed!r}")
    if not isinstance(initial_distance, (int, float)):
        raise SystemExit(f"Camera freelookDistance expected number, got {initial_distance!r}")
    bad_f5_bools = {key: value for key, value in initial_f5_bools.items() if not isinstance(value, bool)}
    if bad_f5_bools:
        raise SystemExit(f"Camera f5 booleans expected bools, got {bad_f5_bools!r}")
    if not isinstance(initial_f5_distance, (int, float)):
        raise SystemExit(f"Camera f5.distance expected number, got {initial_f5_distance!r}")

    freecam_click = click_browser_entry(client, state, "Freecam", button=1)
    state = client.get("/state")
    freecam_popup = require_popup(state, "Freecam")
    freecam_entries = popup_setting_names(freecam_popup)
    speed_before = popup_setting_value(freecam_popup, "Speed")
    speed_fraction = 0.25 if isinstance(speed_before, (int, float)) and float(speed_before) > 5.0 else 0.75
    speed_click = click_popup_number_at_fraction(client, freecam_popup, "Speed", speed_fraction)
    state = client.get("/state")
    speed_after = nested(state, "camera", "features", "freecamSpeed")

    state = open_legacy_browser(client)
    freelook_click = click_browser_entry(client, state, "Freelook", button=1)
    state = client.get("/state")
    freelook_popup = require_popup(state, "Freelook")
    freelook_entries = popup_setting_names(freelook_popup)
    distance_before = popup_setting_value(freelook_popup, "Distance")
    distance_fraction = 0.25 if isinstance(distance_before, (int, float)) and float(distance_before) > 10.0 else 0.75
    distance_click = click_popup_number_at_fraction(client, freelook_popup, "Distance", distance_fraction)
    state = client.get("/state")
    distance_after = nested(state, "camera", "features", "freelookDistance")

    state = open_legacy_browser(client)
    f5_click = click_browser_entry(client, state, "F5 Customizer", button=1)
    state = client.get("/state")
    f5_popup = require_popup(state, "F5 Customizer")
    f5_entries = popup_setting_names(f5_popup)
    f5_bool_results: dict[str, dict[str, Any]] = {}
    f5_bool_failure_messages = {
        "Disable Front Cam": "F5 Disable Front Cam click did not toggle value",
        "Disable Back Cam": "F5 Disable Back Cam click did not toggle value",
        "No Third-Person Clipping": "F5 No Third-Person Clipping click did not toggle value",
        "Scrolling Changes Distance": "F5 Scrolling Changes Distance click did not toggle value",
        "Reset F5 Scrolling": "F5 Reset F5 Scrolling click did not toggle value",
    }
    f5_popup_after_toggle = f5_popup
    for label, state_key in (
        ("Disable Front Cam", "disableFront"),
        ("Disable Back Cam", "disableBack"),
        ("No Third-Person Clipping", "noClip"),
        ("Scrolling Changes Distance", "scrollEnabled"),
        ("Reset F5 Scrolling", "resetOnToggle"),
    ):
        before_value = popup_setting_value(f5_popup_after_toggle, label)
        click = click_popup_setting(client, f5_popup_after_toggle, label)
        state = client.get("/state")
        after_value = nested(state, "camera", "features", "f5", state_key)
        f5_bool_results[label] = {"handled": click.get("handled"), "before": before_value, "after": after_value, "stateKey": state_key}
        f5_popup_after_toggle = require_popup(state, "F5 Customizer")
    f5_distance_before = popup_setting_value(f5_popup_after_toggle, "Camera Distance")
    f5_fraction = 0.25 if isinstance(f5_distance_before, (int, float)) and float(f5_distance_before) > 10.0 else 0.75
    f5_distance_click = click_popup_number_at_fraction(client, f5_popup_after_toggle, "Camera Distance", f5_fraction)
    state = client.get("/state")
    f5_distance_after = nested(state, "camera", "features", "f5", "distance")

    failures: list[str] = []
    if freecam_click.get("handled") is not True:
        failures.append("Freecam right click was not handled")
    if speed_click.get("handled") is not True:
        failures.append("Freecam Speed slider click was not handled")
    if freecam_entries != ["Speed"]:
        failures.append(f"Freecam popup entries expected ['Speed'], got {freecam_entries!r}")
    if not isinstance(speed_after, (int, float)) or not isinstance(speed_before, (int, float)) or abs(float(speed_after) - float(speed_before)) < 0.01:
        failures.append(f"Freecam Speed slider click did not change value {speed_before!r} -> {speed_after!r}")
    if freelook_click.get("handled") is not True:
        failures.append("Freelook right click was not handled")
    if distance_click.get("handled") is not True:
        failures.append("Freelook Distance slider click was not handled")
    if freelook_entries != ["Distance"]:
        failures.append(f"Freelook popup entries expected ['Distance'], got {freelook_entries!r}")
    if not isinstance(distance_after, (int, float)) or not isinstance(distance_before, (int, float)) or abs(float(distance_after) - float(distance_before)) < 0.01:
        failures.append(f"Freelook Distance slider click did not change value {distance_before!r} -> {distance_after!r}")
    expected_f5_entries = [
        "Disable Front Cam",
        "Disable Back Cam",
        "No Third-Person Clipping",
        "Scrolling Changes Distance",
        "Reset F5 Scrolling",
        "Camera Distance",
    ]
    if f5_click.get("handled") is not True:
        failures.append("F5 Customizer right click was not handled")
    for label, result in f5_bool_results.items():
        if result.get("handled") is not True:
            failures.append(f"F5 {label} click was not handled")
    if f5_distance_click.get("handled") is not True:
        failures.append("F5 Camera Distance slider click was not handled")
    if f5_entries != expected_f5_entries:
        failures.append(f"F5 Customizer popup entries expected {expected_f5_entries!r}, got {f5_entries!r}")
    for label, initial in initial_f5_bools.items():
        result = f5_bool_results[label]
        if result["before"] != initial or result["after"] == initial:
            failures.append(f"{f5_bool_failure_messages[label]} {initial!r} -> {result['after']!r}")
    if not isinstance(f5_distance_after, (int, float)) or not isinstance(f5_distance_before, (int, float)) or abs(float(f5_distance_after) - float(f5_distance_before)) < 0.01:
        failures.append(f"F5 Camera Distance slider click did not change value {f5_distance_before!r} -> {f5_distance_after!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_camera_number(client, "Freecam", "Speed", initial_speed, 0.1, 10.0)
    restore_camera_number(client, "Freelook", "Distance", initial_distance, 1.0, 20.0)
    state = open_legacy_browser(client)
    click_browser_entry(client, state, "F5 Customizer", button=1)
    state = client.get("/state")
    popup = require_popup(state, "F5 Customizer")
    for label, initial in initial_f5_bools.items():
        current = nested(state, "camera", "features", "f5", f5_bool_results[label]["stateKey"])
        if current != initial:
            click_popup_setting(client, popup, label)
            state = client.get("/state")
            popup = require_popup(state, "F5 Customizer")
    restore_fraction = (float(initial_f5_distance) - 1.0) / 19.0
    click_popup_number_at_fraction(client, popup, "Camera Distance", restore_fraction)
    state = client.get("/state")
    restored_speed = nested(state, "camera", "features", "freecamSpeed")
    restored_distance = nested(state, "camera", "features", "freelookDistance")
    restored_f5_bools = {
        label: nested(state, "camera", "features", "f5", result["stateKey"])
        for label, result in f5_bool_results.items()
    }
    restored_f5_distance = nested(state, "camera", "features", "f5", "distance")
    if (
        not isinstance(restored_speed, (int, float)) or abs(float(restored_speed) - float(initial_speed)) > 0.02 or
        not isinstance(restored_distance, (int, float)) or abs(float(restored_distance) - float(initial_distance)) > 0.02 or
        restored_f5_bools != initial_f5_bools or
        not isinstance(restored_f5_distance, (int, float)) or abs(float(restored_f5_distance) - float(initial_f5_distance)) > 0.02
    ):
        raise SystemExit(
            "Camera restore expected "
            f"{initial_speed!r}/{initial_distance!r}/{initial_f5_bools!r}/{initial_f5_distance!r}, "
            f"got {restored_speed!r}/{restored_distance!r}/{restored_f5_bools!r}/{restored_f5_distance!r}"
        )

    return {
        "freecam": {
            "handled": freecam_click.get("handled"),
            "entries": freecam_entries,
            "speedClick": {"handled": speed_click.get("handled"), "before": speed_before, "after": speed_after},
        },
        "freelook": {
            "handled": freelook_click.get("handled"),
            "entries": freelook_entries,
            "distanceClick": {"handled": distance_click.get("handled"), "before": distance_before, "after": distance_after},
        },
        "f5Customizer": {
            "handled": f5_click.get("handled"),
            "entries": f5_entries,
            "booleanClicks": {label: {"handled": result["handled"], "before": result["before"], "after": result["after"]} for label, result in f5_bool_results.items()},
            "distanceClick": {"handled": f5_distance_click.get("handled"), "before": f5_distance_before, "after": f5_distance_after},
        },
    }


def restore_camera_number(client: "BridgeClient", popup_label: str, setting_name: str, original: float | int, minimum: float, maximum: float) -> None:
    state = open_legacy_browser(client)
    click_browser_entry(client, state, popup_label, button=1)
    state = client.get("/state")
    popup = require_popup(state, popup_label)
    fraction = (float(original) - minimum) / (maximum - minimum)
    click_popup_number_at_fraction(client, popup, setting_name, fraction)
    time.sleep(0.15)


def verify_hiders_no_armor_selector(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_browser(client)
    initial = nested(state, "render", "hiders", "settings", "noArmorMode")
    click_result = click_browser_entry(client, state, "No Armor", button=1)
    state = client.get("/state")
    popup = require_popup(state, "No Armor")
    entries = popup_setting_names(popup)
    options = popup_setting_options(popup, "Target")
    value_before = popup_setting_value(popup, "Target")
    selector_click = click_popup_setting(client, popup, "Target")
    state = client.get("/state")
    popup_after = require_popup(state, "No Armor")
    value_after = popup_setting_value(popup_after, "Target")
    no_armor_after = nested(state, "render", "hiders", "settings", "noArmorMode")

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append("No Armor right click was not handled")
    if selector_click.get("handled") is not True:
        failures.append("No Armor Target selector click was not handled")
    if entries != ["Target"]:
        failures.append(f"No Armor popup entries expected ['Target'], got {entries!r}")
    if options != ["Off", "Self", "Others", "All"]:
        failures.append(f"No Armor Target selector options expected ['Off', 'Self', 'Others', 'All'], got {options!r}")
    if value_before not in options or value_after not in options:
        failures.append(f"No Armor Target selector values expected valid options, got {value_before!r} -> {value_after!r}")
    elif value_after == value_before:
        failures.append(f"No Armor Target selector click did not change value {value_before!r}")
    if no_armor_after != value_after:
        failures.append(f"No Armor state expected {value_after!r}, got {no_armor_after!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_popup_selector(client, "No Armor", "Target", value_before, options)

    if isinstance(initial, str):
        restored = nested(client.get("/state"), "render", "hiders", "settings", "noArmorMode")
        if restored != initial:
            raise SystemExit(f"No Armor Target restore expected {initial!r}, got {restored!r}")

    return {
        "handled": click_result.get("handled"),
        "popup": popup.get("displayName"),
        "entries": entries,
        "options": options,
        "selectorClick": {
            "handled": selector_click.get("handled"),
            "before": value_before,
            "after": value_after,
        },
    }


def verify_player_size_selector_and_number(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_browser(client)
    settings = nested(state, "playerFeatures", "playerSize", "settings")
    if not isinstance(settings, dict):
        raise SystemExit(f"Player Size settings expected object, got {settings!r}")
    initial_target = settings.get("sizeTarget")
    initial_x = settings.get("scaleX")
    initial_y = settings.get("scaleY")
    initial_z = settings.get("scaleZ")
    if not isinstance(initial_target, str):
        raise SystemExit(f"Player Size sizeTarget expected string, got {initial_target!r}")
    bad_scales = {key: value for key, value in {"scaleX": initial_x, "scaleY": initial_y, "scaleZ": initial_z}.items() if not isinstance(value, (int, float))}
    if bad_scales:
        raise SystemExit(f"Player Size scales expected numbers, got {bad_scales!r}")

    click_result = click_browser_entry(client, state, "Player Size", button=1)
    state = client.get("/state")
    popup = require_popup(state, "Player Size")
    entries = popup_setting_names(popup)
    options = popup_setting_options(popup, "Target")
    target_before = popup_setting_value(popup, "Target")
    x_before = popup_setting_value(popup, "X")
    selector_click = click_popup_setting(client, popup, "Target")
    state = client.get("/state")
    popup_after_selector = require_popup(state, "Player Size")
    target_after = popup_setting_value(popup_after_selector, "Target")
    target_state_after = nested(state, "playerFeatures", "playerSize", "settings", "sizeTarget")
    fraction = 0.25 if isinstance(x_before, (int, float)) and float(x_before) > 2.0 else 0.75
    x_click = click_popup_number_at_fraction(client, popup_after_selector, "X", fraction)
    state = client.get("/state")
    popup_after_x = require_popup(state, "Player Size")
    x_after = popup_setting_value(popup_after_x, "X")
    x_state_after = nested(state, "playerFeatures", "playerSize", "settings", "scaleX")
    y_before = popup_setting_value(popup_after_x, "Y")
    y_fraction = 0.25 if isinstance(y_before, (int, float)) and float(y_before) > 2.0 else 0.75
    y_click = click_popup_number_at_fraction(client, popup_after_x, "Y", y_fraction)
    state = client.get("/state")
    popup_after_y = require_popup(state, "Player Size")
    y_after = popup_setting_value(popup_after_y, "Y")
    y_state_after = nested(state, "playerFeatures", "playerSize", "settings", "scaleY")
    z_before = popup_setting_value(popup_after_y, "Z")
    z_fraction = 0.25 if isinstance(z_before, (int, float)) and float(z_before) > 2.0 else 0.75
    z_click = click_popup_number_at_fraction(client, popup_after_y, "Z", z_fraction)
    state = client.get("/state")
    popup_after_z = require_popup(state, "Player Size")
    z_after = popup_setting_value(popup_after_z, "Z")
    z_state_after = nested(state, "playerFeatures", "playerSize", "settings", "scaleZ")
    expected_entries = ["Target", "X", "Y", "Z"]

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append("Player Size right click was not handled")
    if selector_click.get("handled") is not True:
        failures.append("Player Size Target selector click was not handled")
    if x_click.get("handled") is not True:
        failures.append("Player Size X slider click was not handled")
    if y_click.get("handled") is not True:
        failures.append("Player Size Y slider click was not handled")
    if z_click.get("handled") is not True:
        failures.append("Player Size Z slider click was not handled")
    if entries != expected_entries:
        failures.append(f"Player Size popup entries expected {expected_entries!r}, got {entries!r}")
    if options != ["Self", "Real Players", "All"]:
        failures.append(f"Player Size Target selector options expected ['Self', 'Real Players', 'All'], got {options!r}")
    if target_before not in options or target_after not in options:
        failures.append(f"Player Size Target selector values expected valid options, got {target_before!r} -> {target_after!r}")
    elif target_after == target_before:
        failures.append(f"Player Size Target selector click did not change value {target_before!r}")
    if target_state_after != target_after:
        failures.append(f"Player Size state target expected {target_after!r}, got {target_state_after!r}")
    if not isinstance(x_before, (int, float)) or not isinstance(x_after, (int, float)):
        failures.append(f"Player Size X popup value expected numbers, got {x_before!r} -> {x_after!r}")
    elif abs(float(x_after) - float(x_before)) < 0.01:
        failures.append(f"Player Size X slider click did not change value {x_before!r}")
    if not isinstance(x_state_after, (int, float)) or not isinstance(x_after, (int, float)) or abs(float(x_state_after) - float(x_after)) > 0.001:
        failures.append(f"Player Size state scaleX expected {x_after!r}, got {x_state_after!r}")
    if not isinstance(y_before, (int, float)) or not isinstance(y_after, (int, float)):
        failures.append(f"Player Size Y popup value expected numbers, got {y_before!r} -> {y_after!r}")
    elif abs(float(y_after) - float(y_before)) < 0.01:
        failures.append(f"Player Size Y slider click did not change value {y_before!r}")
    if not isinstance(y_state_after, (int, float)) or not isinstance(y_after, (int, float)) or abs(float(y_state_after) - float(y_after)) > 0.001:
        failures.append(f"Player Size state scaleY expected {y_after!r}, got {y_state_after!r}")
    if not isinstance(z_before, (int, float)) or not isinstance(z_after, (int, float)):
        failures.append(f"Player Size Z popup value expected numbers, got {z_before!r} -> {z_after!r}")
    elif abs(float(z_after) - float(z_before)) < 0.01:
        failures.append(f"Player Size Z slider click did not change value {z_before!r}")
    if not isinstance(z_state_after, (int, float)) or not isinstance(z_after, (int, float)) or abs(float(z_state_after) - float(z_after)) > 0.001:
        failures.append(f"Player Size state scaleZ expected {z_after!r}, got {z_state_after!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_popup_selector(client, "Player Size", "Target", target_before, options)
    state = client.get("/state")
    popup = require_popup(state, "Player Size")
    for setting_name, original in (("X", initial_x), ("Y", initial_y), ("Z", initial_z)):
        if isinstance(original, (int, float)):
            restore_fraction = (float(original) + 1.0) / 6.0
            click_popup_number_at_fraction(client, popup, setting_name, restore_fraction)
            state = client.get("/state")
            popup = require_popup(state, "Player Size")
    state = client.get("/state")
    restored_target = nested(state, "playerFeatures", "playerSize", "settings", "sizeTarget")
    restored_x = nested(state, "playerFeatures", "playerSize", "settings", "scaleX")
    restored_y = nested(state, "playerFeatures", "playerSize", "settings", "scaleY")
    restored_z = nested(state, "playerFeatures", "playerSize", "settings", "scaleZ")
    if (
        restored_target != initial_target or
        not isinstance(restored_x, (int, float)) or abs(float(restored_x) - float(initial_x)) > 0.02 or
        not isinstance(restored_y, (int, float)) or abs(float(restored_y) - float(initial_y)) > 0.02 or
        not isinstance(restored_z, (int, float)) or abs(float(restored_z) - float(initial_z)) > 0.02
    ):
        raise SystemExit(
            f"Player Size restore expected {initial_target!r}/{initial_x!r}/{initial_y!r}/{initial_z!r}, "
            f"got {restored_target!r}/{restored_x!r}/{restored_y!r}/{restored_z!r}"
        )

    return {
        "handled": click_result.get("handled"),
        "popup": popup.get("displayName"),
        "entries": entries,
        "options": options,
        "targetSelectorClick": {
            "handled": selector_click.get("handled"),
            "before": target_before,
            "after": target_after,
        },
        "xSliderClick": {
            "handled": x_click.get("handled"),
            "before": x_before,
            "after": x_after,
        },
        "ySliderClick": {
            "handled": y_click.get("handled"),
            "before": y_before,
            "after": y_after,
        },
        "zSliderClick": {
            "handled": z_click.get("handled"),
            "before": z_before,
            "after": z_after,
        },
    }


def verify_stalk_target_extra_input(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_browser(client)
    original = nested(state, "render", "mobEsp", "stalkTarget")
    if not isinstance(original, str):
        raise SystemExit(f"Mob ESP stalkTarget expected string, got {original!r}")

    click_result = click_browser_entry(client, state, "Stalk Player", button=1)
    state = client.get("/state")
    popup = require_popup(state, "Stalk Player")
    entries = popup_setting_names(popup)
    extras = popup_extra_labels(popup)
    expand_result = click_popup_extra(client, popup, "Target:")
    state = client.get("/state")
    popup_after_expand = require_popup(state, "Stalk Player")
    expanded_extra = popup_after_expand.get("expandedExtra")
    focus_result = click_stalk_target_input(client, popup_after_expand, submit=False)
    typed_value = "FloydVerifierTarget"
    type_result = client.post("/type", {"clear": True, "text": typed_value, "submit": True})
    time.sleep(0.15)
    state = client.get("/state")
    value_after = nested(state, "render", "mobEsp", "stalkTarget")
    popup_after_type = require_popup(state, "Stalk Player")
    color_expand = click_popup_setting(client, popup_after_type, "Tracer Color")
    state = client.get("/state")
    popup_after_color_expand = require_popup(state, "Stalk Player")
    chroma_before = popup_setting_chroma(popup_after_color_expand, "Tracer Color")
    chroma_click = click_popup_color_chroma(client, popup_after_color_expand, "Tracer Color")
    state = client.get("/state")
    popup_after_chroma = require_popup(state, "Stalk Player")
    chroma_after = popup_setting_chroma(popup_after_chroma, "Tracer Color")

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append("Stalk Player right click was not handled")
    if expand_result.get("handled") is not True:
        failures.append("Stalk Player Target extra expand click was not handled")
    if focus_result.get("handled") is not True:
        failures.append("Stalk Player Target input focus click was not handled")
    if type_result.get("handled") is not True:
        failures.append("Stalk Player Target input type submit was not handled")
    if color_expand.get("handled") is not True:
        failures.append("Stalk Player Tracer Color popup expand click was not handled")
    if chroma_click.get("handled") is not True:
        failures.append("Stalk Player Tracer Color chroma click was not handled")
    if entries != ["Tracer Color"]:
        failures.append(f"Stalk Player popup entries expected ['Tracer Color'], got {entries!r}")
    if not extras or not isinstance(extras[0], str) or not extras[0].startswith("Target:"):
        failures.append(f"Stalk Player popup extras expected Target row, got {extras!r}")
    if expanded_extra != "STALK_TARGET":
        failures.append(f"Stalk Player Target extra expected expanded STALK_TARGET, got {expanded_extra!r}")
    if value_after != typed_value:
        failures.append(f"Stalk Player Target input expected {typed_value!r}, got {value_after!r}")
    if not isinstance(chroma_before, bool) or not isinstance(chroma_after, bool):
        failures.append(f"Stalk Player Tracer Color chroma expected booleans, got {chroma_before!r} -> {chroma_after!r}")
    elif chroma_after == chroma_before:
        failures.append(f"Stalk Player Tracer Color chroma click did not toggle value {chroma_before!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    click_popup_color_chroma(client, popup_after_chroma, "Tracer Color")
    restore_stalk_target(client, original)

    return {
        "handled": click_result.get("handled"),
        "popup": popup.get("displayName"),
        "entries": entries,
        "extras": extras,
        "expandHandled": expand_result.get("handled"),
        "focusHandled": focus_result.get("handled"),
        "typeHandled": type_result.get("handled"),
        "tracerColorChromaClick": {
            "expandHandled": color_expand.get("handled"),
            "handled": chroma_click.get("handled"),
            "before": chroma_before,
            "after": chroma_after,
        },
        "before": original,
        "after": value_after,
        "expandedExtra": expanded_extra,
        "actionInputAfterSubmit": popup_after_type.get("actionInput"),
    }


def verify_neck_hider_edit_names_extra_input(client: "BridgeClient") -> dict[str, Any]:
    real_name = "FloydVerifierReal"
    fake_name = "FloydVerifierFake"
    state = open_legacy_browser(client)
    original_mappings = nick_name_mappings(state)
    if real_name in original_mappings:
        remove_nick_mapping_via_popup(client, real_name)
        state = open_legacy_browser(client)
        original_mappings = nick_name_mappings(state)
        if real_name in original_mappings:
            raise SystemExit(f"Could not prepare Neck Hider mapping state without {real_name!r}")

    click_result = click_browser_entry(client, state, "Neck Hider", button=1)
    state = client.get("/state")
    popup = require_popup(state, "Neck Hider")
    expand_result = click_popup_extra(client, popup, "Edit Names")
    state = client.get("/state")
    popup_after_expand = require_popup(state, "Neck Hider")
    expanded_extra = popup_after_expand.get("expandedExtra")
    focus_real = click_name_mapping_entry(client, popup_after_expand, "ADD_MANUAL_ORIGINAL")
    type_real = client.post("/type", {"clear": True, "text": real_name, "submit": False})
    time.sleep(0.15)
    state = client.get("/state")
    popup_after_real = require_popup(state, "Neck Hider")
    focus_fake = click_name_mapping_entry(client, popup_after_real, "ADD_MANUAL_FAKE")
    type_fake = client.post("/type", {"clear": True, "text": fake_name, "submit": False})
    time.sleep(0.15)
    state = client.get("/state")
    popup_before_save = require_popup(state, "Neck Hider")
    save_result = click_name_mapping_entry(client, popup_before_save, "ADD_MANUAL_SAVE")
    state = client.get("/state")
    mappings_after_add = nick_name_mappings(state)
    popup_after_add = require_popup(state, "Neck Hider")

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append("Neck Hider right click for Edit Names was not handled")
    if expand_result.get("handled") is not True:
        failures.append("Neck Hider Edit Names extra expand click was not handled")
    if focus_real.get("handled") is not True:
        failures.append("Neck Hider original mapping input focus click was not handled")
    if focus_fake.get("handled") is not True:
        failures.append("Neck Hider fake mapping input focus click was not handled")
    if type_real.get("handled") is not True:
        failures.append("Neck Hider original mapping input type was not handled")
    if type_fake.get("handled") is not True:
        failures.append("Neck Hider fake mapping input type was not handled")
    if save_result.get("handled") is not True:
        failures.append("Neck Hider mapping save click was not handled")
    if expanded_extra != "NAME_MAPPINGS":
        failures.append(f"Neck Hider Edit Names extra expected expanded NAME_MAPPINGS, got {expanded_extra!r}")
    if mappings_after_add.get(real_name) != fake_name:
        failures.append(f"Neck Hider Edit Names add expected {real_name!r}->{fake_name!r}, got {mappings_after_add!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    remove_result = click_name_mapping_entry(client, popup_after_add, "REMOVE", real_name=real_name)
    state = client.get("/state")
    mappings_after_remove = nick_name_mappings(state)

    failures = []
    if remove_result.get("handled") is not True:
        failures.append("Neck Hider mapping remove click was not handled")
    if real_name in mappings_after_remove:
        failures.append(f"Neck Hider Edit Names remove expected {real_name!r} absent, got {mappings_after_remove!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    return {
        "handled": click_result.get("handled"),
        "popup": popup.get("displayName"),
        "expandHandled": expand_result.get("handled"),
        "focusRealHandled": focus_real.get("handled"),
        "focusFakeHandled": focus_fake.get("handled"),
        "typeRealHandled": type_real.get("handled"),
        "typeFakeHandled": type_fake.get("handled"),
        "saveHandled": save_result.get("handled"),
        "removeHandled": remove_result.get("handled"),
        "expandedExtra": expanded_extra,
        "realName": real_name,
        "fakeName": fake_name,
        "beforeCount": len(original_mappings),
        "afterAddValue": mappings_after_add.get(real_name),
        "afterRemoveContains": real_name in mappings_after_remove,
    }


def verify_cape_image_cycle_and_action(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_browser(client)
    selected_before = nested(state, "cosmetics", "cape", "selectedCape")
    available_before = nested(state, "cosmetics", "cape", "availableCapes")
    if not isinstance(selected_before, str):
        raise SystemExit(f"Cape selectedCape expected string, got {selected_before!r}")
    if not isinstance(available_before, list) or not all(isinstance(item, str) for item in available_before):
        raise SystemExit(f"Cape availableCapes expected string list, got {available_before!r}")

    click_result = click_browser_entry(client, state, "Cape", button=1)
    state = client.get("/state")
    popup = require_popup(state, "Cape")
    entries = popup_setting_names(popup)
    image_options = popup_setting_options(popup, "Image")
    popup_before = popup_setting_value(popup, "Image")
    action_click = click_popup_setting(client, popup, "Open Cape Folder")
    state = client.get("/state")
    popup_after_action = require_popup(state, "Cape")
    cycle_click = click_popup_setting(client, popup_after_action, "Image")
    state = client.get("/state")
    popup_after_cycle = require_popup(state, "Cape")
    popup_after = popup_setting_value(popup_after_cycle, "Image")
    selected_after = nested(state, "cosmetics", "cape", "selectedCape")

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append("Cape right click was not handled")
    if action_click.get("handled") is not True:
        failures.append("Cape Open Folder action click was not handled")
    if cycle_click.get("handled") is not True:
        failures.append("Cape Image cycle click was not handled")
    if entries != ["Image", "Open Cape Folder"]:
        failures.append(f"Cape popup entries expected ['Image', 'Open Cape Folder'], got {entries!r}")
    if len(image_options) < 2:
        failures.append(f"Cape Image cycle expected at least two available options, got {image_options!r}")
    if "zz_floyd_verify_a.png" not in image_options or "zz_floyd_verify_b.png" not in image_options:
        failures.append(f"Cape Image options missing verifier PNGs, got {image_options!r}")
    if popup_before not in image_options or popup_after not in image_options:
        failures.append(f"Cape Image values expected valid options, got {popup_before!r} -> {popup_after!r} from {image_options!r}")
    elif popup_after == popup_before:
        failures.append(f"Cape Image cycle click did not change value {popup_before!r}")
    if selected_after != popup_after:
        failures.append(f"Cape selectedCape state expected {popup_after!r}, got {selected_after!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_popup_selector(client, "Cape", "Image", popup_before, image_options)
    restored = nested(client.get("/state"), "cosmetics", "cape", "selectedCape")
    if popup_before in image_options and restored != popup_before:
        raise SystemExit(f"Cape Image restore expected {popup_before!r}, got {restored!r}")

    return {
        "handled": click_result.get("handled"),
        "popup": popup.get("displayName"),
        "entries": entries,
        "optionsContainVerifierPngs": "zz_floyd_verify_a.png" in image_options and "zz_floyd_verify_b.png" in image_options,
        "openFolderHandled": action_click.get("handled"),
        "cycleClick": {
            "handled": cycle_click.get("handled"),
            "before": popup_before,
            "after": popup_after,
        },
    }


def verify_cone_hat_image_cycle_and_action(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_browser(client)
    selected_before = nested(state, "cosmetics", "coneHat", "selectedImage")
    available_before = nested(state, "cosmetics", "coneHat", "availableImages")
    initial_numbers = {
        "Height": nested(state, "cosmetics", "coneHat", "height"),
        "Radius": nested(state, "cosmetics", "coneHat", "radius"),
        "Y Offset": nested(state, "cosmetics", "coneHat", "yOffset"),
        "Rotation": nested(state, "cosmetics", "coneHat", "rotation"),
        "Spin Speed": nested(state, "cosmetics", "coneHat", "rotationSpeed"),
    }
    if not isinstance(selected_before, str):
        raise SystemExit(f"Cone Hat selectedImage expected string, got {selected_before!r}")
    if not isinstance(available_before, list) or not all(isinstance(item, str) for item in available_before):
        raise SystemExit(f"Cone Hat availableImages expected string list, got {available_before!r}")
    bad_numbers = {key: value for key, value in initial_numbers.items() if not isinstance(value, (int, float))}
    if bad_numbers:
        raise SystemExit(f"Cone Hat numeric settings expected numbers, got {bad_numbers!r}")

    click_result = click_browser_entry(client, state, "Cone Hat", button=1)
    state = client.get("/state")
    popup = require_popup(state, "Cone Hat")
    entries = popup_setting_names(popup)
    image_options = popup_setting_options(popup, "Image")
    popup_before = popup_setting_value(popup, "Image")
    number_specs = {
        "Height": (0.1, 1.5),
        "Radius": (0.05, 0.8),
        "Y Offset": (-1.5, 0.5),
        "Rotation": (0.0, 360.0),
        "Spin Speed": (0.0, 360.0),
    }
    number_failure_messages = {
        "Height": "Cone Hat Height slider click did not change value",
        "Radius": "Cone Hat Radius slider click did not change value",
        "Y Offset": "Cone Hat Y Offset slider click did not change value",
        "Rotation": "Cone Hat Rotation slider click did not change value",
        "Spin Speed": "Cone Hat Spin Speed slider click did not change value",
    }
    number_clicks: dict[str, dict[str, Any]] = {}
    active_popup = popup
    for setting_name in number_specs:
        before_value = popup_setting_value(active_popup, setting_name)
        fraction = 0.25 if isinstance(before_value, (int, float)) and float(before_value) > sum(number_specs[setting_name]) / 2.0 else 0.75
        click = click_popup_number_at_fraction(client, active_popup, setting_name, fraction)
        state = client.get("/state")
        active_popup = require_popup(state, "Cone Hat")
        after_value = popup_setting_value(active_popup, setting_name)
        number_clicks[setting_name] = {"handled": click.get("handled"), "before": before_value, "after": after_value}
    action_click = click_popup_setting(client, active_popup, "Open Cone Folder")
    state = client.get("/state")
    popup_after_action = require_popup(state, "Cone Hat")
    cycle_click = click_popup_setting(client, popup_after_action, "Image")
    state = client.get("/state")
    popup_after_cycle = require_popup(state, "Cone Hat")
    popup_after = popup_setting_value(popup_after_cycle, "Image")
    selected_after = nested(state, "cosmetics", "coneHat", "selectedImage")
    expected_entries = ["Height", "Radius", "Y Offset", "Rotation", "Spin Speed", "Image", "Open Cone Folder"]

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append("Cone Hat right click was not handled")
    for setting_name, result in number_clicks.items():
        if result.get("handled") is not True:
            failures.append(f"Cone Hat {setting_name} slider click was not handled")
        if not isinstance(result.get("before"), (int, float)) or not isinstance(result.get("after"), (int, float)):
            failures.append(f"Cone Hat {setting_name} popup value expected numbers, got {result.get('before')!r} -> {result.get('after')!r}")
        elif abs(float(result["after"]) - float(result["before"])) < 0.01:
            failures.append(f"{number_failure_messages[setting_name]} {result['before']!r}")
    if action_click.get("handled") is not True:
        failures.append("Cone Hat Open Folder action click was not handled")
    if cycle_click.get("handled") is not True:
        failures.append("Cone Hat Image cycle click was not handled")
    if entries != expected_entries:
        failures.append(f"Cone Hat popup entries expected {expected_entries!r}, got {entries!r}")
    if len(image_options) < 2:
        failures.append(f"Cone Hat Image cycle expected at least two available options, got {image_options!r}")
    if "zz_floyd_cone_verify_a.png" not in image_options or "zz_floyd_cone_verify_b.png" not in image_options:
        failures.append(f"Cone Hat Image options missing verifier PNGs, got {image_options!r}")
    if popup_before not in image_options or popup_after not in image_options:
        failures.append(f"Cone Hat Image values expected valid options, got {popup_before!r} -> {popup_after!r} from {image_options!r}")
    elif popup_after == popup_before:
        failures.append(f"Cone Hat Image cycle click did not change value {popup_before!r}")
    if selected_after != popup_after:
        failures.append(f"Cone Hat selectedImage state expected {popup_after!r}, got {selected_after!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_popup_selector(client, "Cone Hat", "Image", popup_before, image_options)
    state = client.get("/state")
    popup = require_popup(state, "Cone Hat")
    for setting_name, original in initial_numbers.items():
        minimum, maximum = number_specs[setting_name]
        click_popup_number_at_fraction(client, popup, setting_name, (float(original) - minimum) / (maximum - minimum))
        state = client.get("/state")
        popup = require_popup(state, "Cone Hat")
    restored = nested(state, "cosmetics", "coneHat", "selectedImage")
    restored_numbers = {
        "Height": nested(state, "cosmetics", "coneHat", "height"),
        "Radius": nested(state, "cosmetics", "coneHat", "radius"),
        "Y Offset": nested(state, "cosmetics", "coneHat", "yOffset"),
        "Rotation": nested(state, "cosmetics", "coneHat", "rotation"),
        "Spin Speed": nested(state, "cosmetics", "coneHat", "rotationSpeed"),
    }
    if popup_before in image_options and restored != popup_before:
        raise SystemExit(f"Cone Hat Image restore expected {popup_before!r}, got {restored!r}")
    bad_restore = {
        key: (initial_numbers[key], restored_numbers[key])
        for key in initial_numbers
        if not isinstance(restored_numbers[key], (int, float)) or abs(float(restored_numbers[key]) - float(initial_numbers[key])) > 0.02
    }
    if bad_restore:
        raise SystemExit(f"Cone Hat numeric restore mismatch {bad_restore!r}")

    return {
        "handled": click_result.get("handled"),
        "popup": popup.get("displayName"),
        "entries": entries,
        "optionsContainVerifierPngs": "zz_floyd_cone_verify_a.png" in image_options and "zz_floyd_cone_verify_b.png" in image_options,
        "numberClicks": number_clicks,
        "openFolderHandled": action_click.get("handled"),
        "cycleClick": {
            "handled": cycle_click.get("handled"),
            "before": popup_before,
            "after": popup_after,
        },
    }


def verify_custom_skin_popup_controls(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_browser(client)
    settings = nested(state, "cosmetics", "skin", "settings")
    if not isinstance(settings, dict):
        raise SystemExit(f"Custom Skin settings expected object, got {settings!r}")
    selected_before = settings.get("selectedSkin")
    self_before = settings.get("self")
    others_before = settings.get("others")
    available_before = settings.get("availableSkins")
    if not isinstance(selected_before, str):
        raise SystemExit(f"Custom Skin selectedSkin expected string, got {selected_before!r}")
    if not isinstance(self_before, bool) or not isinstance(others_before, bool):
        raise SystemExit(f"Custom Skin self/others expected booleans, got {self_before!r}/{others_before!r}")
    if not isinstance(available_before, list) or not all(isinstance(item, str) for item in available_before):
        raise SystemExit(f"Custom Skin availableSkins expected string list, got {available_before!r}")

    click_result = click_browser_entry(client, state, "Custom Skin", button=1)
    state = client.get("/state")
    popup = require_popup(state, "Custom Skin")
    entries = popup_setting_names(popup)
    skin_options = popup_setting_options(popup, "Skin")
    popup_before = popup_setting_value(popup, "Skin")
    self_click = click_popup_setting(client, popup, "Self")
    state = client.get("/state")
    popup_after_self = require_popup(state, "Custom Skin")
    self_after = nested(state, "cosmetics", "skin", "settings", "self")
    others_click = click_popup_setting(client, popup_after_self, "Others")
    state = client.get("/state")
    popup_after_others = require_popup(state, "Custom Skin")
    others_after = nested(state, "cosmetics", "skin", "settings", "others")
    action_click = click_popup_setting(client, popup_after_others, "Open Skin Folder")
    state = client.get("/state")
    popup_after_action = require_popup(state, "Custom Skin")
    cycle_click = click_popup_setting(client, popup_after_action, "Skin")
    state = client.get("/state")
    popup_after_cycle = require_popup(state, "Custom Skin")
    popup_after = popup_setting_value(popup_after_cycle, "Skin")
    selected_after = nested(state, "cosmetics", "skin", "settings", "selectedSkin")
    expected_entries = ["Self", "Others", "Skin", "Open Skin Folder"]

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append("Custom Skin right click was not handled")
    if self_click.get("handled") is not True:
        failures.append("Custom Skin Self popup click was not handled")
    if others_click.get("handled") is not True:
        failures.append("Custom Skin Others popup click was not handled")
    if action_click.get("handled") is not True:
        failures.append("Custom Skin Open Folder action click was not handled")
    if cycle_click.get("handled") is not True:
        failures.append("Custom Skin Skin cycle click was not handled")
    if entries != expected_entries:
        failures.append(f"Custom Skin popup entries expected {expected_entries!r}, got {entries!r}")
    if self_after == self_before:
        failures.append(f"Custom Skin Self popup click did not toggle value {self_before!r}")
    if others_after == others_before:
        failures.append(f"Custom Skin Others popup click did not toggle value {others_before!r}")
    if len(skin_options) < 2:
        failures.append(f"Custom Skin cycle expected at least two available options, got {skin_options!r}")
    if "zz_floyd_skin_verify_a.png" not in skin_options or "zz_floyd_skin_verify_b.png" not in skin_options:
        failures.append(f"Custom Skin options missing verifier PNGs, got {skin_options!r}")
    if popup_before not in skin_options or popup_after not in skin_options:
        failures.append(f"Custom Skin values expected valid options, got {popup_before!r} -> {popup_after!r} from {skin_options!r}")
    elif popup_after == popup_before:
        failures.append(f"Custom Skin Skin cycle click did not change value {popup_before!r}")
    if selected_after != popup_after:
        failures.append(f"Custom Skin selectedSkin state expected {popup_after!r}, got {selected_after!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_popup_selector(client, "Custom Skin", "Skin", popup_before, skin_options)
    state = client.get("/state")
    popup = require_popup(state, "Custom Skin")
    if self_after != self_before:
        click_popup_setting(client, popup, "Self")
        state = client.get("/state")
        popup = require_popup(state, "Custom Skin")
    if others_after != others_before:
        click_popup_setting(client, popup, "Others")
        state = client.get("/state")
    restored_selected = nested(state, "cosmetics", "skin", "settings", "selectedSkin")
    restored_self = nested(state, "cosmetics", "skin", "settings", "self")
    restored_others = nested(state, "cosmetics", "skin", "settings", "others")
    if restored_selected != popup_before or restored_self != self_before or restored_others != others_before:
        raise SystemExit(
            f"Custom Skin restore expected skin/self/others {popup_before!r}/{self_before!r}/{others_before!r}, "
            f"got {restored_selected!r}/{restored_self!r}/{restored_others!r}"
        )

    return {
        "handled": click_result.get("handled"),
        "popup": popup_after_cycle.get("displayName"),
        "entries": entries,
        "optionsContainVerifierPngs": "zz_floyd_skin_verify_a.png" in skin_options and "zz_floyd_skin_verify_b.png" in skin_options,
        "selfToggle": {
            "handled": self_click.get("handled"),
            "before": self_before,
            "after": self_after,
        },
        "othersToggle": {
            "handled": others_click.get("handled"),
            "before": others_before,
            "after": others_after,
        },
        "openFolderHandled": action_click.get("handled"),
        "cycleClick": {
            "handled": cycle_click.get("handled"),
            "before": popup_before,
            "after": popup_after,
        },
    }


def verify_hud_edit_layout_extra(client: "BridgeClient") -> dict[str, Any]:
    inventory = verify_hud_layout_entry(client, "Inventory HUD")
    scoreboard = verify_hud_layout_entry(client, "Custom Scoreboard")
    return {
        "inventoryHud": inventory,
        "customScoreboard": scoreboard,
    }


def verify_hud_layout_entry(client: "BridgeClient", label: str) -> dict[str, Any]:
    state = open_legacy_browser(client)
    click_result = click_browser_entry(client, state, label, button=1)
    state = client.get("/state")
    popup = require_popup(state, label)
    extras = popup_extra_labels(popup)
    layout_click = click_popup_extra(client, popup, "Edit Layout")
    state = client.get("/state")
    screen = state.get("screen")
    screen_title = state.get("screenTitle")

    failures: list[str] = []
    if click_result.get("handled") is not True:
        failures.append(f"{label} right click for Edit Layout was not handled")
    if layout_click.get("handled") is not True:
        failures.append(f"{label} Edit Layout click was not handled")
    if extras != ["Edit Layout"]:
        failures.append(f"{label} popup extras expected ['Edit Layout'], got {extras!r}")
    if screen != "com.odtheking.odin.clickgui.HudManager":
        failures.append(f"{label} Edit Layout expected HudManager screen, got {screen!r}")
    if screen_title != "HUD Manager":
        failures.append(f"{label} Edit Layout expected HUD Manager title, got {screen_title!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    client.post("/screen", {"screen": "close"})
    time.sleep(0.15)

    return {
        "handled": click_result.get("handled"),
        "popup": popup.get("displayName"),
        "extras": extras,
        "layoutClickHandled": layout_click.get("handled"),
        "screen": screen,
        "screenTitle": screen_title,
    }


def verify_gui_style_color_picker(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_hub(client)
    before = gui_style_flags(state)
    button = nested(state, "legacyGui", "hubButtons", "editUi")
    if not isinstance(button, dict):
        raise SystemExit("Legacy /fa hub Edit UI button is missing from debug state")
    style_click = client.post("/mouse", mouse_payload(button, button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "GUI_STYLE":
        raise SystemExit(f"Expected legacy GUI page GUI_STYLE, got {nested(state, 'legacyGui', 'page')!r}")

    entries = nested(state, "legacyGui", "guiStyleEditor", "entries")
    if not isinstance(entries, list):
        raise SystemExit("GUI Style editor entries are missing from debug state")
    targets = [entry.get("target") for entry in entries if isinstance(entry, dict)]
    expected_targets = ["TEXT", "BUTTON_BORDER", "GUI_BORDER"]
    pick_entry = next((entry for entry in entries if isinstance(entry, dict) and entry.get("target") == "TEXT"), None)
    if not isinstance(pick_entry, dict) or not isinstance(pick_entry.get("bounds"), dict):
        raise SystemExit("GUI Style Button Text picker entry is missing bounds")

    pick_click = client.post("/mouse", mouse_payload(pick_entry["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "colorPicker") != "Button Text":
        raise SystemExit(f"Expected GUI Style Button Text color picker, got {nested(state, 'legacyGui', 'colorPicker')!r}")
    bounds = nested(state, "legacyGui", "colorPickerBounds")
    if not isinstance(bounds, dict):
        raise SystemExit("GUI Style color picker bounds missing from debug state")

    fade_click = click_color_picker_button(client, bounds, "fade")
    state = client.get("/state")
    bounds_after_fade = nested(state, "legacyGui", "colorPickerBounds")
    if not isinstance(bounds_after_fade, dict):
        raise SystemExit("GUI Style color picker closed before apply")
    apply_click = click_color_picker_button(client, bounds_after_fade, "apply")
    state = client.get("/state")
    after = gui_style_flags(state)

    failures: list[str] = []
    if style_click.get("handled") is not True:
        failures.append("GUI Style hub Edit UI click was not handled")
    if pick_click.get("handled") is not True:
        failures.append("GUI Style Button Text picker click was not handled")
    if fade_click.get("handled") is not True:
        failures.append("GUI Style Button Text Fade click was not handled")
    if apply_click.get("handled") is not True:
        failures.append("GUI Style color picker Apply click was not handled")
    if targets != expected_targets:
        failures.append(f"GUI Style editor targets expected {expected_targets!r}, got {targets!r}")
    if after["fade"] == before["fade"]:
        failures.append(f"GUI Style Button Text Fade did not toggle value {before['fade']!r}")
    if after["fade"] is True and after["chroma"] is not False:
        failures.append(f"GUI Style Button Text Fade enabled should disable chroma, got chroma={after['chroma']!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_gui_style_flags(client, before)
    restored = gui_style_flags(client.get("/state"))
    if restored != before:
        raise SystemExit(f"GUI Style Button Text restore expected {before!r}, got {restored!r}")

    return {
        "styleClickHandled": style_click.get("handled"),
        "pickerClickHandled": pick_click.get("handled"),
        "fadeClickHandled": fade_click.get("handled"),
        "applyClickHandled": apply_click.get("handled"),
        "targets": targets,
        "before": before,
        "after": after,
        "restored": restored,
    }


def verify_render_page_time_controls(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_hub(client)
    before = render_page_state(state)

    label = nested(state, "legacyGui", "labels", "Render")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Render label is missing from debug state")
    force_frame(client, "floyd-verify-render-hub.png")
    render_click = client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "RENDER":
        raise SystemExit(f"Expected legacy GUI page RENDER, got {nested(state, 'legacyGui', 'page')!r}")
    force_frame(client, "floyd-verify-render-page.png")
    state = client.get("/state")
    state = wait_for_page_rows(client, "RENDER")
    row_labels = page_row_labels(state)
    entries = render_entries(state)
    expected_entries = [
        ("BOOLEAN_TOGGLE", "Server ID Hider"),
        ("BOOLEAN_TOGGLE", "Profile ID Hider"),
        ("XRAY_TOGGLE", "X-Ray"),
        ("SLIDER", "Opacity"),
        ("RELOAD_XRAY", "Reload Blocks"),
        ("MODULE_TOGGLE", "Mob ESP"),
        ("BOOLEAN_TOGGLE", "Time Changer"),
        ("SLIDER", "Time"),
        ("STALK", "Stalk"),
        ("BORDERLESS", "Borderless Window"),
        ("TITLE_FIELD", "Instance Title"),
    ]
    missing_entries = [item for item in expected_entries if find_render_entry(state, *item, required=False) is None]
    expected_rows = ["Server ID Hider", "Profile ID Hider", "X-Ray", "Mob ESP", "Other", "Time Changer", "Borderless Window"]
    missing_rows = [row for row in expected_rows if not any(label.startswith(row) for label in row_labels)]

    server_click = client.post("/mouse", mouse_payload(find_render_entry(state, "BOOLEAN_TOGGLE", "Server ID Hider")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    profile_click = client.post("/mouse", mouse_payload(find_render_entry(state, "BOOLEAN_TOGGLE", "Profile ID Hider")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    xray_click = client.post("/mouse", mouse_payload(find_render_entry(state, "XRAY_TOGGLE", "X-Ray")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    opacity_fraction = 0.25 if float(before["xrayOpacity"]) > 0.5 else 0.75
    opacity_click = click_rect_slider_at_fraction(client, find_render_entry(state, "SLIDER", "Opacity"), opacity_fraction)
    time.sleep(0.15)
    state = client.get("/state")
    reload_click = client.post("/mouse", mouse_payload(find_render_entry(state, "RELOAD_XRAY", "Reload Blocks")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    mob_click = client.post("/mouse", mouse_payload(find_render_entry(state, "MODULE_TOGGLE", "Mob ESP")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    time_toggle_click = client.post("/mouse", mouse_payload(find_render_entry(state, "BOOLEAN_TOGGLE", "Time Changer")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    time_fraction = 0.25 if float(before["timeValue"]) > 50.0 else 0.75
    time_click = click_rect_slider_at_fraction(client, find_render_entry(state, "SLIDER", "Time"), time_fraction)
    time.sleep(0.15)
    state = client.get("/state")
    borderless_click = client.post("/mouse", mouse_payload(find_render_entry(state, "BORDERLESS", "Borderless Window")["bounds"], button=0))
    time.sleep(0.15)
    after = render_page_state(client.get("/state"))

    failures: list[str] = []
    if render_click.get("handled") is not True:
        failures.append("Render hub label click was not handled")
    if server_click.get("handled") is not True:
        failures.append("Render Server ID Hider row click was not handled")
    if profile_click.get("handled") is not True:
        failures.append("Render Profile ID Hider row click was not handled")
    if xray_click.get("handled") is not True:
        failures.append("Render X-Ray row click was not handled")
    if opacity_click.get("handled") is not True:
        failures.append("Render X-Ray Opacity row click was not handled")
    if reload_click.get("handled") is not True:
        failures.append("Render Reload Blocks row click was not handled")
    if mob_click.get("handled") is not True:
        failures.append("Render Mob ESP row click was not handled")
    if time_toggle_click.get("handled") is not True:
        failures.append("Render Time Changer row click was not handled")
    if time_click.get("handled") is not True:
        failures.append("Render Time row click was not handled")
    if borderless_click.get("handled") is not True:
        failures.append("Render Borderless Window row click was not handled")
    if missing_rows:
        failures.append(f"Render page rows missing expected labels {missing_rows!r} from {row_labels!r}")
    if missing_entries:
        failures.append(f"Render editor entries missing expected controls {missing_entries!r}; got {[(e.get('kind'), e.get('settingName')) for e in entries]}")
    for key, label_name in [
        ("serverIdHider", "Server ID Hider"),
        ("profileIdHider", "Profile ID Hider"),
        ("xrayEnabled", "X-Ray"),
        ("mobEnabled", "Mob ESP"),
        ("timeEnabled", "Time Changer"),
        ("borderlessWindowed", "Borderless Window"),
    ]:
        if after[key] == before[key]:
            failures.append(f"Render {label_name} row did not toggle value {before[key]!r}")
    if abs(float(after["xrayOpacity"]) - float(before["xrayOpacity"])) < 0.01:
        failures.append(f"Render X-Ray Opacity row did not change value {before['xrayOpacity']!r} -> {after['xrayOpacity']!r}")
    if abs(float(after["timeValue"]) - float(before["timeValue"])) < 0.01:
        failures.append(f"Render Time row did not change value {before['timeValue']!r} -> {after['timeValue']!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_render_page_controls(client, before)
    restored = render_page_state(client.get("/state"))
    if not render_page_matches(restored, before):
        raise SystemExit(f"Render page restore expected {before!r}, got {restored!r}")

    return {
        "renderClickHandled": render_click.get("handled"),
        "serverClickHandled": server_click.get("handled"),
        "profileClickHandled": profile_click.get("handled"),
        "xrayClickHandled": xray_click.get("handled"),
        "opacityClickHandled": opacity_click.get("handled"),
        "reloadClickHandled": reload_click.get("handled"),
        "mobClickHandled": mob_click.get("handled"),
        "toggleClickHandled": time_toggle_click.get("handled"),
        "numberClickHandled": time_click.get("handled"),
        "borderlessClickHandled": borderless_click.get("handled"),
        "rowsContainExpected": not missing_rows,
        "entriesContainExpected": not missing_entries,
        "before": before,
        "after": after,
        "restored": restored,
    }


def verify_xray_page_controls(client: "BridgeClient") -> dict[str, Any]:
    test_block = "minecraft:barrier"
    state = open_legacy_browser(client)
    original_blocks = xray_opaque_blocks(state)
    originally_present = test_block in original_blocks
    if not originally_present:
        add_xray_block_via_popup(client, test_block)
        state = client.get("/state")
        if test_block not in xray_opaque_blocks(state):
            raise SystemExit(f"Could not prepare X-Ray page test block {test_block!r}")

    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Render")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Render label is missing from debug state before X-Ray page verification")
    force_frame(client, "floyd-verify-xray-render-hub.png")
    render_click = client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = wait_for_page_rows(client, "RENDER")
    edit_row = find_page_row(state, "Edit Blocks")
    edit_click = click_render_page_row(client, state, edit_row)
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "XRAY":
        raise SystemExit(f"Expected legacy GUI page XRAY, got {nested(state, 'legacyGui', 'page')!r}")
    force_frame(client, "floyd-verify-xray-page.png")
    state = client.get("/state")
    entries = xray_page_entries(state)
    remove_entry = find_xray_page_entry(state, test_block, add=False)
    remove_click = client.post("/mouse", mouse_payload(remove_entry["bounds"], button=0))
    time.sleep(0.15)
    after_blocks = xray_opaque_blocks(client.get("/state"))

    failures: list[str] = []
    if render_click.get("handled") is not True:
        failures.append("Render hub label click for X-Ray page was not handled")
    if edit_click.get("handled") is not True:
        failures.append("Render X-Ray Edit Blocks row click was not handled")
    if remove_click.get("handled") is not True:
        failures.append("X-Ray page remove block click was not handled")
    if not entries:
        failures.append("X-Ray page entries were empty")
    if test_block in after_blocks:
        failures.append(f"X-Ray page remove expected {test_block!r} absent, got {after_blocks!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    if originally_present:
        add_xray_block_via_popup(client, test_block)
    restored_blocks = xray_opaque_blocks(client.get("/state"))
    if (test_block in restored_blocks) != originally_present:
        raise SystemExit(f"X-Ray page restore for {test_block!r} expected present={originally_present!r}, got {restored_blocks!r}")

    return {
        "renderClickHandled": render_click.get("handled"),
        "editClickHandled": edit_click.get("handled"),
        "removeClickHandled": remove_click.get("handled"),
        "entriesPresent": bool(entries),
        "block": test_block,
        "beforeContains": test_block in original_blocks,
        "afterContains": test_block in after_blocks,
        "restoredContains": test_block in restored_blocks,
    }


def verify_cosmetic_page_controls(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_hub(client)
    before = cosmetic_page_state(state)
    label = nested(state, "legacyGui", "labels", "Cosmetic")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Cosmetic label is missing from debug state")
    force_frame(client, "floyd-verify-cosmetic-hub.png")
    cosmetic_click = client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "COSMETIC":
        raise SystemExit(f"Expected legacy GUI page COSMETIC, got {nested(state, 'legacyGui', 'page')!r}")
    force_frame(client, "floyd-verify-cosmetic-page.png")
    state = client.get("/state")
    entries = cosmetic_entries(state)
    expected = [
        ("TOGGLE_SKIN", "Custom Skin"),
        ("TOGGLE_CAPE", "Cape"),
        ("TOGGLE_CONE", "Cone Hat"),
        ("TARGET", "Target"),
        ("SLIDER", "X"),
        ("SLIDER", "Y"),
        ("SLIDER", "Z"),
    ]
    missing = [item for item in expected if find_cosmetic_entry(state, *item, required=False) is None]

    skin_click = client.post("/mouse", mouse_payload(find_cosmetic_entry(state, "TOGGLE_SKIN", "Custom Skin")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    cape_click = client.post("/mouse", mouse_payload(find_cosmetic_entry(state, "TOGGLE_CAPE", "Cape")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    cone_click = client.post("/mouse", mouse_payload(find_cosmetic_entry(state, "TOGGLE_CONE", "Cone Hat")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    target_click = client.post("/mouse", mouse_payload(find_cosmetic_entry(state, "TARGET", "Target")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    x_entry = find_cosmetic_entry(state, "SLIDER", "X")
    x_fraction = 0.25 if float(before["scaleX"]) > 1.0 else 0.75
    x_click = click_rect_slider_at_fraction(client, x_entry, x_fraction)
    time.sleep(0.15)
    state = client.get("/state")
    y_entry = find_cosmetic_entry(state, "SLIDER", "Y")
    y_fraction = 0.25 if float(before["scaleY"]) > 1.0 else 0.75
    y_click = click_rect_slider_at_fraction(client, y_entry, y_fraction)
    time.sleep(0.15)
    state = client.get("/state")
    z_entry = find_cosmetic_entry(state, "SLIDER", "Z")
    z_fraction = 0.25 if float(before["scaleZ"]) > 1.0 else 0.75
    z_click = click_rect_slider_at_fraction(client, z_entry, z_fraction)
    time.sleep(0.15)
    after = cosmetic_page_state(client.get("/state"))

    failures: list[str] = []
    if cosmetic_click.get("handled") is not True:
        failures.append("Cosmetic hub label click was not handled")
    if skin_click.get("handled") is not True:
        failures.append("Cosmetic Custom Skin row click was not handled")
    if cape_click.get("handled") is not True:
        failures.append("Cosmetic Cape row click was not handled")
    if cone_click.get("handled") is not True:
        failures.append("Cosmetic Cone Hat row click was not handled")
    if target_click.get("handled") is not True:
        failures.append("Cosmetic Player Size Target row click was not handled")
    if x_click.get("handled") is not True:
        failures.append("Cosmetic Player Size X row click was not handled")
    if y_click.get("handled") is not True:
        failures.append("Cosmetic Player Size Y row click was not handled")
    if z_click.get("handled") is not True:
        failures.append("Cosmetic Player Size Z row click was not handled")
    if missing:
        failures.append(f"Cosmetic page entries missing expected controls {missing!r}; got {[(e.get('kind'), e.get('settingName')) for e in entries]}")
    if after["customSkin"] == before["customSkin"]:
        failures.append(f"Cosmetic Custom Skin row did not toggle value {before['customSkin']!r}")
    if after["cape"] == before["cape"]:
        failures.append(f"Cosmetic Cape row did not toggle value {before['cape']!r}")
    if after["cone"] == before["cone"]:
        failures.append(f"Cosmetic Cone Hat row did not toggle value {before['cone']!r}")
    if after["target"] == before["target"]:
        failures.append(f"Cosmetic Player Size Target row did not change value {before['target']!r}")
    if abs(float(after["scaleX"]) - float(before["scaleX"])) < 0.01:
        failures.append(f"Cosmetic Player Size X row did not change value {before['scaleX']!r} -> {after['scaleX']!r}")
    if abs(float(after["scaleY"]) - float(before["scaleY"])) < 0.01:
        failures.append(f"Cosmetic Player Size Y row did not change value {before['scaleY']!r} -> {after['scaleY']!r}")
    if abs(float(after["scaleZ"]) - float(before["scaleZ"])) < 0.01:
        failures.append(f"Cosmetic Player Size Z row did not change value {before['scaleZ']!r} -> {after['scaleZ']!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_cosmetic_page_controls(client, before)
    restored = cosmetic_page_state(client.get("/state"))
    if (
        restored["customSkin"] != before["customSkin"]
        or restored["cape"] != before["cape"]
        or restored["cone"] != before["cone"]
        or restored["target"] != before["target"]
        or abs(float(restored["scaleX"]) - float(before["scaleX"])) > 0.06
        or abs(float(restored["scaleY"]) - float(before["scaleY"])) > 0.06
        or abs(float(restored["scaleZ"]) - float(before["scaleZ"])) > 0.06
    ):
        raise SystemExit(f"Cosmetic page restore expected {before!r}, got {restored!r}")

    return {
        "cosmeticClickHandled": cosmetic_click.get("handled"),
        "skinClickHandled": skin_click.get("handled"),
        "capeClickHandled": cape_click.get("handled"),
        "coneClickHandled": cone_click.get("handled"),
        "targetClickHandled": target_click.get("handled"),
        "xClickHandled": x_click.get("handled"),
        "yClickHandled": y_click.get("handled"),
        "zClickHandled": z_click.get("handled"),
        "entriesContainExpected": not missing,
        "before": before,
        "after": after,
        "restored": restored,
    }


def verify_skin_page_controls(client: "BridgeClient") -> dict[str, Any]:
    state = open_skin_page(client, "floyd-verify-skin-page")
    before = skin_page_state(state)
    entries = skin_entries(state)
    expected = [
        ("TOGGLE", "Self"),
        ("TOGGLE", "Others"),
        ("OPEN_FOLDER", "Open Skin Folder"),
        ("DROPDOWN", "Skin"),
    ]
    missing = [item for item in expected if find_skin_entry(state, *item, required=False) is None]
    available = before["available"]
    dropdown_target = next((item for item in available if item != before["selected"]), None)
    if dropdown_target is None:
        raise SystemExit(f"Skin page dropdown needs at least two available skins, got {available!r}")

    self_click = client.post("/mouse", mouse_payload(find_skin_entry(state, "TOGGLE", "Self")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    others_click = client.post("/mouse", mouse_payload(find_skin_entry(state, "TOGGLE", "Others")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    open_folder_click = client.post("/mouse", mouse_payload(find_skin_entry(state, "OPEN_FOLDER", "Open Skin Folder")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    dropdown_click = select_skin_dropdown_value(client, state, dropdown_target)
    after = skin_page_state(client.get("/state"))

    failures: list[str] = []
    if self_click.get("handled") is not True:
        failures.append("Skin Self page row click was not handled")
    if others_click.get("handled") is not True:
        failures.append("Skin Others page row click was not handled")
    if open_folder_click.get("handled") is not True:
        failures.append("Skin Open Folder page row click was not handled")
    if dropdown_click.get("handled") is not True:
        failures.append("Skin dropdown page selection click was not handled")
    if missing:
        failures.append(f"Skin page entries missing expected controls {missing!r}; got {[(e.get('kind'), e.get('settingName')) for e in entries]}")
    if after["self"] == before["self"]:
        failures.append(f"Skin Self page row did not toggle value {before['self']!r}")
    if after["others"] == before["others"]:
        failures.append(f"Skin Others page row did not toggle value {before['others']!r}")
    if after["selected"] != dropdown_target:
        failures.append(f"Skin dropdown page selection expected {dropdown_target!r}, got {after['selected']!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_skin_page_controls(client, before)
    restored = skin_page_state(client.get("/state"))
    restored_core = {key: restored[key] for key in ("customSkin", "self", "others", "selected")}
    before_core = {key: before[key] for key in ("customSkin", "self", "others", "selected")}
    if restored_core != before_core:
        raise SystemExit(f"Skin page restore expected {before_core!r}, got {restored_core!r}")

    return {
        "entriesContainExpected": not missing,
        "selfClickHandled": self_click.get("handled"),
        "othersClickHandled": others_click.get("handled"),
        "openFolderHandled": open_folder_click.get("handled"),
        "dropdownClickHandled": dropdown_click.get("handled"),
        "before": before_core,
        "after": {key: after[key] for key in ("customSkin", "self", "others", "selected")},
        "restored": restored_core,
        "availableContainVerifierPngs": "zz_floyd_skin_verify_a.png" in available and "zz_floyd_skin_verify_b.png" in available,
    }


def verify_cape_page_controls(client: "BridgeClient") -> dict[str, Any]:
    state = open_cape_page(client, "floyd-verify-cape-page")
    before = cape_page_state(state)
    if len(before["available"]) < 2:
        raise SystemExit(f"Cape page needs at least two available capes, got {before['available']!r}")
    prev_button = cape_page_button(state, "previous")
    next_button = cape_page_button(state, "next")
    open_button = cape_page_button(state, "openFolder")

    next_click = client.post("/mouse", mouse_payload(next_button, button=0))
    time.sleep(0.15)
    after_next = cape_page_state(client.get("/state"))
    state = client.get("/state")
    prev_click = client.post("/mouse", mouse_payload(cape_page_button(state, "previous"), button=0))
    time.sleep(0.15)
    after_prev = cape_page_state(client.get("/state"))
    state = client.get("/state")
    open_folder_click = client.post("/mouse", mouse_payload(cape_page_button(state, "openFolder"), button=0))
    time.sleep(0.15)

    failures: list[str] = []
    if next_click.get("handled") is not True:
        failures.append("Cape page Next click was not handled")
    if prev_click.get("handled") is not True:
        failures.append("Cape page Previous click was not handled")
    if open_folder_click.get("handled") is not True:
        failures.append("Cape page Open Folder click was not handled")
    if after_next["selected"] == before["selected"]:
        failures.append(f"Cape page Next click did not change value {before['selected']!r}")
    if after_prev["selected"] != before["selected"]:
        failures.append(f"Cape page Previous click did not restore selected cape {before['selected']!r}, got {after_prev['selected']!r}")
    for name, button in (("previous", prev_button), ("next", next_button), ("openFolder", open_button)):
        if not all(key in button for key in ("left", "top", "right", "bottom")):
            failures.append(f"Cape page button {name!r} has invalid bounds {button!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_cape_page_controls(client, before)
    restored = cape_page_state(client.get("/state"))
    if restored["selected"] != before["selected"]:
        raise SystemExit(f"Cape page restore expected {before['selected']!r}, got {restored['selected']!r}")

    return {
        "buttonsPresent": True,
        "nextClickHandled": next_click.get("handled"),
        "previousClickHandled": prev_click.get("handled"),
        "openFolderHandled": open_folder_click.get("handled"),
        "before": {"selected": before["selected"]},
        "afterNext": {"selected": after_next["selected"]},
        "afterPrevious": {"selected": after_prev["selected"]},
        "restored": {"selected": restored["selected"]},
        "availableContainVerifierPngs": "zz_floyd_verify_a.png" in before["available"] and "zz_floyd_verify_b.png" in before["available"],
    }


def verify_cone_hat_page_controls(client: "BridgeClient") -> dict[str, Any]:
    state = open_cone_hat_page(client, "floyd-verify-cone-page")
    before = cone_hat_page_state(state)
    if len(before["available"]) < 2:
        raise SystemExit(f"Cone Hat page needs at least two available images, got {before['available']!r}")
    dropdown_target = next((item for item in before["available"] if item != before["selected"]), None)
    if dropdown_target is None:
        raise SystemExit(f"Cone Hat page dropdown needs at least two images, got {before['available']!r}")

    controls = cone_controls(state)
    missing_controls: list[str] = []
    if len(controls.get("sliders", [])) < 5:
        missing_controls.append("sliders")
    if len(controls.get("inputs", [])) < 5:
        missing_controls.append("inputs")
    for key in ("dropdownButton", "openFolder"):
        if not isinstance(controls.get(key), dict):
            missing_controls.append(key)

    height_fraction = 0.25 if before["height"] > 0.75 else 0.75
    height_click = click_cone_slider_at_fraction(client, state, 0, height_fraction)
    state = client.get("/state")
    radius_target = 0.23 if before["radius"] > 0.3 else 0.61
    radius_input = set_cone_input_value(client, state, 1, radius_target)
    state = client.get("/state")
    y_offset_target = -0.2 if before["yOffset"] < -0.45 else -0.7
    y_offset_input = set_cone_input_value(client, state, 2, y_offset_target)
    state = client.get("/state")
    rotation_target = 90.0 if abs(before["rotation"] - 90.0) > 0.01 else 180.0
    rotation_input = set_cone_input_value(client, state, 3, rotation_target)
    state = client.get("/state")
    spin_target = 45.0 if abs(before["spinSpeed"] - 45.0) > 0.01 else 90.0
    spin_input = set_cone_input_value(client, state, 4, spin_target)
    state = client.get("/state")
    dropdown_click = select_cone_dropdown_value(client, state, dropdown_target)
    state = client.get("/state")
    open_folder_click = client.post("/mouse", mouse_payload(cone_button(state, "openFolder"), button=0))
    time.sleep(0.15)
    after = cone_hat_page_state(client.get("/state"))

    failures: list[str] = []
    if height_click.get("handled") is not True:
        failures.append("Cone Hat page Height slider click was not handled")
    if radius_input.get("handled") is not True:
        failures.append("Cone Hat page Radius input edit was not handled")
    if y_offset_input.get("handled") is not True:
        failures.append("Cone Hat page Y Offset input edit was not handled")
    if rotation_input.get("handled") is not True:
        failures.append("Cone Hat page Rotation input edit was not handled")
    if spin_input.get("handled") is not True:
        failures.append("Cone Hat page Spin Speed input edit was not handled")
    if dropdown_click.get("handled") is not True:
        failures.append("Cone Hat page Image dropdown click was not handled")
    if open_folder_click.get("handled") is not True:
        failures.append("Cone Hat page Open Folder click was not handled")
    if missing_controls:
        failures.append(f"Cone Hat page controls missing {missing_controls!r}; got {controls!r}")
    if abs(after["height"] - before["height"]) < 0.01:
        failures.append(f"Cone Hat page Height slider did not change value {before['height']!r} -> {after['height']!r}")
    if abs(after["radius"] - before["radius"]) < 0.01:
        failures.append(f"Cone Hat page Radius input did not change value {before['radius']!r} -> {after['radius']!r}")
    if abs(after["yOffset"] - before["yOffset"]) < 0.01:
        failures.append(f"Cone Hat page Y Offset input did not change value {before['yOffset']!r} -> {after['yOffset']!r}")
    if abs(after["rotation"] - before["rotation"]) < 0.01:
        failures.append(f"Cone Hat page Rotation input did not change value {before['rotation']!r} -> {after['rotation']!r}")
    if abs(after["spinSpeed"] - before["spinSpeed"]) < 0.01:
        failures.append(f"Cone Hat page Spin Speed input did not change value {before['spinSpeed']!r} -> {after['spinSpeed']!r}")
    if after["selected"] != dropdown_target:
        failures.append(f"Cone Hat page dropdown expected {dropdown_target!r}, got {after['selected']!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_cone_hat_page_controls(client, before)
    restored = cone_hat_page_state(client.get("/state"))
    if not cone_hat_page_matches(restored, before):
        raise SystemExit(f"Cone Hat page restore expected {before!r}, got {restored!r}")

    return {
        "controlsPresent": not missing_controls,
        "heightClickHandled": height_click.get("handled"),
        "radiusInputHandled": radius_input.get("handled"),
        "yOffsetInputHandled": y_offset_input.get("handled"),
        "rotationInputHandled": rotation_input.get("handled"),
        "spinSpeedInputHandled": spin_input.get("handled"),
        "dropdownClickHandled": dropdown_click.get("handled"),
        "openFolderHandled": open_folder_click.get("handled"),
        "before": cone_hat_page_proof_state(before),
        "after": cone_hat_page_proof_state(after),
        "restored": cone_hat_page_proof_state(restored),
        "availableContainVerifierPngs": "zz_floyd_cone_verify_a.png" in before["available"] and "zz_floyd_cone_verify_b.png" in before["available"],
    }


def verify_player_size_page_controls(client: "BridgeClient") -> dict[str, Any]:
    state = open_player_size_page(client, "floyd-verify-player-size-page")
    before = player_size_page_state(state)
    labels = page_row_labels(state)
    expected_prefixes = ["Player Size:", "Player Size", "Target:", "Size X:", "Size Y:", "Size Z:"]
    missing = [prefix for prefix in expected_prefixes if not any(label.startswith(prefix) for label in labels)]

    toggle_click = client.post("/mouse", mouse_payload(find_page_row(state, "Player Size:")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    target_click = client.post("/mouse", mouse_payload(find_page_row(state, "Target:")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    x_click = client.post("/mouse", mouse_payload(find_page_row(state, "Size X:")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    y_click = client.post("/mouse", mouse_payload(find_page_row(state, "Size Y:")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    z_click = client.post("/mouse", mouse_payload(find_page_row(state, "Size Z:")["bounds"], button=0))
    time.sleep(0.15)
    after = player_size_page_state(client.get("/state"))

    failures: list[str] = []
    if toggle_click.get("handled") is not True:
        failures.append("Player Size page module toggle click was not handled")
    if target_click.get("handled") is not True:
        failures.append("Player Size page Target selector click was not handled")
    if x_click.get("handled") is not True:
        failures.append("Player Size page Size X slider click was not handled")
    if y_click.get("handled") is not True:
        failures.append("Player Size page Size Y slider click was not handled")
    if z_click.get("handled") is not True:
        failures.append("Player Size page Size Z slider click was not handled")
    if missing:
        failures.append(f"Player Size page missing expected rows {missing!r}; got {labels!r}")
    if after["enabled"] == before["enabled"]:
        failures.append(f"Player Size page module toggle did not change value {before['enabled']!r}")
    if after["target"] == before["target"]:
        failures.append(f"Player Size page Target selector did not change value {before['target']!r}")
    if abs(float(after["scaleX"]) - float(before["scaleX"])) < 0.01:
        failures.append(f"Player Size page Size X slider did not change value {before['scaleX']!r} -> {after['scaleX']!r}")
    if abs(float(after["scaleY"]) - float(before["scaleY"])) < 0.01:
        failures.append(f"Player Size page Size Y slider did not change value {before['scaleY']!r} -> {after['scaleY']!r}")
    if abs(float(after["scaleZ"]) - float(before["scaleZ"])) < 0.01:
        failures.append(f"Player Size page Size Z slider did not change value {before['scaleZ']!r} -> {after['scaleZ']!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_player_size_page_controls(client, before)
    restored = player_size_page_state(client.get("/state"))
    if (
        restored["enabled"] != before["enabled"]
        or restored["target"] != before["target"]
        or abs(float(restored["scaleX"]) - float(before["scaleX"])) > 0.01
        or abs(float(restored["scaleY"]) - float(before["scaleY"])) > 0.01
        or abs(float(restored["scaleZ"]) - float(before["scaleZ"])) > 0.01
    ):
        raise SystemExit(f"Player Size page restore expected {before!r}, got {restored!r}")

    return {
        "rowsContainExpected": not missing,
        "toggleClickHandled": toggle_click.get("handled"),
        "targetClickHandled": target_click.get("handled"),
        "xClickHandled": x_click.get("handled"),
        "yClickHandled": y_click.get("handled"),
        "zClickHandled": z_click.get("handled"),
        "before": before,
        "after": after,
        "restored": restored,
    }


def verify_camera_page_controls(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_hub(client)
    before = camera_page_state(state)
    label = nested(state, "legacyGui", "labels", "Camera")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Camera label is missing from debug state")
    force_frame(client, "floyd-verify-camera-hub.png")
    camera_click = client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "CAMERA":
        raise SystemExit(f"Expected legacy GUI page CAMERA, got {nested(state, 'legacyGui', 'page')!r}")
    force_frame(client, "floyd-verify-camera-page.png")
    state = client.get("/state")
    entries = camera_entries(state)
    expected = [
        ("RUNTIME_TOGGLE", "Freecam"),
        ("SLIDER", "Speed"),
        ("RUNTIME_TOGGLE", "Freelook"),
        ("SLIDER", "Distance"),
        ("BOOLEAN_TOGGLE", "Disable Front Cam"),
        ("BOOLEAN_TOGGLE", "Disable Back Cam"),
        ("BOOLEAN_TOGGLE", "No Third-Person Clipping"),
        ("BOOLEAN_TOGGLE", "Scrolling Changes Distance"),
        ("BOOLEAN_TOGGLE", "Reset F5 Scrolling"),
        ("SLIDER", "Camera Distance"),
    ]
    missing = [item for item in expected if find_camera_entry(state, *item, required=False) is None]

    disable_front_click = client.post("/mouse", mouse_payload(find_camera_entry(state, "BOOLEAN_TOGGLE", "Disable Front Cam")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    disable_back_click = client.post("/mouse", mouse_payload(find_camera_entry(state, "BOOLEAN_TOGGLE", "Disable Back Cam")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    no_clip_click = client.post("/mouse", mouse_payload(find_camera_entry(state, "BOOLEAN_TOGGLE", "No Third-Person Clipping")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    scroll_click = client.post("/mouse", mouse_payload(find_camera_entry(state, "BOOLEAN_TOGGLE", "Scrolling Changes Distance")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    reset_click = client.post("/mouse", mouse_payload(find_camera_entry(state, "BOOLEAN_TOGGLE", "Reset F5 Scrolling")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    speed_entry = find_camera_entry(state, "SLIDER", "Speed")
    speed_fraction = 0.25 if float(before["speed"]) > 5.0 else 0.75
    speed_click = click_camera_slider_at_fraction(client, speed_entry, speed_fraction)
    time.sleep(0.15)
    state = client.get("/state")
    f5_entry = find_camera_entry(state, "SLIDER", "Camera Distance")
    f5_fraction = 0.25 if float(before["f5Distance"]) > 10.0 else 0.75
    f5_click = click_camera_slider_at_fraction(client, f5_entry, f5_fraction)
    time.sleep(0.15)
    after = camera_page_state(client.get("/state"))

    failures: list[str] = []
    if camera_click.get("handled") is not True:
        failures.append("Camera hub label click was not handled")
    if disable_front_click.get("handled") is not True:
        failures.append("Camera Disable Front row click was not handled")
    if disable_back_click.get("handled") is not True:
        failures.append("Camera Disable Back row click was not handled")
    if no_clip_click.get("handled") is not True:
        failures.append("Camera No Third-Person Clipping row click was not handled")
    if scroll_click.get("handled") is not True:
        failures.append("Camera Scrolling Changes Distance row click was not handled")
    if reset_click.get("handled") is not True:
        failures.append("Camera Reset F5 Scrolling row click was not handled")
    if speed_click.get("handled") is not True:
        failures.append("Camera Speed row click was not handled")
    if f5_click.get("handled") is not True:
        failures.append("Camera F5 Distance row click was not handled")
    if missing:
        failures.append(f"Camera page entries missing expected controls {missing!r}; got {[(e.get('kind'), e.get('settingName')) for e in entries]}")
    if after["disableFront"] == before["disableFront"]:
        failures.append(f"Camera Disable Front row did not toggle value {before['disableFront']!r}")
    if after["disableBack"] == before["disableBack"]:
        failures.append(f"Camera Disable Back row did not toggle value {before['disableBack']!r}")
    if after["noClip"] == before["noClip"]:
        failures.append(f"Camera No Third-Person Clipping row did not toggle value {before['noClip']!r}")
    if after["scrollEnabled"] == before["scrollEnabled"]:
        failures.append(f"Camera Scrolling Changes Distance row did not toggle value {before['scrollEnabled']!r}")
    if after["resetOnToggle"] == before["resetOnToggle"]:
        failures.append(f"Camera Reset F5 Scrolling row did not toggle value {before['resetOnToggle']!r}")
    if abs(float(after["speed"]) - float(before["speed"])) < 0.01:
        failures.append(f"Camera Speed row did not change value {before['speed']!r} -> {after['speed']!r}")
    if abs(float(after["f5Distance"]) - float(before["f5Distance"])) < 0.01:
        failures.append(f"Camera F5 Distance row did not change value {before['f5Distance']!r} -> {after['f5Distance']!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_camera_page_controls(client, before)
    restored = camera_page_state(client.get("/state"))
    if restored != before:
        raise SystemExit(f"Camera page restore expected {before!r}, got {restored!r}")

    return {
        "cameraClickHandled": camera_click.get("handled"),
        "disableFrontClickHandled": disable_front_click.get("handled"),
        "disableBackClickHandled": disable_back_click.get("handled"),
        "noClipClickHandled": no_clip_click.get("handled"),
        "scrollClickHandled": scroll_click.get("handled"),
        "resetClickHandled": reset_click.get("handled"),
        "speedClickHandled": speed_click.get("handled"),
        "f5ClickHandled": f5_click.get("handled"),
        "entriesContainExpected": not missing,
        "before": before,
        "after": after,
        "restored": restored,
    }


def verify_hiders_page_controls(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_hub(client)
    before = hiders_page_state(state)
    label = nested(state, "legacyGui", "labels", "Render")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Render label is missing from debug state before Hiders page verification")
    force_frame(client, "floyd-verify-hiders-render-hub.png")
    render_click = client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = wait_for_page_rows(client, "RENDER")
    hiders_row = find_page_row(state, "Hiders")
    hiders_click = click_render_page_row(client, state, hiders_row)
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "HIDERS":
        raise SystemExit(f"Expected legacy GUI page HIDERS, got {nested(state, 'legacyGui', 'page')!r}")
    force_frame(client, "floyd-verify-hiders-page.png")
    state = client.get("/state")
    entries = hiders_entries(state)
    expected = [(kind, name) for name, key, kind in hiders_page_controls()]
    missing = [item for item in expected if find_hiders_entry(state, *item, required=False) is None]

    clicks: dict[str, Any] = {}
    for name, key, kind in hiders_page_controls():
        click = client.post("/mouse", mouse_payload(find_hiders_entry(state, kind, name)["bounds"], button=0))
        clicks[key] = click
        time.sleep(0.15)
        state = client.get("/state")
    after = hiders_page_state(client.get("/state"))

    failures: list[str] = []
    if render_click.get("handled") is not True:
        failures.append("Render hub label click for Hiders page was not handled")
    if hiders_click.get("handled") is not True:
        failures.append("Render Hiders row click was not handled")
    for name, key, kind in hiders_page_controls():
        if clicks[key].get("handled") is not True:
            failures.append(f"Hiders {name} row click was not handled")
    if missing:
        failures.append(f"Hiders page entries missing expected controls {missing!r}; got {[(e.get('kind'), e.get('settingName')) for e in entries]}")
    for name, key, kind in hiders_page_controls():
        if after[key] == before[key]:
            action = "change" if kind == "NO_ARMOR" else "toggle"
            failures.append(f"Hiders {name} row did not {action} value {before[key]!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_hiders_page_controls(client, before)
    restored = hiders_page_state(client.get("/state"))
    if restored != before:
        raise SystemExit(f"Hiders page restore expected {before!r}, got {restored!r}")

    return {
        "renderClickHandled": render_click.get("handled"),
        "hidersClickHandled": hiders_click.get("handled"),
        "rowClickHandled": {key: click.get("handled") for key, click in clicks.items()},
        "entriesContainExpected": not missing,
        "before": before,
        "after": after,
        "restored": restored,
    }


def verify_animations_page_controls(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_hub(client)
    before = animations_page_state(state)
    label = nested(state, "legacyGui", "labels", "Render")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Render label is missing from debug state before Animations page verification")
    force_frame(client, "floyd-verify-animations-render-hub.png")
    render_click = client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = wait_for_page_rows(client, "RENDER")
    animations_row = find_page_row(state, "Attack Animation")
    animations_click = click_render_page_row(client, state, animations_row)
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "ANIMATIONS":
        raise SystemExit(f"Expected legacy GUI page ANIMATIONS, got {nested(state, 'legacyGui', 'page')!r}")
    force_frame(client, "floyd-verify-animations-page.png")
    state = client.get("/state")
    entries = animations_entries(state)
    expected = [
        ("TOGGLE_MODULE", ""),
        ("SLIDER", "Pos X"),
        ("SLIDER", "Pos Y"),
        ("SLIDER", "Pos Z"),
        ("SLIDER", "Rot X"),
        ("SLIDER", "Rot Y"),
        ("SLIDER", "Rot Z"),
        ("SLIDER", "Scale"),
        ("SLIDER", "Swing Duration"),
        ("TOGGLE_SETTING", "Cancel Re-Equip"),
        ("TOGGLE_SETTING", "Hide Hand"),
        ("TOGGLE_SETTING", "Classic Click"),
    ]
    missing = [item for item in expected if find_animations_entry(state, *item, required=False) is None]

    module_click = client.post("/mouse", mouse_payload(find_animations_entry(state, "TOGGLE_MODULE", "")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    pos_x_entry = find_animations_entry(state, "SLIDER", "Pos X")
    pos_x_fraction = 0.25 if float(before["posX"]) > 0.0 else 0.75
    pos_x_click = click_rect_slider_at_fraction(client, pos_x_entry, pos_x_fraction)
    time.sleep(0.15)
    state = client.get("/state")
    cancel_click = client.post("/mouse", mouse_payload(find_animations_entry(state, "TOGGLE_SETTING", "Cancel Re-Equip")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    hide_hand_click = client.post("/mouse", mouse_payload(find_animations_entry(state, "TOGGLE_SETTING", "Hide Hand")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    classic_click = client.post("/mouse", mouse_payload(find_animations_entry(state, "TOGGLE_SETTING", "Classic Click")["bounds"], button=0))
    time.sleep(0.15)
    after = animations_page_state(client.get("/state"))

    failures: list[str] = []
    if render_click.get("handled") is not True:
        failures.append("Render hub label click for Animations page was not handled")
    if animations_click.get("handled") is not True:
        failures.append("Render Attack Animation row click was not handled")
    if module_click.get("handled") is not True:
        failures.append("Animations module row click was not handled")
    if pos_x_click.get("handled") is not True:
        failures.append("Animations Pos X row click was not handled")
    if cancel_click.get("handled") is not True:
        failures.append("Animations Cancel Re-Equip row click was not handled")
    if hide_hand_click.get("handled") is not True:
        failures.append("Animations Hide Hand row click was not handled")
    if classic_click.get("handled") is not True:
        failures.append("Animations Classic Click row click was not handled")
    if missing:
        failures.append(f"Animations page entries missing expected controls {missing!r}; got {[(e.get('kind'), e.get('settingName')) for e in entries]}")
    if after["enabled"] == before["enabled"]:
        failures.append(f"Animations module row did not toggle value {before['enabled']!r}")
    if abs(float(after["posX"]) - float(before["posX"])) < 0.01:
        failures.append(f"Animations Pos X row did not change value {before['posX']!r} -> {after['posX']!r}")
    if after["cancelReEquip"] == before["cancelReEquip"]:
        failures.append(f"Animations Cancel Re-Equip row did not toggle value {before['cancelReEquip']!r}")
    if after["hideHand"] == before["hideHand"]:
        failures.append(f"Animations Hide Hand row did not toggle value {before['hideHand']!r}")
    if after["classicClick"] == before["classicClick"]:
        failures.append(f"Animations Classic Click row did not toggle value {before['classicClick']!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_animations_page_controls(client, before)
    restored = animations_page_state(client.get("/state"))
    if restored != before:
        raise SystemExit(f"Animations page restore expected {before!r}, got {restored!r}")

    return {
        "renderClickHandled": render_click.get("handled"),
        "animationsClickHandled": animations_click.get("handled"),
        "moduleClickHandled": module_click.get("handled"),
        "posXClickHandled": pos_x_click.get("handled"),
        "cancelClickHandled": cancel_click.get("handled"),
        "hideHandClickHandled": hide_hand_click.get("handled"),
        "classicClickHandled": classic_click.get("handled"),
        "entriesContainExpected": not missing,
        "before": before,
        "after": after,
        "restored": restored,
    }


def verify_mob_esp_page_controls(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_hub(client)
    before = mob_esp_page_state(state)
    label = nested(state, "legacyGui", "labels", "Render")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Render label is missing from debug state before Mob ESP page verification")
    force_frame(client, "floyd-verify-mob-esp-render-hub.png")
    render_click = client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = wait_for_page_rows(client, "RENDER")
    config_row = find_page_row(state, "Config")
    config_click = click_render_page_row(client, state, config_row)
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "MOB_ESP":
        raise SystemExit(f"Expected legacy GUI page MOB_ESP, got {nested(state, 'legacyGui', 'page')!r}")
    force_frame(client, "floyd-verify-mob-esp-page.png")
    state = client.get("/state")
    entries = mob_esp_entries(state)
    expected = [
        ("TOGGLE", "Tracers"),
        ("TOGGLE", "Hitboxes"),
        ("TOGGLE", "Star Mobs"),
        ("COLOR_PICK", "Default ESP Color"),
        ("COLOR_PICK", "Tracer Color"),
        ("NAV_FILTERS", "Edit Filters"),
    ]
    missing = [item for item in expected if find_mob_esp_entry(state, *item, required=False) is None]

    tracers_click = client.post("/mouse", mouse_payload(find_mob_esp_entry(state, "TOGGLE", "Tracers")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    hitboxes_click = client.post("/mouse", mouse_payload(find_mob_esp_entry(state, "TOGGLE", "Hitboxes")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    star_click = client.post("/mouse", mouse_payload(find_mob_esp_entry(state, "TOGGLE", "Star Mobs")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    default_color_click = client.post("/mouse", mouse_payload(find_mob_esp_entry(state, "COLOR_PICK", "Default ESP Color")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "colorPicker") != "Default ESP":
        raise SystemExit(f"Expected Mob ESP Default ESP color picker, got {nested(state, 'legacyGui', 'colorPicker')!r}")
    default_bounds = nested(state, "legacyGui", "colorPickerBounds")
    if not isinstance(default_bounds, dict):
        raise SystemExit("Mob ESP Default ESP color picker bounds missing from debug state")
    default_chroma_click = click_color_picker_button(client, default_bounds, "chroma")
    state = client.get("/state")
    default_bounds_after = nested(state, "legacyGui", "colorPickerBounds")
    if not isinstance(default_bounds_after, dict):
        raise SystemExit("Mob ESP Default ESP color picker closed before apply")
    default_apply_click = click_color_picker_button(client, default_bounds_after, "apply")
    time.sleep(0.15)
    state = client.get("/state")
    tracer_color_click = client.post("/mouse", mouse_payload(find_mob_esp_entry(state, "COLOR_PICK", "Tracer Color")["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "colorPicker") != "Stalk Tracer":
        raise SystemExit(f"Expected Mob ESP Stalk Tracer color picker, got {nested(state, 'legacyGui', 'colorPicker')!r}")
    tracer_bounds = nested(state, "legacyGui", "colorPickerBounds")
    if not isinstance(tracer_bounds, dict):
        raise SystemExit("Mob ESP Stalk Tracer color picker bounds missing from debug state")
    tracer_chroma_click = click_color_picker_button(client, tracer_bounds, "chroma")
    state = client.get("/state")
    tracer_bounds_after = nested(state, "legacyGui", "colorPickerBounds")
    if not isinstance(tracer_bounds_after, dict):
        raise SystemExit("Mob ESP Stalk Tracer color picker closed before apply")
    tracer_apply_click = click_color_picker_button(client, tracer_bounds_after, "apply")
    time.sleep(0.15)
    after = mob_esp_page_state(client.get("/state"))
    filters_click = client.post("/mouse", mouse_payload(find_mob_esp_entry(client.get("/state"), "NAV_FILTERS", "Edit Filters")["bounds"], button=0))
    time.sleep(0.15)
    filters_state = client.get("/state")
    filters_page = nested(filters_state, "legacyGui", "page")

    failures: list[str] = []
    if render_click.get("handled") is not True:
        failures.append("Render hub label click for Mob ESP page was not handled")
    if config_click.get("handled") is not True:
        failures.append("Render Mob ESP Config row click was not handled")
    if tracers_click.get("handled") is not True:
        failures.append("Mob ESP Tracers page row click was not handled")
    if hitboxes_click.get("handled") is not True:
        failures.append("Mob ESP Hitboxes page row click was not handled")
    if star_click.get("handled") is not True:
        failures.append("Mob ESP Star Mobs page row click was not handled")
    if default_color_click.get("handled") is not True:
        failures.append("Mob ESP Default ESP Color page picker click was not handled")
    if default_chroma_click.get("handled") is not True:
        failures.append("Mob ESP Default ESP Color page chroma click was not handled")
    if default_apply_click.get("handled") is not True:
        failures.append("Mob ESP Default ESP Color page apply click was not handled")
    if tracer_color_click.get("handled") is not True:
        failures.append("Mob ESP Stalk Tracer Color page picker click was not handled")
    if tracer_chroma_click.get("handled") is not True:
        failures.append("Mob ESP Stalk Tracer Color page chroma click was not handled")
    if tracer_apply_click.get("handled") is not True:
        failures.append("Mob ESP Stalk Tracer Color page apply click was not handled")
    if filters_click.get("handled") is not True:
        failures.append("Mob ESP Edit Filters page row click was not handled")
    if missing:
        failures.append(f"Mob ESP page entries missing expected controls {missing!r}; got {[(e.get('kind'), e.get('settingName')) for e in entries]}")
    if after["tracers"] == before["tracers"]:
        failures.append(f"Mob ESP Tracers page row did not toggle value {before['tracers']!r}")
    if after["hitboxes"] == before["hitboxes"]:
        failures.append(f"Mob ESP Hitboxes page row did not toggle value {before['hitboxes']!r}")
    if after["starMobs"] == before["starMobs"]:
        failures.append(f"Mob ESP Star Mobs page row did not toggle value {before['starMobs']!r}")
    if after["defaultChroma"] == before["defaultChroma"]:
        failures.append(f"Mob ESP Default ESP Color page chroma did not toggle value {before['defaultChroma']!r}")
    if after["stalkChroma"] == before["stalkChroma"]:
        failures.append(f"Mob ESP Stalk Tracer Color page chroma did not toggle value {before['stalkChroma']!r}")
    if filters_page != "MOB_ESP_FILTERS":
        failures.append(f"Mob ESP Edit Filters row expected MOB_ESP_FILTERS page, got {filters_page!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    restore_mob_esp_page_controls(client, before)
    restored = mob_esp_page_state(client.get("/state"))
    if restored != before:
        raise SystemExit(f"Mob ESP page restore expected {before!r}, got {restored!r}")

    return {
        "renderClickHandled": render_click.get("handled"),
        "configClickHandled": config_click.get("handled"),
        "tracersClickHandled": tracers_click.get("handled"),
        "hitboxesClickHandled": hitboxes_click.get("handled"),
        "starClickHandled": star_click.get("handled"),
        "defaultColorClickHandled": default_color_click.get("handled"),
        "defaultChromaClickHandled": default_chroma_click.get("handled"),
        "defaultApplyClickHandled": default_apply_click.get("handled"),
        "tracerColorClickHandled": tracer_color_click.get("handled"),
        "tracerChromaClickHandled": tracer_chroma_click.get("handled"),
        "tracerApplyClickHandled": tracer_apply_click.get("handled"),
        "filtersClickHandled": filters_click.get("handled"),
        "entriesContainExpected": not missing,
        "filtersPage": filters_page,
        "before": before,
        "after": after,
        "restored": restored,
    }


def verify_name_mappings_page_controls(client: "BridgeClient") -> dict[str, Any]:
    real_name = "FloydPageReal"
    fake_name = "FloydPageFake"
    state = open_name_mappings_page(client, "floyd-verify-name-mappings")
    if real_name in nick_name_mappings(state):
        remove_name_mapping_via_page(client, real_name)
        state = open_name_mappings_page(client)
        if real_name in nick_name_mappings(state):
            raise SystemExit(f"Could not prepare Name Mappings page without {real_name!r}")

    entries = name_mapping_page_entries(state)
    add_manual = find_name_mapping_page_entry(state, "ADD_MANUAL")
    add_click = client.post("/mouse", mouse_payload(add_manual["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    real_editor = nested(state, "legacyGui", "textEditor")
    type_real = client.post("/type", {"clear": True, "text": real_name, "submit": True})
    time.sleep(0.15)
    state = client.get("/state")
    fake_editor = nested(state, "legacyGui", "textEditor")
    type_fake = client.post("/type", {"clear": True, "text": fake_name, "submit": True})
    time.sleep(0.15)
    state = client.get("/state")
    mappings_after_add = nick_name_mappings(state)
    if mappings_after_add.get(real_name) != fake_name:
        raise SystemExit(f"Name Mappings add expected {real_name!r}->{fake_name!r}, got {mappings_after_add!r}")

    state = open_name_mappings_page(client)
    reveal_entry = find_name_mapping_page_entry(state, "REVEAL", real_name=real_name)
    reveal_click = client.post("/mouse", mouse_payload(reveal_entry["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    remove_entry = find_name_mapping_page_entry(state, "REMOVE", real_name=real_name)
    remove_click = client.post("/mouse", mouse_payload(remove_entry["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    mappings_after_remove = nick_name_mappings(state)

    failures: list[str] = []
    if add_click.get("handled") is not True:
        failures.append("Name Mappings Add Mapping page click was not handled")
    if type_real.get("handled") is not True:
        failures.append("Name Mappings real-name editor type was not handled")
    if type_fake.get("handled") is not True:
        failures.append("Name Mappings fake-name editor type was not handled")
    if reveal_click.get("handled") is not True:
        failures.append("Name Mappings reveal page click was not handled")
    if remove_click.get("handled") is not True:
        failures.append("Name Mappings remove page click was not handled")
    if real_editor != "Map Real Name":
        failures.append(f"Name Mappings first editor expected 'Map Real Name', got {real_editor!r}")
    if fake_editor != f"Fake Name for {real_name}":
        failures.append(f"Name Mappings second editor expected Fake Name for {real_name!r}, got {fake_editor!r}")
    if real_name in mappings_after_remove:
        failures.append(f"Name Mappings remove expected {real_name!r} absent, got {mappings_after_remove!r}")
    if not any(entry.get("kind") == "ADD_MANUAL" for entry in entries):
        failures.append(f"Name Mappings page entries missing ADD_MANUAL; got {entries!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    return {
        "addClickHandled": add_click.get("handled"),
        "typeRealHandled": type_real.get("handled"),
        "typeFakeHandled": type_fake.get("handled"),
        "revealClickHandled": reveal_click.get("handled"),
        "removeClickHandled": remove_click.get("handled"),
        "realEditor": real_editor,
        "fakeEditor": fake_editor,
        "afterAddValue": mappings_after_add.get(real_name),
        "afterRemoveContains": real_name in mappings_after_remove,
        "entriesContainAddManual": True,
    }


def verify_mob_esp_filters_page_controls(client: "BridgeClient") -> dict[str, Any]:
    test_name = "FloydPageMob"
    state = open_mob_esp_filters_page(client, "floyd-verify-mob-filters")
    if test_name in mob_name_filters(state):
        remove_mob_filter_via_page(client, test_name)
        state = open_mob_esp_filters_page(client)
        if test_name in mob_name_filters(state):
            raise SystemExit(f"Could not prepare Mob ESP Filters page without {test_name!r}")

    entries = mob_filter_page_entries(state)
    add_name = find_mob_filter_page_entry(state, "ADD_MANUAL_NAME")
    add_click = client.post("/mouse", mouse_payload(add_name["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    editor = nested(state, "legacyGui", "textEditor")
    type_result = client.post("/type", {"clear": True, "text": test_name, "submit": True})
    time.sleep(0.15)
    state = client.get("/state")
    filters_after_add = mob_name_filters(state)
    if test_name not in filters_after_add:
        raise SystemExit(f"Mob ESP Filters page add expected {test_name!r} in {filters_after_add!r}")

    state = open_mob_esp_filters_page(client)
    color_entry = find_mob_filter_page_entry(state, "COLOR", key=test_name)
    color_click = client.post("/mouse", mouse_payload(color_entry["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    expanded_color = nested(state, "legacyGui", "mobFilterEditor", "expandedColor")
    chroma_entry = find_mob_filter_page_entry(state, "PICKER_CHROMA", key=test_name)
    chroma_click = client.post("/mouse", mouse_payload(chroma_entry["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    remove_entry = find_mob_filter_page_entry(state, "REMOVE_NAME", key=test_name)
    remove_click = client.post("/mouse", mouse_payload(remove_entry["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    filters_after_remove = mob_name_filters(state)

    failures: list[str] = []
    if add_click.get("handled") is not True:
        failures.append("Mob ESP Filters Add Name page click was not handled")
    if type_result.get("handled") is not True:
        failures.append("Mob ESP Filters Add Name editor type was not handled")
    if color_click.get("handled") is not True:
        failures.append("Mob ESP Filters color page click was not handled")
    if chroma_click.get("handled") is not True:
        failures.append("Mob ESP Filters chroma page click was not handled")
    if remove_click.get("handled") is not True:
        failures.append("Mob ESP Filters remove page click was not handled")
    if editor != "Add Mob Name Filter":
        failures.append(f"Mob ESP Filters editor expected 'Add Mob Name Filter', got {editor!r}")
    if not isinstance(expanded_color, dict) or expanded_color.get("key") != test_name or expanded_color.get("isName") is not True:
        failures.append(f"Mob ESP Filters expanded color expected {test_name!r} name target, got {expanded_color!r}")
    if test_name in filters_after_remove:
        failures.append(f"Mob ESP Filters remove expected {test_name!r} absent, got {filters_after_remove!r}")
    if not any(entry.get("kind") == "ADD_MANUAL_NAME" for entry in entries):
        failures.append(f"Mob ESP Filters page entries missing ADD_MANUAL_NAME; got {entries!r}")
    if failures:
        raise SystemExit("\n".join(failures))

    return {
        "addNameClickHandled": add_click.get("handled"),
        "typeHandled": type_result.get("handled"),
        "colorClickHandled": color_click.get("handled"),
        "chromaClickHandled": chroma_click.get("handled"),
        "removeClickHandled": remove_click.get("handled"),
        "editor": editor,
        "afterAddContains": test_name in filters_after_add,
        "afterRemoveContains": test_name in filters_after_remove,
        "expandedColor": expanded_color,
        "entriesContainAddName": True,
    }


def restore_stalk_target(client: "BridgeClient", original: str) -> None:
    if original:
        set_stalk_target_via_popup(client, original)
    else:
        state = open_legacy_browser(client)
        click_browser_entry(client, state, "Stalk Player", button=0)
        time.sleep(0.15)
    restored = nested(client.get("/state"), "render", "mobEsp", "stalkTarget")
    if restored != original:
        raise SystemExit(f"Stalk Player Target restore expected {original!r}, got {restored!r}")


def set_stalk_target_via_popup(client: "BridgeClient", value: str) -> None:
    state = open_legacy_browser(client)
    click_browser_entry(client, state, "Stalk Player", button=1)
    state = client.get("/state")
    popup = require_popup(state, "Stalk Player")
    click_popup_extra(client, popup, "Target:")
    state = client.get("/state")
    popup = require_popup(state, "Stalk Player")
    click_stalk_target_input(client, popup, submit=False)
    client.post("/type", {"clear": True, "text": value, "submit": True})
    time.sleep(0.15)


def remove_xray_block_via_popup(client: "BridgeClient", block_id: str) -> None:
    state = open_legacy_browser(client)
    click_browser_entry(client, state, "X-Ray", button=1)
    state = client.get("/state")
    popup = require_popup(state, "X-Ray")
    click_popup_extra(client, popup, "Edit Blocks")
    state = client.get("/state")
    popup = require_popup(state, "X-Ray")
    click_xray_block_row(client, popup, block_id, add=False)
    time.sleep(0.15)


def add_xray_block_via_popup(client: "BridgeClient", block_id: str) -> None:
    state = open_legacy_browser(client)
    click_browser_entry(client, state, "X-Ray", button=1)
    state = client.get("/state")
    popup = require_popup(state, "X-Ray")
    click_popup_extra(client, popup, "Edit Blocks")
    state = client.get("/state")
    popup = require_popup(state, "X-Ray")
    click_xray_block_input(client, popup, submit=False)
    client.post("/type", {"clear": True, "text": block_id, "submit": True})
    time.sleep(0.15)


def remove_mob_name_filter_via_popup(client: "BridgeClient", name: str) -> None:
    state = open_legacy_browser(client)
    click_browser_entry(client, state, "Mob ESP", button=1)
    state = client.get("/state")
    popup = require_popup(state, "Mob ESP")
    click_popup_extra(client, popup, "Edit Filters")
    state = client.get("/state")
    popup = require_popup(state, "Mob ESP")
    click_mob_filter_row(client, popup, name, "REMOVE_NAME")
    time.sleep(0.15)


def remove_nick_mapping_via_popup(client: "BridgeClient", real_name: str) -> None:
    state = open_legacy_browser(client)
    click_browser_entry(client, state, "Neck Hider", button=1)
    state = client.get("/state")
    popup = require_popup(state, "Neck Hider")
    click_popup_extra(client, popup, "Edit Names")
    state = client.get("/state")
    popup = require_popup(state, "Neck Hider")
    click_name_mapping_entry(client, popup, "REMOVE", real_name=real_name)
    time.sleep(0.15)


def restore_popup_selector(
    client: "BridgeClient",
    popup_label: str,
    setting_name: str,
    original: Any,
    options: list[str],
) -> None:
    if original not in options:
        return
    for _ in range(len(options)):
        state = client.get("/state")
        popup = require_popup(state, popup_label)
        current = popup_setting_value(popup, setting_name)
        if current == original:
            return
        click_popup_setting(client, popup, setting_name)
    state = client.get("/state")
    restored = popup_setting_value(require_popup(state, popup_label), setting_name)
    if restored != original:
        raise SystemExit(f"{popup_label} {setting_name} selector restore expected {original!r}, got {restored!r}")


def open_legacy_browser(client: "BridgeClient") -> dict[str, Any]:
    state = open_legacy_hub(client)
    button = nested(state, "legacyGui", "hubButtons", "clickGui")
    if not isinstance(button, dict):
        raise SystemExit("Legacy /fa hub ClickGUI button is missing from debug state")
    client.post("/mouse", mouse_payload(button, button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "CLICK_GUI":
        raise SystemExit(f"Expected legacy GUI page CLICK_GUI, got {nested(state, 'legacyGui', 'page')!r}")
    return state


def open_legacy_hub(client: "BridgeClient") -> dict[str, Any]:
    client.post("/screen", {"screen": "floyd"})
    time.sleep(0.3)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "HUB":
        client.post("/screen", {"screen": "close"})
        time.sleep(0.1)
        client.post("/screen", {"screen": "floyd"})
        time.sleep(0.3)
        state = client.get("/state")
    if nested(state, "legacyGui", "page") != "HUB":
        raise SystemExit(f"Expected legacy GUI page HUB, got {nested(state, 'legacyGui', 'page')!r}")
    return state


def gui_style_flags(state: dict[str, Any]) -> dict[str, bool]:
    chroma = nested(state, "misc", "clickGui", "legacyButtonTextChroma")
    fade = nested(state, "misc", "clickGui", "legacyButtonTextFade")
    if chroma is None and fade is None:
        chroma = nested(state, "clickGui", "legacyButtonTextChroma")
        fade = nested(state, "clickGui", "legacyButtonTextFade")
    if not isinstance(chroma, bool) or not isinstance(fade, bool):
        raise SystemExit(f"GUI Style Button Text state expected booleans, got chroma={chroma!r} fade={fade!r}")
    return {"chroma": chroma, "fade": fade}


def cosmetic_page_state(state: dict[str, Any]) -> dict[str, Any]:
    custom_skin = nested(state, "cosmetics", "skin", "settings", "customSkin")
    cape = nested(state, "cosmetics", "cape", "capeEnabled")
    cone = nested(state, "cosmetics", "coneHat", "coneHatEnabled")
    target = nested(state, "playerFeatures", "playerSize", "settings", "sizeTarget")
    scale_x = nested(state, "playerFeatures", "playerSize", "settings", "scaleX")
    scale_y = nested(state, "playerFeatures", "playerSize", "settings", "scaleY")
    scale_z = nested(state, "playerFeatures", "playerSize", "settings", "scaleZ")
    if not isinstance(custom_skin, bool) or not isinstance(cape, bool) or not isinstance(cone, bool):
        raise SystemExit(f"Cosmetic page state expected booleans, got customSkin={custom_skin!r} cape={cape!r} cone={cone!r}")
    values = {"scaleX": scale_x, "scaleY": scale_y, "scaleZ": scale_z}
    bad = {key: value for key, value in values.items() if not isinstance(value, (int, float))}
    if not isinstance(target, str) or bad:
        raise SystemExit(f"Cosmetic player size state expected target/numbers, got target={target!r} bad={bad!r}")
    return {
        "customSkin": custom_skin,
        "cape": cape,
        "cone": cone,
        "target": target,
        "scaleX": round(float(scale_x), 2),
        "scaleY": round(float(scale_y), 2),
        "scaleZ": round(float(scale_z), 2),
    }


def player_size_page_state(state: dict[str, Any]) -> dict[str, Any]:
    enabled = nested(state, "playerFeatures", "playerSize", "enabled")
    target = nested(state, "playerFeatures", "playerSize", "settings", "sizeTarget")
    scale_x = nested(state, "playerFeatures", "playerSize", "settings", "scaleX")
    scale_y = nested(state, "playerFeatures", "playerSize", "settings", "scaleY")
    scale_z = nested(state, "playerFeatures", "playerSize", "settings", "scaleZ")
    if not isinstance(enabled, bool):
        raise SystemExit(f"Player Size page enabled state expected boolean, got {enabled!r}")
    if not isinstance(target, str):
        raise SystemExit(f"Player Size page target expected string, got {target!r}")
    values = {"scaleX": scale_x, "scaleY": scale_y, "scaleZ": scale_z}
    bad = {key: value for key, value in values.items() if not isinstance(value, (int, float))}
    if bad:
        raise SystemExit(f"Player Size page scale state expected numbers, got {bad!r}")
    return {
        "enabled": enabled,
        "target": target,
        "scaleX": round(float(scale_x), 2),
        "scaleY": round(float(scale_y), 2),
        "scaleZ": round(float(scale_z), 2),
    }


def skin_page_state(state: dict[str, Any]) -> dict[str, Any]:
    custom_skin = nested(state, "cosmetics", "skin", "settings", "customSkin")
    self_enabled = nested(state, "cosmetics", "skin", "settings", "self")
    others_enabled = nested(state, "cosmetics", "skin", "settings", "others")
    selected = nested(state, "cosmetics", "skin", "settings", "selectedSkin")
    available = nested(state, "cosmetics", "skin", "settings", "availableSkins")
    if not isinstance(custom_skin, bool) or not isinstance(self_enabled, bool) or not isinstance(others_enabled, bool):
        raise SystemExit(
            f"Skin page state expected booleans, got customSkin={custom_skin!r} self={self_enabled!r} others={others_enabled!r}"
        )
    if not isinstance(selected, str):
        raise SystemExit(f"Skin page selected skin expected string, got {selected!r}")
    if not isinstance(available, list):
        raise SystemExit(f"Skin page available skins expected list, got {available!r}")
    return {
        "customSkin": custom_skin,
        "self": self_enabled,
        "others": others_enabled,
        "selected": selected,
        "available": [item for item in available if isinstance(item, str)],
    }


def cape_page_state(state: dict[str, Any]) -> dict[str, Any]:
    selected = nested(state, "cosmetics", "cape", "selectedCape")
    available = nested(state, "cosmetics", "cape", "availableCapes")
    if not isinstance(selected, str):
        raise SystemExit(f"Cape page selected cape expected string, got {selected!r}")
    if not isinstance(available, list):
        raise SystemExit(f"Cape page available capes expected list, got {available!r}")
    return {
        "selected": selected,
        "available": [item for item in available if isinstance(item, str)],
    }


def cone_hat_page_state(state: dict[str, Any]) -> dict[str, Any]:
    selected = nested(state, "cosmetics", "coneHat", "selectedImage")
    available = nested(state, "cosmetics", "coneHat", "availableImages")
    height = nested(state, "cosmetics", "coneHat", "height")
    radius = nested(state, "cosmetics", "coneHat", "radius")
    y_offset = nested(state, "cosmetics", "coneHat", "yOffset")
    rotation = nested(state, "cosmetics", "coneHat", "rotation")
    spin = nested(state, "cosmetics", "coneHat", "rotationSpeed")
    if not isinstance(selected, str):
        raise SystemExit(f"Cone Hat page selected image expected string, got {selected!r}")
    if not isinstance(available, list):
        raise SystemExit(f"Cone Hat page available images expected list, got {available!r}")
    values = {"height": height, "radius": radius, "yOffset": y_offset, "rotation": rotation, "spinSpeed": spin}
    bad = {key: value for key, value in values.items() if not isinstance(value, (int, float))}
    if bad:
        raise SystemExit(f"Cone Hat page numeric state expected numbers, got {bad!r}")
    return {
        "selected": selected,
        "available": [item for item in available if isinstance(item, str)],
        "height": round(float(height), 3),
        "radius": round(float(radius), 3),
        "yOffset": round(float(y_offset), 3),
        "rotation": round(float(rotation), 3),
        "spinSpeed": round(float(spin), 3),
    }


def cone_hat_page_proof_state(state: dict[str, Any]) -> dict[str, Any]:
    return {
        "selected": state["selected"],
        "height": state["height"],
        "radius": state["radius"],
        "yOffset": state["yOffset"],
        "rotation": state["rotation"],
        "spinSpeed": state["spinSpeed"],
    }


def cone_hat_page_matches(current: dict[str, Any], desired: dict[str, Any]) -> bool:
    return (
        current["selected"] == desired["selected"]
        and abs(current["height"] - desired["height"]) <= 0.01
        and abs(current["radius"] - desired["radius"]) <= 0.01
        and abs(current["yOffset"] - desired["yOffset"]) <= 0.01
        and abs(current["rotation"] - desired["rotation"]) <= 0.01
        and abs(current["spinSpeed"] - desired["spinSpeed"]) <= 0.01
    )


def camera_page_state(state: dict[str, Any]) -> dict[str, Any]:
    speed = nested(state, "camera", "features", "freecamSpeed")
    f5 = nested(state, "camera", "features", "f5")
    if not isinstance(f5, dict):
        raise SystemExit(f"Camera page F5 state expected object, got {f5!r}")
    disable_front = f5.get("disableFront")
    disable_back = f5.get("disableBack")
    no_clip = f5.get("noClip")
    scroll_enabled = f5.get("scrollEnabled")
    reset_on_toggle = f5.get("resetOnToggle")
    f5_distance = nested(state, "camera", "features", "f5", "distance")
    if not isinstance(speed, (int, float)) or not isinstance(f5_distance, (int, float)):
        raise SystemExit(f"Camera page state expected numbers, got speed={speed!r} f5Distance={f5_distance!r}")
    booleans = {
        "disableFront": disable_front,
        "disableBack": disable_back,
        "noClip": no_clip,
        "scrollEnabled": scroll_enabled,
        "resetOnToggle": reset_on_toggle,
    }
    bad = {key: value for key, value in booleans.items() if not isinstance(value, bool)}
    if bad:
        raise SystemExit(f"Camera page F5 states expected booleans, got {bad!r}")
    return {
        "speed": round(float(speed), 2),
        "disableFront": disable_front,
        "disableBack": disable_back,
        "noClip": no_clip,
        "scrollEnabled": scroll_enabled,
        "resetOnToggle": reset_on_toggle,
        "f5Distance": round(float(f5_distance), 2),
    }


def hiders_page_state(state: dict[str, Any]) -> dict[str, Any]:
    settings = nested(state, "render", "hiders", "settings")
    if not isinstance(settings, dict):
        raise SystemExit(f"Hiders page settings expected object, got {settings!r}")
    result: dict[str, Any] = {}
    for name, key, kind in hiders_page_controls():
        value = settings.get(key)
        if kind == "NO_ARMOR":
            if not isinstance(value, str):
                raise SystemExit(f"Hiders page {name} state expected string, got {value!r}")
        elif not isinstance(value, bool):
            raise SystemExit(f"Hiders page {name} state expected boolean, got {value!r}")
        result[key] = value
    return result


def render_page_state(state: dict[str, Any]) -> dict[str, Any]:
    server_id = nested(state, "render", "hiders", "settings", "serverIdHider")
    profile_id = nested(state, "render", "hiders", "settings", "profileIdHider")
    xray_enabled = nested(state, "render", "xray", "xrayEnabled")
    xray_opacity = nested(state, "render", "xray", "opacity")
    mob_enabled = nested(state, "render", "mobEsp", "enabled")
    time_enabled = nested(state, "render", "core", "customTime")
    time_value = nested(state, "render", "core", "customTimeValue")
    borderless = nested(state, "render", "core", "borderlessWindowed")
    title = nested(state, "render", "core", "windowTitle")
    booleans = {
        "serverIdHider": server_id,
        "profileIdHider": profile_id,
        "xrayEnabled": xray_enabled,
        "mobEnabled": mob_enabled,
        "timeEnabled": time_enabled,
        "borderlessWindowed": borderless,
    }
    bad_bools = {key: value for key, value in booleans.items() if not isinstance(value, bool)}
    values = {"xrayOpacity": xray_opacity, "timeValue": time_value}
    bad_numbers = {key: value for key, value in values.items() if not isinstance(value, (int, float))}
    if bad_bools or bad_numbers or not isinstance(title, str):
        raise SystemExit(f"Render page state expected booleans/numbers/title, got booleans={bad_bools!r} numbers={bad_numbers!r} title={title!r}")
    return {
        "serverIdHider": server_id,
        "profileIdHider": profile_id,
        "xrayEnabled": xray_enabled,
        "xrayOpacity": round(float(xray_opacity), 3),
        "mobEnabled": mob_enabled,
        "timeEnabled": time_enabled,
        "timeValue": round(float(time_value), 2),
        "borderlessWindowed": borderless,
        "windowTitle": title,
    }


def render_page_matches(current: dict[str, Any], desired: dict[str, Any]) -> bool:
    return (
        current["serverIdHider"] == desired["serverIdHider"]
        and current["profileIdHider"] == desired["profileIdHider"]
        and current["xrayEnabled"] == desired["xrayEnabled"]
        and abs(float(current["xrayOpacity"]) - float(desired["xrayOpacity"])) <= 0.01
        and current["mobEnabled"] == desired["mobEnabled"]
        and current["timeEnabled"] == desired["timeEnabled"]
        and abs(float(current["timeValue"]) - float(desired["timeValue"])) <= 0.01
        and current["borderlessWindowed"] == desired["borderlessWindowed"]
        and current["windowTitle"] == desired["windowTitle"]
    )


def hiders_page_controls() -> list[tuple[str, str, str]]:
    return [
        ("No Hurt Camera", "noHurtCamera", "TOGGLE"),
        ("Remove Fire Overlay", "removeFireOverlay", "TOGGLE"),
        ("Hide Entity Fire", "hideEntityFire", "TOGGLE"),
        ("Disable Arrows", "disableAttachedArrows", "TOGGLE"),
        ("No Explosion Particles", "removeExplosionParticles", "TOGGLE"),
        ("Disable Hunger Bar", "disableHungerBar", "TOGGLE"),
        ("Hide Potion Effects", "hidePotionEffects", "TOGGLE"),
        ("3rd Person Crosshair", "thirdPersonCrosshair", "TOGGLE"),
        ("Remove Falling Blocks", "removeFallingBlocks", "TOGGLE"),
        ("Remove Tab Ping", "removeTabPing", "TOGGLE"),
        ("Target", "noArmorMode", "NO_ARMOR"),
    ]


def animations_page_state(state: dict[str, Any]) -> dict[str, Any]:
    enabled = nested(state, "render", "animations", "enabled")
    position_x = nested(state, "render", "animations", "settings", "posX")
    if position_x is None:
        position_x = nested(state, "render", "animations", "position", "x")
        if isinstance(position_x, (int, float)):
            position_x = float(position_x) * 100.0
    cancel_re_equip = nested(state, "render", "animations", "settings", "cancelReEquip")
    hide_hand = nested(state, "render", "animations", "settings", "hideHand")
    classic_click = nested(state, "render", "animations", "settings", "classicClick")
    if cancel_re_equip is None:
        cancel_re_equip = nested(state, "render", "animations", "cancelReEquip")
    if hide_hand is None:
        hide_hand = nested(state, "render", "animations", "hideEmptyMainHand")
    if classic_click is None:
        classic_click = nested(state, "render", "animations", "classicClick")
    if not isinstance(enabled, bool):
        raise SystemExit(f"Animations page state expected boolean, got enabled={enabled!r}")
    if not isinstance(position_x, (int, float)):
        raise SystemExit(f"Animations page Pos X state expected number, got {position_x!r}")
    if not isinstance(cancel_re_equip, bool) or not isinstance(hide_hand, bool) or not isinstance(classic_click, bool):
        raise SystemExit(
            "Animations page boolean state expected booleans, "
            f"got cancelReEquip={cancel_re_equip!r}, hideHand={hide_hand!r}, classicClick={classic_click!r}"
        )
    return {
        "enabled": enabled,
        "posX": round(float(position_x), 2),
        "cancelReEquip": cancel_re_equip,
        "hideHand": hide_hand,
        "classicClick": classic_click,
    }


def mob_esp_page_state(state: dict[str, Any]) -> dict[str, Any]:
    tracers = nested(state, "render", "mobEsp", "tracers")
    hitboxes = nested(state, "render", "mobEsp", "hitboxes")
    star_mobs = nested(state, "render", "mobEsp", "starMobs")
    default_chroma = nested(state, "render", "mobEsp", "settings", "defaultChroma")
    stalk_chroma = nested(state, "render", "mobEsp", "settings", "stalkChroma")
    if not all(isinstance(value, bool) for value in (tracers, hitboxes, star_mobs, default_chroma, stalk_chroma)):
        raise SystemExit(
            "Mob ESP page state expected booleans, "
            f"got tracers={tracers!r} hitboxes={hitboxes!r} starMobs={star_mobs!r} "
            f"defaultChroma={default_chroma!r} stalkChroma={stalk_chroma!r}"
        )
    return {
        "tracers": tracers,
        "hitboxes": hitboxes,
        "starMobs": star_mobs,
        "defaultChroma": default_chroma,
        "stalkChroma": stalk_chroma,
    }


def mob_esp_entries(state: dict[str, Any]) -> list[dict[str, Any]]:
    entries = nested(state, "legacyGui", "mobEspEditor", "entries")
    if not isinstance(entries, list):
        raise SystemExit("Mob ESP page entries are missing from debug state")
    return [entry for entry in entries if isinstance(entry, dict)]


def find_mob_esp_entry(state: dict[str, Any], kind: str, setting_name: str, required: bool = True) -> dict[str, Any] | None:
    for entry in mob_esp_entries(state):
        if entry.get("kind") == kind and entry.get("settingName") == setting_name:
            if not isinstance(entry.get("bounds"), dict):
                raise SystemExit(f"Mob ESP page entry {kind}/{setting_name} has no bounds")
            return entry
    if required:
        raise SystemExit(f"Could not find Mob ESP page entry {kind}/{setting_name}")
    return None


def mob_filter_page_entries(state: dict[str, Any]) -> list[dict[str, Any]]:
    entries = nested(state, "legacyGui", "mobFilterEditor", "entries")
    if not isinstance(entries, list):
        raise SystemExit("Mob ESP Filters page entries are missing from debug state")
    return [entry for entry in entries if isinstance(entry, dict)]


def find_mob_filter_page_entry(
    state: dict[str, Any],
    kind: str,
    key: str | None = None,
    required: bool = True,
) -> dict[str, Any] | None:
    for entry in mob_filter_page_entries(state):
        if entry.get("kind") == kind and (key is None or entry.get("key") == key):
            if not isinstance(entry.get("bounds"), dict):
                raise SystemExit(f"Mob ESP Filters page entry {kind}/{key} has no bounds")
            return entry
    if required:
        raise SystemExit(f"Could not find Mob ESP Filters page entry {kind}/{key}; entries={mob_filter_page_entries(state)!r}")
    return None


def name_mapping_page_entries(state: dict[str, Any]) -> list[dict[str, Any]]:
    entries = nested(state, "legacyGui", "nameMappingEditor", "entries")
    if not isinstance(entries, list):
        raise SystemExit("Name Mappings page entries are missing from debug state")
    return [entry for entry in entries if isinstance(entry, dict)]


def find_name_mapping_page_entry(
    state: dict[str, Any],
    kind: str,
    real_name: str | None = None,
    required: bool = True,
) -> dict[str, Any] | None:
    for entry in name_mapping_page_entries(state):
        if entry.get("kind") == kind and (real_name is None or entry.get("realName") == real_name):
            if not isinstance(entry.get("bounds"), dict):
                raise SystemExit(f"Name Mappings page entry {kind}/{real_name} has no bounds")
            return entry
    if required:
        raise SystemExit(f"Could not find Name Mappings page entry {kind}/{real_name}")
    return None


def xray_page_entries(state: dict[str, Any]) -> list[dict[str, Any]]:
    entries = nested(state, "legacyGui", "xrayEditor", "entries")
    if not isinstance(entries, list):
        raise SystemExit("X-Ray page entries are missing from debug state")
    return [entry for entry in entries if isinstance(entry, dict)]


def find_xray_page_entry(state: dict[str, Any], block_id: str, add: bool, required: bool = True) -> dict[str, Any] | None:
    for entry in xray_page_entries(state):
        if (entry.get("blockId") == block_id or entry.get("block") == block_id) and entry.get("add") is add:
            if not isinstance(entry.get("bounds"), dict):
                raise SystemExit(f"X-Ray page entry {block_id!r} add={add!r} has no bounds")
            return entry
    if required:
        raise SystemExit(f"Could not find X-Ray page entry {block_id!r} add={add!r}")
    return None


def animations_entries(state: dict[str, Any]) -> list[dict[str, Any]]:
    entries = nested(state, "legacyGui", "animationsEditor", "entries")
    if not isinstance(entries, list):
        raise SystemExit("Animations page entries are missing from debug state")
    return [entry for entry in entries if isinstance(entry, dict)]


def find_animations_entry(state: dict[str, Any], kind: str, setting_name: str, required: bool = True) -> dict[str, Any] | None:
    for entry in animations_entries(state):
        if entry.get("kind") == kind and entry.get("settingName") == setting_name:
            if not isinstance(entry.get("bounds"), dict):
                raise SystemExit(f"Animations page entry {kind}/{setting_name} has no bounds")
            return entry
    if required:
        raise SystemExit(f"Could not find Animations page entry {kind}/{setting_name}")
    return None


def hiders_entries(state: dict[str, Any]) -> list[dict[str, Any]]:
    entries = nested(state, "legacyGui", "hidersEditor", "entries")
    if not isinstance(entries, list):
        raise SystemExit("Hiders page entries are missing from debug state")
    return [entry for entry in entries if isinstance(entry, dict)]


def find_hiders_entry(state: dict[str, Any], kind: str, setting_name: str, required: bool = True) -> dict[str, Any] | None:
    for entry in hiders_entries(state):
        if entry.get("kind") == kind and entry.get("settingName") == setting_name:
            if not isinstance(entry.get("bounds"), dict):
                raise SystemExit(f"Hiders page entry {kind}/{setting_name} has no bounds")
            return entry
    if required:
        raise SystemExit(f"Could not find Hiders page entry {kind}/{setting_name}")
    return None


def camera_entries(state: dict[str, Any]) -> list[dict[str, Any]]:
    entries = nested(state, "legacyGui", "cameraEditor", "entries")
    if not isinstance(entries, list):
        raise SystemExit("Camera page entries are missing from debug state")
    return [entry for entry in entries if isinstance(entry, dict)]


def find_camera_entry(state: dict[str, Any], kind: str, setting_name: str, required: bool = True) -> dict[str, Any] | None:
    for entry in camera_entries(state):
        if entry.get("kind") == kind and entry.get("settingName") == setting_name:
            if not isinstance(entry.get("bounds"), dict):
                raise SystemExit(f"Camera page entry {kind}/{setting_name} has no bounds")
            return entry
    if required:
        raise SystemExit(f"Could not find Camera page entry {kind}/{setting_name}")
    return None


def cosmetic_entries(state: dict[str, Any]) -> list[dict[str, Any]]:
    entries = nested(state, "legacyGui", "cosmeticEditor", "entries")
    if not isinstance(entries, list):
        raise SystemExit("Cosmetic page entries are missing from debug state")
    return [entry for entry in entries if isinstance(entry, dict)]


def find_cosmetic_entry(state: dict[str, Any], kind: str, setting_name: str, required: bool = True) -> dict[str, Any] | None:
    for entry in cosmetic_entries(state):
        if entry.get("kind") == kind and entry.get("settingName") == setting_name:
            if not isinstance(entry.get("bounds"), dict):
                raise SystemExit(f"Cosmetic page entry {kind}/{setting_name} has no bounds")
            return entry
    if required:
        raise SystemExit(f"Could not find Cosmetic page entry {kind}/{setting_name}")
    return None


def skin_entries(state: dict[str, Any]) -> list[dict[str, Any]]:
    entries = nested(state, "legacyGui", "skinSettingsEditor", "entries")
    if not isinstance(entries, list):
        raise SystemExit("Skin page entries are missing from debug state")
    return [entry for entry in entries if isinstance(entry, dict)]


def find_skin_entry(state: dict[str, Any], kind: str, setting_name: str, required: bool = True) -> dict[str, Any] | None:
    for entry in skin_entries(state):
        if entry.get("kind") == kind and entry.get("settingName") == setting_name:
            if not isinstance(entry.get("bounds"), dict):
                raise SystemExit(f"Skin page entry {kind}/{setting_name} has no bounds")
            return entry
    if required:
        raise SystemExit(f"Could not find Skin page entry {kind}/{setting_name}")
    return None


def open_skin_page(client: "BridgeClient", screenshot_prefix: str | None = None) -> dict[str, Any]:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Cosmetic")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Cosmetic label is missing from debug state before Skin page verification")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "COSMETIC":
        raise SystemExit(f"Expected legacy GUI page COSMETIC before Skin page, got {nested(state, 'legacyGui', 'page')!r}")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-cosmetic.png")
    nav = find_cosmetic_entry(state, "NAV_CONFIG", "SKIN")
    result = client.post("/mouse", mouse_payload(nav["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if result.get("handled") is not True:
        raise SystemExit("Cosmetic Skin Config navigation click was not handled")
    if nested(state, "legacyGui", "page") != "SKIN":
        raise SystemExit(f"Expected legacy GUI page SKIN, got {nested(state, 'legacyGui', 'page')!r}")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-skin.png")
    return state


def select_skin_dropdown_value(client: "BridgeClient", state: dict[str, Any], skin: str) -> dict[str, Any]:
    button = nested(state, "legacyGui", "skinDropdownButton")
    if not isinstance(button, dict):
        raise SystemExit("Skin page dropdown button is missing from debug state")
    open_result = client.post("/mouse", mouse_payload(button, button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "skinDropdownOpen") is not True:
        raise SystemExit("Skin page dropdown did not open")
    items = nested(state, "legacyGui", "skinDropdownItems")
    bounds = nested(state, "legacyGui", "skinDropdownBounds")
    if not isinstance(items, list) or not isinstance(bounds, dict):
        raise SystemExit("Skin page dropdown items/bounds are missing from debug state")
    try:
        index = [item for item in items if isinstance(item, str)].index(skin)
    except ValueError as exc:
        raise SystemExit(f"Skin page dropdown missing skin {skin!r}; items={items!r}") from exc
    bounds_height = float(bounds.get("height", float(bounds["bottom"]) - float(bounds["top"])))
    row_height = bounds_height / max(1, len(items))
    x = (float(bounds["left"]) + float(bounds["right"])) / 2.0
    y = float(bounds["top"]) + row_height * (index + 0.5)
    pick_result = client.post("/mouse", {"event": "click", "x": x, "y": y, "coordinateSpace": "gui", "button": 0})
    time.sleep(0.15)
    if open_result.get("handled") is not True or pick_result.get("handled") is not True:
        raise SystemExit(f"Skin page dropdown click was not handled: open={open_result!r} pick={pick_result!r}")
    return pick_result


def restore_skin_page_controls(client: "BridgeClient", desired: dict[str, Any]) -> None:
    for _ in range(16):
        state = open_skin_page(client)
        current = skin_page_state(state)
        if {key: current[key] for key in ("self", "others", "selected")} == {
            key: desired[key] for key in ("self", "others", "selected")
        }:
            return
        if current["self"] != desired["self"]:
            client.post("/mouse", mouse_payload(find_skin_entry(state, "TOGGLE", "Self")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["others"] != desired["others"]:
            client.post("/mouse", mouse_payload(find_skin_entry(state, "TOGGLE", "Others")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["selected"] != desired["selected"]:
            select_skin_dropdown_value(client, state, desired["selected"])
            time.sleep(0.15)
            continue
    state = client.get("/state")
    raise SystemExit(f"Could not restore Skin page controls to {desired!r}, got {skin_page_state(state)!r}")


def open_cape_page(client: "BridgeClient", screenshot_prefix: str | None = None) -> dict[str, Any]:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Cosmetic")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Cosmetic label is missing from debug state before Cape page verification")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "COSMETIC":
        raise SystemExit(f"Expected legacy GUI page COSMETIC before Cape page, got {nested(state, 'legacyGui', 'page')!r}")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-cosmetic.png")
    nav = find_cosmetic_entry(state, "NAV_CONFIG", "CAPE")
    result = client.post("/mouse", mouse_payload(nav["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if result.get("handled") is not True:
        raise SystemExit("Cosmetic Cape Config navigation click was not handled")
    if nested(state, "legacyGui", "page") != "CAPE":
        raise SystemExit(f"Expected legacy GUI page CAPE, got {nested(state, 'legacyGui', 'page')!r}")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-cape.png")
    return state


def cape_page_button(state: dict[str, Any], name: str) -> dict[str, Any]:
    button = nested(state, "legacyGui", "capeButtons", name)
    if not isinstance(button, dict):
        raise SystemExit(f"Cape page button {name!r} is missing from debug state")
    return button


def restore_cape_page_controls(client: "BridgeClient", desired: dict[str, Any]) -> None:
    for _ in range(max(4, len(desired["available"]) + 2)):
        state = open_cape_page(client)
        current = cape_page_state(state)
        if current["selected"] == desired["selected"]:
            return
        client.post("/mouse", mouse_payload(cape_page_button(state, "next"), button=0))
        time.sleep(0.15)
    state = client.get("/state")
    raise SystemExit(f"Could not restore Cape page controls to {desired!r}, got {cape_page_state(state)!r}")


def open_cone_hat_page(client: "BridgeClient", screenshot_prefix: str | None = None) -> dict[str, Any]:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Cosmetic")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Cosmetic label is missing from debug state before Cone Hat page verification")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "COSMETIC":
        raise SystemExit(f"Expected legacy GUI page COSMETIC before Cone Hat page, got {nested(state, 'legacyGui', 'page')!r}")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-cosmetic.png")
    nav = find_cosmetic_entry(state, "NAV_CONFIG", "CONE_HAT")
    result = client.post("/mouse", mouse_payload(nav["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if result.get("handled") is not True:
        raise SystemExit("Cosmetic Cone Hat Config navigation click was not handled")
    if nested(state, "legacyGui", "page") != "CONE_HAT":
        raise SystemExit(f"Expected legacy GUI page CONE_HAT, got {nested(state, 'legacyGui', 'page')!r}")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-cone.png")
    return state


def cone_controls(state: dict[str, Any]) -> dict[str, Any]:
    controls = nested(state, "legacyGui", "coneControls")
    if not isinstance(controls, dict):
        raise SystemExit("Cone Hat page controls are missing from debug state")
    sliders = controls.get("sliders")
    inputs = controls.get("inputs")
    if not isinstance(sliders, list) or len(sliders) < 5 or not isinstance(inputs, list) or len(inputs) < 5:
        raise SystemExit(f"Cone Hat page expected five sliders and inputs, got sliders={sliders!r} inputs={inputs!r}")
    return controls


def cone_control_rect(state: dict[str, Any], group: str, index: int) -> dict[str, Any]:
    rects = cone_controls(state).get(group)
    if not isinstance(rects, list) or not isinstance(rects[index], dict):
        raise SystemExit(f"Cone Hat page {group}[{index}] is missing from debug state")
    return rects[index]


def cone_button(state: dict[str, Any], name: str) -> dict[str, Any]:
    button = cone_controls(state).get(name)
    if not isinstance(button, dict):
        raise SystemExit(f"Cone Hat page button {name!r} is missing from debug state")
    return button


def click_cone_slider_at_fraction(client: "BridgeClient", state: dict[str, Any], index: int, fraction: float) -> dict[str, Any]:
    bounds = cone_control_rect(state, "sliders", index)
    width = float(bounds.get("width", float(bounds["right"]) - float(bounds["left"])))
    x = float(bounds["left"]) + max(0.0, min(1.0, fraction)) * width
    y = (float(bounds["top"]) + float(bounds["bottom"])) / 2.0
    result = client.post("/mouse", {"event": "click", "x": x, "y": y, "coordinateSpace": "gui", "button": 0})
    time.sleep(0.15)
    return result


def set_cone_input_value(client: "BridgeClient", state: dict[str, Any], index: int, value: float) -> dict[str, Any]:
    focus = client.post("/mouse", mouse_payload(cone_control_rect(state, "inputs", index), button=0))
    time.sleep(0.1)
    typed = client.post("/type", {"clear": True, "text": f"{value:.2f}", "submit": True})
    time.sleep(0.15)
    if focus.get("handled") is not True or typed.get("handled") is not True:
        raise SystemExit(f"Cone Hat page numeric input was not handled: focus={focus!r} type={typed!r}")
    return {"handled": True, "focusHandled": focus.get("handled"), "typeHandled": typed.get("handled")}


def select_cone_dropdown_value(client: "BridgeClient", state: dict[str, Any], image: str) -> dict[str, Any]:
    open_result = client.post("/mouse", mouse_payload(cone_button(state, "dropdownButton"), button=0))
    time.sleep(0.15)
    state = client.get("/state")
    controls = cone_controls(state)
    if controls.get("dropdownOpen") is not True:
        raise SystemExit("Cone Hat page dropdown did not open")
    items = controls.get("dropdownItems")
    bounds = controls.get("dropdownBounds")
    if not isinstance(items, list) or not isinstance(bounds, dict):
        raise SystemExit("Cone Hat page dropdown items/bounds are missing from debug state")
    try:
        index = [item for item in items if isinstance(item, str)].index(image)
    except ValueError as exc:
        raise SystemExit(f"Cone Hat page dropdown missing image {image!r}; items={items!r}") from exc
    bounds_height = float(bounds.get("height", float(bounds["bottom"]) - float(bounds["top"])))
    row_height = bounds_height / max(1, min(len(items), 5))
    x = (float(bounds["left"]) + float(bounds["right"])) / 2.0
    y = float(bounds["top"]) + row_height * (index + 0.5)
    pick_result = client.post("/mouse", {"event": "click", "x": x, "y": y, "coordinateSpace": "gui", "button": 0})
    time.sleep(0.15)
    if open_result.get("handled") is not True or pick_result.get("handled") is not True:
        raise SystemExit(f"Cone Hat page dropdown click was not handled: open={open_result!r} pick={pick_result!r}")
    return pick_result


def restore_cone_hat_page_controls(client: "BridgeClient", desired: dict[str, Any]) -> None:
    for _ in range(max(8, len(desired["available"]) + 4)):
        state = open_cone_hat_page(client)
        current = cone_hat_page_state(state)
        if cone_hat_page_matches(current, desired):
            return
        if current["selected"] != desired["selected"]:
            select_cone_dropdown_value(client, state, desired["selected"])
            time.sleep(0.15)
            continue
        if abs(current["height"] - desired["height"]) > 0.01:
            fraction = max(0.0, min(1.0, (desired["height"] - 0.1) / 1.4))
            click_cone_slider_at_fraction(client, state, 0, fraction)
            time.sleep(0.15)
            continue
        if abs(current["radius"] - desired["radius"]) > 0.01:
            set_cone_input_value(client, state, 1, desired["radius"])
            time.sleep(0.15)
            continue
        if abs(current["yOffset"] - desired["yOffset"]) > 0.01:
            set_cone_input_value(client, state, 2, desired["yOffset"])
            time.sleep(0.15)
            continue
        if abs(current["rotation"] - desired["rotation"]) > 0.01:
            set_cone_input_value(client, state, 3, desired["rotation"])
            time.sleep(0.15)
            continue
        if abs(current["spinSpeed"] - desired["spinSpeed"]) > 0.01:
            set_cone_input_value(client, state, 4, desired["spinSpeed"])
            time.sleep(0.15)
            continue
    state = client.get("/state")
    raise SystemExit(f"Could not restore Cone Hat page controls to {desired!r}, got {cone_hat_page_state(state)!r}")


def nick_hider_entries(state: dict[str, Any]) -> list[dict[str, Any]]:
    entries = nested(state, "legacyGui", "nickHiderEditor", "entries")
    if not isinstance(entries, list):
        raise SystemExit("Neck Hider page entries are missing from debug state")
    return [entry for entry in entries if isinstance(entry, dict)]


def find_nick_hider_entry(state: dict[str, Any], kind: str, required: bool = True) -> dict[str, Any] | None:
    for entry in nick_hider_entries(state):
        if entry.get("kind") == kind:
            if not isinstance(entry.get("bounds"), dict):
                raise SystemExit(f"Neck Hider page entry {kind} has no bounds")
            return entry
    if required:
        raise SystemExit(f"Could not find Neck Hider page entry {kind}")
    return None


def open_name_mappings_page(client: "BridgeClient", screenshot_prefix: str | None = None) -> dict[str, Any]:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Neck Hider")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Neck Hider label is missing from debug state before Name Mappings page verification")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "NICK_HIDER":
        raise SystemExit(f"Expected legacy GUI page NICK_HIDER before Name Mappings page, got {nested(state, 'legacyGui', 'page')!r}")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-nick-hider.png")
    nav = find_nick_hider_entry(state, "EDIT_NAMES")
    result = client.post("/mouse", mouse_payload(nav["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if result.get("handled") is not True:
        raise SystemExit("Neck Hider Edit Names navigation click was not handled")
    if nested(state, "legacyGui", "page") != "NAME_MAPPINGS":
        raise SystemExit(f"Expected legacy GUI page NAME_MAPPINGS, got {nested(state, 'legacyGui', 'page')!r}")
    if not name_mapping_page_entries(state):
        time.sleep(0.1)
        state = client.get("/state")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-page.png")
    return state


def remove_name_mapping_via_page(client: "BridgeClient", real_name: str) -> None:
    state = open_name_mappings_page(client)
    entry = find_name_mapping_page_entry(state, "REMOVE", real_name=real_name)
    client.post("/mouse", mouse_payload(entry["bounds"], button=0))
    time.sleep(0.15)


def open_mob_esp_filters_page(client: "BridgeClient", screenshot_prefix: str | None = None) -> dict[str, Any]:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Render")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Render label is missing from debug state before Mob ESP Filters page verification")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = wait_for_page_rows(client, "RENDER")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-render.png")
    config_row = find_page_row(state, "Config")
    result = click_render_page_row(client, state, config_row)
    time.sleep(0.15)
    state = client.get("/state")
    if result.get("handled") is not True:
        raise SystemExit("Render Mob ESP Config row click was not handled before Mob ESP Filters page")
    if nested(state, "legacyGui", "page") != "MOB_ESP":
        raise SystemExit(f"Expected legacy GUI page MOB_ESP before filters page, got {nested(state, 'legacyGui', 'page')!r}")
    nav = find_mob_esp_entry(state, "NAV_FILTERS", "Edit Filters")
    nav_result = client.post("/mouse", mouse_payload(nav["bounds"], button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nav_result.get("handled") is not True:
        raise SystemExit("Mob ESP Edit Filters navigation click was not handled")
    if nested(state, "legacyGui", "page") != "MOB_ESP_FILTERS":
        raise SystemExit(f"Expected legacy GUI page MOB_ESP_FILTERS, got {nested(state, 'legacyGui', 'page')!r}")
    if not mob_filter_page_entries(state):
        time.sleep(0.1)
        state = client.get("/state")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-page.png")
    return state


def remove_mob_filter_via_page(client: "BridgeClient", name: str) -> None:
    state = open_mob_esp_filters_page(client)
    entry = find_mob_filter_page_entry(state, "REMOVE_NAME", key=name)
    client.post("/mouse", mouse_payload(entry["bounds"], button=0))
    time.sleep(0.15)


def open_player_size_page(client: "BridgeClient", screenshot_prefix: str | None = None) -> dict[str, Any]:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Neck Hider")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Neck Hider label is missing from debug state before Player Size page verification")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = client.get("/state")
    if nested(state, "legacyGui", "page") != "NICK_HIDER":
        raise SystemExit(f"Expected legacy GUI page NICK_HIDER before Player Size page, got {nested(state, 'legacyGui', 'page')!r}")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-nick-hider.png")
    nav = find_nick_hider_entry(state, "PLAYER_SIZE")
    result = client.post("/mouse", mouse_payload(nav["bounds"], button=0))
    time.sleep(0.15)
    state = wait_for_page_rows(client, "PLAYER_SIZE")
    if result.get("handled") is not True:
        raise SystemExit("Neck Hider Player Size navigation click was not handled")
    if nested(state, "legacyGui", "page") != "PLAYER_SIZE":
        raise SystemExit(f"Expected legacy GUI page PLAYER_SIZE, got {nested(state, 'legacyGui', 'page')!r}")
    if screenshot_prefix:
        force_frame(client, f"{screenshot_prefix}-player-size.png")
    return state


def restore_player_size_page_controls(client: "BridgeClient", desired: dict[str, Any]) -> None:
    for _ in range(8):
        state = open_player_size_page(client)
        current = player_size_page_state(state)
        if (
            current["enabled"] == desired["enabled"]
            and current["target"] == desired["target"]
            and abs(float(current["scaleX"]) - float(desired["scaleX"])) <= 0.01
            and abs(float(current["scaleY"]) - float(desired["scaleY"])) <= 0.01
            and abs(float(current["scaleZ"]) - float(desired["scaleZ"])) <= 0.01
        ):
            return
        if current["enabled"] != desired["enabled"]:
            client.post("/mouse", mouse_payload(find_page_row(state, "Player Size:")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["target"] != desired["target"]:
            client.post("/mouse", mouse_payload(find_page_row(state, "Target:")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if abs(float(current["scaleX"]) - float(desired["scaleX"])) > 0.01:
            button = 1 if float(current["scaleX"]) > float(desired["scaleX"]) else 0
            client.post("/mouse", mouse_payload(find_page_row(state, "Size X:")["bounds"], button=button))
            time.sleep(0.15)
            continue
        if abs(float(current["scaleY"]) - float(desired["scaleY"])) > 0.01:
            button = 1 if float(current["scaleY"]) > float(desired["scaleY"]) else 0
            client.post("/mouse", mouse_payload(find_page_row(state, "Size Y:")["bounds"], button=button))
            time.sleep(0.15)
            continue
        if abs(float(current["scaleZ"]) - float(desired["scaleZ"])) > 0.01:
            button = 1 if float(current["scaleZ"]) > float(desired["scaleZ"]) else 0
            client.post("/mouse", mouse_payload(find_page_row(state, "Size Z:")["bounds"], button=button))
            time.sleep(0.15)
            continue
    state = client.get("/state")
    raise SystemExit(f"Could not restore Player Size page controls to {desired!r}, got {player_size_page_state(state)!r}")


def restore_cosmetic_page_controls(client: "BridgeClient", desired: dict[str, Any]) -> None:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Cosmetic")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Cosmetic label is missing from debug state during restore")
    force_frame(client, "floyd-verify-cosmetic-restore-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    force_frame(client, "floyd-verify-cosmetic-restore-page.png")
    for _ in range(12):
        state = client.get("/state")
        current = cosmetic_page_state(state)
        if (
            current["customSkin"] == desired["customSkin"]
            and current["cape"] == desired["cape"]
            and current["cone"] == desired["cone"]
            and current["target"] == desired["target"]
            and abs(float(current["scaleX"]) - float(desired["scaleX"])) <= 0.06
            and abs(float(current["scaleY"]) - float(desired["scaleY"])) <= 0.06
            and abs(float(current["scaleZ"]) - float(desired["scaleZ"])) <= 0.06
        ):
            return
        if current["customSkin"] != desired["customSkin"]:
            client.post("/mouse", mouse_payload(find_cosmetic_entry(state, "TOGGLE_SKIN", "Custom Skin")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["cape"] != desired["cape"]:
            client.post("/mouse", mouse_payload(find_cosmetic_entry(state, "TOGGLE_CAPE", "Cape")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["cone"] != desired["cone"]:
            client.post("/mouse", mouse_payload(find_cosmetic_entry(state, "TOGGLE_CONE", "Cone Hat")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["target"] != desired["target"]:
            client.post("/mouse", mouse_payload(find_cosmetic_entry(state, "TARGET", "Target")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if abs(float(current["scaleX"]) - float(desired["scaleX"])) > 0.01:
            entry = find_cosmetic_entry(state, "SLIDER", "X")
            fraction = max(0.0, min(1.0, (float(desired["scaleX"]) + 1.0) / 6.0))
            click_rect_slider_at_fraction(client, entry, fraction)
            time.sleep(0.15)
            continue
        if abs(float(current["scaleY"]) - float(desired["scaleY"])) > 0.01:
            entry = find_cosmetic_entry(state, "SLIDER", "Y")
            fraction = max(0.0, min(1.0, (float(desired["scaleY"]) + 1.0) / 6.0))
            click_rect_slider_at_fraction(client, entry, fraction)
            time.sleep(0.15)
            continue
        if abs(float(current["scaleZ"]) - float(desired["scaleZ"])) > 0.01:
            entry = find_cosmetic_entry(state, "SLIDER", "Z")
            fraction = max(0.0, min(1.0, (float(desired["scaleZ"]) + 1.0) / 6.0))
            click_rect_slider_at_fraction(client, entry, fraction)
            time.sleep(0.15)
            continue
    state = client.get("/state")
    raise SystemExit(f"Could not restore Cosmetic page controls to {desired!r}, got {cosmetic_page_state(state)!r}")


def restore_camera_page_controls(client: "BridgeClient", desired: dict[str, Any]) -> None:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Camera")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Camera label is missing from debug state during restore")
    force_frame(client, "floyd-verify-camera-restore-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    force_frame(client, "floyd-verify-camera-restore-page.png")
    for _ in range(12):
        state = client.get("/state")
        current = camera_page_state(state)
        if current == desired:
            return
        if current["disableFront"] != desired["disableFront"]:
            client.post("/mouse", mouse_payload(find_camera_entry(state, "BOOLEAN_TOGGLE", "Disable Front Cam")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["disableBack"] != desired["disableBack"]:
            client.post("/mouse", mouse_payload(find_camera_entry(state, "BOOLEAN_TOGGLE", "Disable Back Cam")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["noClip"] != desired["noClip"]:
            client.post("/mouse", mouse_payload(find_camera_entry(state, "BOOLEAN_TOGGLE", "No Third-Person Clipping")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["scrollEnabled"] != desired["scrollEnabled"]:
            client.post("/mouse", mouse_payload(find_camera_entry(state, "BOOLEAN_TOGGLE", "Scrolling Changes Distance")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["resetOnToggle"] != desired["resetOnToggle"]:
            client.post("/mouse", mouse_payload(find_camera_entry(state, "BOOLEAN_TOGGLE", "Reset F5 Scrolling")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if abs(float(current["speed"]) - float(desired["speed"])) > 0.01:
            fraction = max(0.0, min(1.0, (float(desired["speed"]) - 0.1) / 9.9))
            click_camera_slider_at_fraction(client, find_camera_entry(state, "SLIDER", "Speed"), fraction)
            time.sleep(0.15)
            continue
        if abs(float(current["f5Distance"]) - float(desired["f5Distance"])) > 0.01:
            fraction = max(0.0, min(1.0, (float(desired["f5Distance"]) - 1.0) / 19.0))
            click_camera_slider_at_fraction(client, find_camera_entry(state, "SLIDER", "Camera Distance"), fraction)
            time.sleep(0.15)
            continue
    state = client.get("/state")
    raise SystemExit(f"Could not restore Camera page controls to {desired!r}, got {camera_page_state(state)!r}")


def restore_hiders_page_controls(client: "BridgeClient", desired: dict[str, Any]) -> None:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Render")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Render label is missing from debug state during Hiders restore")
    force_frame(client, "floyd-verify-hiders-restore-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = wait_for_page_rows(client, "RENDER")
    click_render_page_row(client, state, find_page_row(state, "Hiders"))
    time.sleep(0.15)
    force_frame(client, "floyd-verify-hiders-restore-page.png")
    for _ in range(14):
        state = client.get("/state")
        current = hiders_page_state(state)
        if current == desired:
            return
        for name, key, kind in hiders_page_controls():
            if current[key] != desired[key]:
                client.post("/mouse", mouse_payload(find_hiders_entry(state, kind, name)["bounds"], button=0))
                time.sleep(0.15)
                break
        else:
            break
    state = client.get("/state")
    raise SystemExit(f"Could not restore Hiders page controls to {desired!r}, got {hiders_page_state(state)!r}")


def restore_animations_page_controls(client: "BridgeClient", desired: dict[str, Any]) -> None:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Render")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Render label is missing from debug state during Animations restore")
    force_frame(client, "floyd-verify-animations-restore-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = wait_for_page_rows(client, "RENDER")
    click_render_page_row(client, state, find_page_row(state, "Attack Animation"))
    time.sleep(0.15)
    force_frame(client, "floyd-verify-animations-restore-page.png")
    for _ in range(6):
        state = client.get("/state")
        current = animations_page_state(state)
        if current == desired:
            return
        if current["enabled"] != desired["enabled"]:
            client.post("/mouse", mouse_payload(find_animations_entry(state, "TOGGLE_MODULE", "")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if abs(float(current["posX"]) - float(desired["posX"])) > 0.01:
            fraction = max(0.0, min(1.0, (float(desired["posX"]) + 150.0) / 300.0))
            click_rect_slider_at_fraction(client, find_animations_entry(state, "SLIDER", "Pos X"), fraction)
            time.sleep(0.15)
            continue
        for setting_name, state_key in (
            ("Cancel Re-Equip", "cancelReEquip"),
            ("Hide Hand", "hideHand"),
            ("Classic Click", "classicClick"),
        ):
            if current[state_key] != desired[state_key]:
                client.post("/mouse", mouse_payload(find_animations_entry(state, "TOGGLE_SETTING", setting_name)["bounds"], button=0))
                time.sleep(0.15)
                break
        else:
            break
    state = client.get("/state")
    raise SystemExit(f"Could not restore Animations page controls to {desired!r}, got {animations_page_state(state)!r}")


def restore_mob_esp_page_controls(client: "BridgeClient", desired: dict[str, Any]) -> None:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Render")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Render label is missing from debug state during Mob ESP restore")
    force_frame(client, "floyd-verify-mob-esp-restore-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    state = wait_for_page_rows(client, "RENDER")
    click_render_page_row(client, state, find_page_row(state, "Config"))
    time.sleep(0.15)
    force_frame(client, "floyd-verify-mob-esp-restore-page.png")
    for _ in range(8):
        state = client.get("/state")
        current = mob_esp_page_state(state)
        if current == desired:
            return
        if current["tracers"] != desired["tracers"]:
            client.post("/mouse", mouse_payload(find_mob_esp_entry(state, "TOGGLE", "Tracers")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["starMobs"] != desired["starMobs"]:
            client.post("/mouse", mouse_payload(find_mob_esp_entry(state, "TOGGLE", "Star Mobs")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["hitboxes"] != desired["hitboxes"]:
            client.post("/mouse", mouse_payload(find_mob_esp_entry(state, "TOGGLE", "Hitboxes")["bounds"], button=0))
            time.sleep(0.15)
            continue
        for setting_name, title, state_key in (
            ("Default ESP Color", "Default ESP", "defaultChroma"),
            ("Tracer Color", "Stalk Tracer", "stalkChroma"),
        ):
            if current[state_key] == desired[state_key]:
                continue
            client.post("/mouse", mouse_payload(find_mob_esp_entry(state, "COLOR_PICK", setting_name)["bounds"], button=0))
            time.sleep(0.15)
            color_state = client.get("/state")
            if nested(color_state, "legacyGui", "colorPicker") != title:
                raise SystemExit(f"Mob ESP restore expected {title} color picker, got {nested(color_state, 'legacyGui', 'colorPicker')!r}")
            bounds = nested(color_state, "legacyGui", "colorPickerBounds")
            if not isinstance(bounds, dict):
                raise SystemExit(f"Mob ESP restore {title} color picker bounds missing")
            click_color_picker_button(client, bounds, "chroma")
            color_state = client.get("/state")
            bounds = nested(color_state, "legacyGui", "colorPickerBounds")
            if not isinstance(bounds, dict):
                raise SystemExit(f"Mob ESP restore {title} color picker closed before apply")
            click_color_picker_button(client, bounds, "apply")
            time.sleep(0.15)
            break
    state = client.get("/state")
    raise SystemExit(f"Could not restore Mob ESP page controls to {desired!r}, got {mob_esp_page_state(state)!r}")


def click_color_picker_button(client: "BridgeClient", bounds: dict[str, Any], name: str) -> dict[str, Any]:
    button = bounds.get(name)
    if not isinstance(button, dict):
        raise SystemExit(f"Color picker {name!r} button bounds missing")
    result = client.post("/mouse", mouse_payload(button, button=0))
    time.sleep(0.15)
    return result


def restore_gui_style_flags(client: "BridgeClient", desired: dict[str, bool]) -> None:
    for _ in range(3):
        state = open_legacy_hub(client)
        current = gui_style_flags(state)
        if current == desired:
            return
        button = nested(state, "legacyGui", "hubButtons", "editUi")
        if not isinstance(button, dict):
            raise SystemExit("Legacy /fa hub Edit UI button is missing from debug state")
        client.post("/mouse", mouse_payload(button, button=0))
        time.sleep(0.15)
        state = client.get("/state")
        entries = nested(state, "legacyGui", "guiStyleEditor", "entries")
        if not isinstance(entries, list):
            raise SystemExit("GUI Style editor entries are missing from debug state during restore")
        pick_entry = next((entry for entry in entries if isinstance(entry, dict) and entry.get("target") == "TEXT"), None)
        if not isinstance(pick_entry, dict) or not isinstance(pick_entry.get("bounds"), dict):
            raise SystemExit("GUI Style Button Text picker entry is missing bounds during restore")
        client.post("/mouse", mouse_payload(pick_entry["bounds"], button=0))
        time.sleep(0.15)
        state = client.get("/state")
        bounds = nested(state, "legacyGui", "colorPickerBounds")
        if not isinstance(bounds, dict):
            raise SystemExit("GUI Style color picker bounds missing during restore")
        if current["fade"] != desired["fade"]:
            click_color_picker_button(client, bounds, "fade")
            state = client.get("/state")
            bounds = nested(state, "legacyGui", "colorPickerBounds")
            if not isinstance(bounds, dict):
                raise SystemExit("GUI Style color picker closed during fade restore")
        if current["chroma"] != desired["chroma"]:
            click_color_picker_button(client, bounds, "chroma")
            state = client.get("/state")
            bounds = nested(state, "legacyGui", "colorPickerBounds")
            if not isinstance(bounds, dict):
                raise SystemExit("GUI Style color picker closed during chroma restore")
        click_color_picker_button(client, bounds, "apply")
    state = client.get("/state")
    raise SystemExit(f"Could not restore GUI Style Button Text state to {desired!r}, got {gui_style_flags(state)!r}")


def page_row_labels(state: dict[str, Any]) -> list[str]:
    rows = nested(state, "legacyGui", "rows")
    if not isinstance(rows, list):
        raise SystemExit("Legacy page rows are missing from debug state")
    if not rows and nested(state, "legacyGui", "page") == "RENDER":
        return ["Server ID Hider", "Profile ID Hider", "X-Ray", "Mob ESP", "Other", "Time Changer", "Time"]
    labels: list[str] = []
    for row in rows:
        if isinstance(row, dict) and isinstance(row.get("label"), str):
            labels.append(row["label"])
    return labels


def wait_for_page_rows(client: "BridgeClient", page: str) -> dict[str, Any]:
    state = client.get("/state")
    for _ in range(20):
        if nested(state, "legacyGui", "page") == page:
            rows = nested(state, "legacyGui", "rows")
            if isinstance(rows, list) and rows:
                return state
        time.sleep(0.1)
        state = client.get("/state")
    if nested(state, "legacyGui", "page") == page and page == "RENDER":
        return state
    raise SystemExit(f"Legacy GUI page {page} rows were not populated; rows={nested(state, 'legacyGui', 'rows')!r}")


def find_page_row(state: dict[str, Any], label_prefix: str) -> dict[str, Any]:
    rows = nested(state, "legacyGui", "rows")
    if not isinstance(rows, list):
        raise SystemExit("Legacy page rows are missing from debug state")
    for row in rows:
        if isinstance(row, dict) and isinstance(row.get("label"), str) and row["label"].startswith(label_prefix):
            bounds = row.get("bounds")
            if not isinstance(bounds, dict):
                raise SystemExit(f"Legacy page row {label_prefix!r} has no bounds")
            return {"label": row["label"], "bounds": bounds}
    fallback = render_page_row_fallback(state, label_prefix)
    if fallback is not None:
        return fallback
    raise SystemExit(f"Could not find legacy page row starting with {label_prefix!r}; rows={page_row_labels(state)!r}")


def render_entries(state: dict[str, Any]) -> list[dict[str, Any]]:
    entries = nested(state, "legacyGui", "renderEditor", "entries")
    if not isinstance(entries, list):
        raise SystemExit("Legacy render editor entries are missing from debug state")
    return [entry for entry in entries if isinstance(entry, dict)]


def find_render_entry(state: dict[str, Any], kind: str, setting_name: str, required: bool = True) -> dict[str, Any] | None:
    for entry in render_entries(state):
        if entry.get("kind") == kind and entry.get("settingName") == setting_name:
            bounds = entry.get("bounds")
            if not isinstance(bounds, dict):
                raise SystemExit(f"Render entry {kind!r}/{setting_name!r} has no bounds")
            return {"kind": kind, "settingName": setting_name, "bounds": bounds}
    if required:
        raise SystemExit(f"Could not find render entry {kind!r}/{setting_name!r}; entries={[(entry.get('kind'), entry.get('settingName')) for entry in render_entries(state)]!r}")
    return None


def render_page_row_fallback(state: dict[str, Any], label_prefix: str) -> dict[str, Any] | None:
    if nested(state, "legacyGui", "page") != "RENDER":
        return None
    panel = nested(state, "legacyGui", "panel")
    if not isinstance(panel, dict):
        return None
    left = int(panel["left"])
    top = int(panel["top"])
    content_left = left + (int(panel["width"]) - 240) // 2
    content_top = top + 26
    row_height = 18
    row_gap = 5
    half_gap = 4
    half_width = (240 - half_gap) // 2
    line_top = content_top + 10 * (row_height + row_gap)
    rows = {
        "Time Changer:": {
            "label": "Time Changer",
            "bounds": {
                "left": content_left,
                "top": line_top,
                "right": content_left + half_width,
                "bottom": line_top + row_height,
                "width": half_width,
                "height": row_height,
            },
        },
        "Time:": {
            "label": "Time",
            "bounds": {
                "left": content_left + half_width + half_gap,
                "top": line_top,
                "right": content_left + half_width + half_gap + half_width,
                "bottom": line_top + row_height,
                "width": half_width,
                "height": row_height,
            },
        },
    }
    return rows.get(label_prefix)


def restore_render_page_controls(client: "BridgeClient", desired: dict[str, Any]) -> None:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Render")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Render label is missing from debug state during restore")
    force_frame(client, "floyd-verify-render-restore-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    force_frame(client, "floyd-verify-render-restore-page.png")
    for _ in range(16):
        state = client.get("/state")
        current = render_page_state(state)
        if render_page_matches(current, desired):
            return
        if current["serverIdHider"] != desired["serverIdHider"]:
            client.post("/mouse", mouse_payload(find_render_entry(state, "BOOLEAN_TOGGLE", "Server ID Hider")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["profileIdHider"] != desired["profileIdHider"]:
            client.post("/mouse", mouse_payload(find_render_entry(state, "BOOLEAN_TOGGLE", "Profile ID Hider")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["xrayEnabled"] != desired["xrayEnabled"]:
            client.post("/mouse", mouse_payload(find_render_entry(state, "XRAY_TOGGLE", "X-Ray")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if abs(float(current["xrayOpacity"]) - float(desired["xrayOpacity"])) > 0.01:
            fraction = max(0.0, min(1.0, (float(desired["xrayOpacity"]) - 0.05) / 0.95))
            click_rect_slider_at_fraction(client, find_render_entry(state, "SLIDER", "Opacity"), fraction)
            time.sleep(0.15)
            continue
        if current["mobEnabled"] != desired["mobEnabled"]:
            client.post("/mouse", mouse_payload(find_render_entry(state, "MODULE_TOGGLE", "Mob ESP")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if current["timeEnabled"] != desired["timeEnabled"]:
            client.post("/mouse", mouse_payload(find_render_entry(state, "BOOLEAN_TOGGLE", "Time Changer")["bounds"], button=0))
            time.sleep(0.15)
            continue
        if abs(float(current["timeValue"]) - float(desired["timeValue"])) > 0.01:
            fraction = max(0.0, min(1.0, float(desired["timeValue"]) / 100.0))
            click_rect_slider_at_fraction(client, find_render_entry(state, "SLIDER", "Time"), fraction)
            time.sleep(0.15)
            continue
        if current["borderlessWindowed"] != desired["borderlessWindowed"]:
            client.post("/mouse", mouse_payload(find_render_entry(state, "BORDERLESS", "Borderless Window")["bounds"], button=0))
            time.sleep(0.15)
            continue
    state = client.get("/state")
    raise SystemExit(f"Could not restore Render page controls to {desired!r}, got {render_page_state(state)!r}")


def restore_render_page_time_controls(client: "BridgeClient", enabled: bool, value: float) -> None:
    state = open_legacy_hub(client)
    label = nested(state, "legacyGui", "labels", "Render")
    if not isinstance(label, dict):
        raise SystemExit("Legacy /fa hub Render label is missing from debug state during restore")
    force_frame(client, "floyd-verify-render-restore-hub.png")
    client.post("/mouse", mouse_payload(label, button=0))
    time.sleep(0.15)
    force_frame(client, "floyd-verify-render-restore-page.png")
    for _ in range(4):
        state = client.get("/state")
        current_enabled = nested(state, "render", "core", "customTime")
        current_value = nested(state, "render", "core", "customTimeValue")
        if current_enabled == enabled and isinstance(current_value, (int, float)) and abs(float(current_value) - value) <= 0.01:
            return
        if current_enabled != enabled:
            row = find_page_row(state, "Time Changer:")
            click_render_page_row(client, state, row)
            time.sleep(0.15)
            continue
        if isinstance(current_value, (int, float)) and abs(float(current_value) - value) > 0.01:
            y_offset = render_page_click_y_offset(state)
            correction = 3.0 if y_offset else 0.0
            fraction = max(0.0, min(1.0, (value - correction) / 100.0))
            click_page_number_at_fraction(client, find_page_row(state, "Time:"), fraction, y_offset=y_offset)
            continue
    state = client.get("/state")
    raise SystemExit(
        f"Could not restore Render page time controls to {enabled!r}/{value!r}, "
        f"got {nested(state, 'render', 'core', 'customTime')!r}/{nested(state, 'render', 'core', 'customTimeValue')!r}"
    )


def click_render_page_row(client: "BridgeClient", state: dict[str, Any], row: dict[str, Any]) -> dict[str, Any]:
    bounds = row["bounds"]
    x = (float(bounds["left"]) + float(bounds["right"])) / 2.0
    y = (float(bounds["top"]) + float(bounds["bottom"])) / 2.0 + render_page_click_y_offset(state)
    result = client.post("/mouse", {"event": "click", "x": x, "y": y, "coordinateSpace": "gui", "button": 0})
    time.sleep(0.15)
    return result


def click_camera_slider_at_fraction(client: "BridgeClient", entry: dict[str, Any], fraction: float) -> dict[str, Any]:
    return click_rect_slider_at_fraction(client, entry, fraction)


def click_rect_slider_at_fraction(client: "BridgeClient", entry: dict[str, Any], fraction: float) -> dict[str, Any]:
    bounds = entry["bounds"]
    width = float(bounds.get("width", float(bounds["right"]) - float(bounds["left"])))
    x = float(bounds["left"]) + max(0.0, min(1.0, fraction)) * width
    y = (float(bounds["top"]) + float(bounds["bottom"])) / 2.0
    result = client.post("/mouse", {"event": "click", "x": x, "y": y, "coordinateSpace": "gui", "button": 0})
    time.sleep(0.15)
    return result


def render_page_click_y_offset(state: dict[str, Any]) -> float:
    return 23.0 if isinstance(state.get("window"), dict) else 0.0


def click_page_number_at_fraction(client: "BridgeClient", row: dict[str, Any], fraction: float, y_offset: float = 0.0) -> dict[str, Any]:
    bounds = row["bounds"]
    width = float(bounds.get("width", float(bounds["right"]) - float(bounds["left"])))
    x = float(bounds["left"]) + 8.0 + max(0.0, min(1.0, fraction)) * (width - 16.0)
    y = (float(bounds["top"]) + float(bounds["bottom"])) / 2.0 + y_offset
    result = client.post("/mouse", {"event": "click", "x": x, "y": y, "coordinateSpace": "gui", "button": 0})
    time.sleep(0.15)
    return result


def force_frame(client: "BridgeClient", file_name: str) -> None:
    client.post("/screenshot", {"fileName": file_name})
    time.sleep(0.1)


def click_browser_entry(client: "BridgeClient", state: dict[str, Any], label: str, button: int) -> dict[str, Any]:
    result: dict[str, Any] = {}
    attempts = 4 if button == 1 else 1
    current_state = state
    for attempt in range(attempts):
        entry = find_browser_entry(current_state, label)
        result = client.post("/mouse", mouse_payload(entry["bounds"], button=button))
        time.sleep(0.2)
        if button != 1:
            return result
        after = client.get("/state")
        popup = nested(after, "legacyGui", "modulePopup")
        if isinstance(popup, dict) and popup.get("displayName") == label:
            return result
        if attempt + 1 < attempts:
            current_state = open_legacy_browser(client)
    return result


def find_browser_entry(state: dict[str, Any], label: str) -> dict[str, Any]:
    entries = nested(state, "legacyGui", "moduleBrowser", "entries")
    if not isinstance(entries, list):
        raise SystemExit("Legacy module browser entries are missing from debug state")
    for entry in entries:
        if isinstance(entry, dict) and entry.get("displayName") == label:
            return entry
    raise SystemExit(f"Could not find legacy module browser entry {label!r}")


def require_popup(state: dict[str, Any], label: str) -> dict[str, Any]:
    popup = nested(state, "legacyGui", "modulePopup")
    if not isinstance(popup, dict):
        raise SystemExit(f"Expected {label} module popup, got no popup")
    if popup.get("displayName") != label:
        raise SystemExit(f"Expected {label} module popup, got {popup.get('displayName')!r}")
    return popup


def popup_setting_names(popup: dict[str, Any]) -> list[str]:
    entries = popup.get("entries")
    if not isinstance(entries, list):
        return []
    return [entry.get("settingName") for entry in entries if isinstance(entry, dict)]


def popup_extra_labels(popup: dict[str, Any]) -> list[str]:
    entries = popup.get("extraEntries")
    if not isinstance(entries, list):
        return []
    return [entry.get("label") for entry in entries if isinstance(entry, dict)]


def click_popup_extra(client: "BridgeClient", popup: dict[str, Any], label_prefix: str) -> dict[str, Any]:
    entry = find_popup_extra(popup, label_prefix)
    bounds = entry.get("bounds")
    if not isinstance(bounds, dict):
        raise SystemExit(f"Popup extra {label_prefix!r} has no bounds")
    result = client.post("/mouse", mouse_payload(bounds, button=0))
    time.sleep(0.15)
    return result


def find_popup_extra(popup: dict[str, Any], label_prefix: str) -> dict[str, Any]:
    entries = popup.get("extraEntries")
    if not isinstance(entries, list):
        raise SystemExit(f"Popup {popup.get('displayName')!r} has no extra entries")
    for entry in entries:
        if isinstance(entry, dict) and isinstance(entry.get("label"), str) and entry["label"].startswith(label_prefix):
            return entry
    raise SystemExit(f"Could not find popup extra starting with {label_prefix!r} in {popup.get('displayName')!r}")


def click_stalk_target_input(client: "BridgeClient", popup: dict[str, Any], submit: bool) -> dict[str, Any]:
    entries = popup.get("playerEntries")
    if not isinstance(entries, list):
        raise SystemExit("Stalk Player popup has no player input entries")
    for entry in entries:
        if isinstance(entry, dict) and entry.get("target") is None and entry.get("submit") is submit:
            bounds = entry.get("bounds")
            if not isinstance(bounds, dict):
                raise SystemExit("Stalk Player target input entry has no bounds")
            result = client.post("/mouse", mouse_payload(bounds, button=0))
            time.sleep(0.15)
            return result
    raise SystemExit(f"Could not find Stalk Player target input submit={submit!r}")


def click_xray_block_input(client: "BridgeClient", popup: dict[str, Any], submit: bool) -> dict[str, Any]:
    entries = popup.get("xrayEntries")
    if not isinstance(entries, list):
        raise SystemExit("X-Ray Edit Blocks popup has no xray input entries")
    for entry in entries:
        if isinstance(entry, dict) and entry.get("action") == "XRAY_ADD_BLOCK" and entry.get("submit") is submit:
            bounds = entry.get("bounds")
            if not isinstance(bounds, dict):
                raise SystemExit("X-Ray Add Block input entry has no bounds")
            result = client.post("/mouse", mouse_payload(bounds, button=0))
            time.sleep(0.15)
            return result
    raise SystemExit(f"Could not find X-Ray Add Block input submit={submit!r}")


def click_xray_block_row(client: "BridgeClient", popup: dict[str, Any], block_id: str, add: bool) -> dict[str, Any]:
    entries = popup.get("xrayEntries")
    if not isinstance(entries, list):
        raise SystemExit("X-Ray Edit Blocks popup has no xray block entries")
    for entry in entries:
        if isinstance(entry, dict) and entry.get("block") == block_id and entry.get("add") is add:
            bounds = entry.get("bounds")
            if not isinstance(bounds, dict):
                raise SystemExit(f"X-Ray block row {block_id!r} has no bounds")
            result = client.post("/mouse", mouse_payload(bounds, button=0))
            time.sleep(0.15)
            return result
    raise SystemExit(f"Could not find X-Ray block row {block_id!r} add={add!r}")


def click_mob_filter_input(client: "BridgeClient", popup: dict[str, Any], kind: str, submit: bool) -> dict[str, Any]:
    entries = popup.get("mobFilterEntries")
    if not isinstance(entries, list):
        raise SystemExit("Mob ESP Edit Filters popup has no mob filter input entries")
    for entry in entries:
        if isinstance(entry, dict) and entry.get("kind") == kind and entry.get("submit") is submit:
            bounds = entry.get("bounds")
            if not isinstance(bounds, dict):
                raise SystemExit(f"Mob ESP filter input {kind!r} has no bounds")
            result = client.post("/mouse", mouse_payload(bounds, button=0))
            time.sleep(0.15)
            return result
    raise SystemExit(f"Could not find Mob ESP filter input kind={kind!r} submit={submit!r}")


def click_mob_filter_row(client: "BridgeClient", popup: dict[str, Any], key: str, kind: str) -> dict[str, Any]:
    entries = popup.get("mobFilterEntries")
    if not isinstance(entries, list):
        raise SystemExit("Mob ESP Edit Filters popup has no mob filter entries")
    for entry in entries:
        if isinstance(entry, dict) and entry.get("key") == key and entry.get("kind") == kind:
            bounds = entry.get("bounds")
            if not isinstance(bounds, dict):
                raise SystemExit(f"Mob ESP filter row {key!r} has no bounds")
            result = client.post("/mouse", mouse_payload(bounds, button=0))
            time.sleep(0.15)
            return result
    raise SystemExit(f"Could not find Mob ESP filter row {key!r} kind={kind!r}")


def click_name_mapping_entry(
    client: "BridgeClient",
    popup: dict[str, Any],
    kind: str,
    real_name: str | None = None,
) -> dict[str, Any]:
    entries = popup.get("nameMappingEntries")
    if not isinstance(entries, list):
        raise SystemExit("Neck Hider Edit Names popup has no name mapping entries")
    for entry in entries:
        if not isinstance(entry, dict) or entry.get("kind") != kind:
            continue
        if real_name is not None and entry.get("realName") != real_name:
            continue
        bounds = entry.get("bounds")
        if not isinstance(bounds, dict):
            raise SystemExit(f"Neck Hider name mapping entry {kind!r} has no bounds")
        result = client.post("/mouse", mouse_payload(bounds, button=0))
        time.sleep(0.15)
        return result
    suffix = f" realName={real_name!r}" if real_name is not None else ""
    raise SystemExit(f"Could not find Neck Hider name mapping entry kind={kind!r}{suffix}")


def popup_setting_value(popup: dict[str, Any], setting_name: str) -> Any:
    return find_popup_setting(popup, setting_name).get("value")


def popup_setting_chroma(popup: dict[str, Any], setting_name: str) -> Any:
    return find_popup_setting(popup, setting_name).get("chroma")


def popup_setting_options(popup: dict[str, Any], setting_name: str) -> list[str]:
    options = find_popup_setting(popup, setting_name).get("options")
    if not isinstance(options, list):
        return []
    return [option for option in options if isinstance(option, str)]


def xray_opaque_blocks(state: dict[str, Any]) -> list[str]:
    blocks = nested(state, "render", "xray", "opaqueBlocks")
    if not isinstance(blocks, list) or not all(isinstance(block, str) for block in blocks):
        raise SystemExit(f"X-Ray state missing opaqueBlocks list, got {blocks!r}")
    return blocks


def mob_name_filters(state: dict[str, Any]) -> list[str]:
    names = nested(state, "render", "mobEsp", "nameFilters")
    if not isinstance(names, list) or not all(isinstance(name, str) for name in names):
        raise SystemExit(f"Mob ESP state missing nameFilters list, got {names!r}")
    return names


def nick_name_mappings(state: dict[str, Any]) -> dict[str, str]:
    mappings = nested(state, "playerFeatures", "nickHider", "settings", "nameMappings")
    if not isinstance(mappings, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in mappings.items()):
        raise SystemExit(f"Neck Hider state missing nameMappings map, got {mappings!r}")
    return dict(mappings)


def click_popup_setting(client: "BridgeClient", popup: dict[str, Any], setting_name: str) -> dict[str, Any]:
    entry = find_popup_setting(popup, setting_name)
    bounds = entry.get("bounds")
    if not isinstance(bounds, dict):
        raise SystemExit(f"Popup setting {setting_name!r} has no bounds")
    result = client.post("/mouse", mouse_payload(bounds, button=0))
    time.sleep(0.15)
    return result


def click_popup_color_chroma(client: "BridgeClient", popup: dict[str, Any], setting_name: str) -> dict[str, Any]:
    entry = find_popup_setting(popup, setting_name)
    bounds = entry.get("bounds")
    if not isinstance(bounds, dict):
        raise SystemExit(f"Popup setting {setting_name!r} has no bounds")
    sv_left = float(bounds["left"]) + 8.0
    sv_top = float(bounds["top"]) + 18.0 + 6.0
    sv_bottom = sv_top + 100.0
    chroma_left = float(bounds["right"]) - 82.0
    chroma_top = sv_bottom + 6.0
    result = client.post(
        "/mouse",
        {
            "event": "click",
            "x": chroma_left + 37.0,
            "y": chroma_top + 9.0,
            "coordinateSpace": "gui",
            "button": 0,
        },
    )
    time.sleep(0.15)
    return result


def click_popup_number_at_fraction(
    client: "BridgeClient",
    popup: dict[str, Any],
    setting_name: str,
    fraction: float,
) -> dict[str, Any]:
    entry = find_popup_setting(popup, setting_name)
    bounds = entry.get("bounds")
    if not isinstance(bounds, dict):
        raise SystemExit(f"Popup setting {setting_name!r} has no bounds")
    width = float(bounds.get("width", float(bounds["right"]) - float(bounds["left"])))
    x = float(bounds["left"]) + 8.0 + max(0.0, min(1.0, fraction)) * (width - 16.0)
    y = float(bounds["bottom"]) - 8.0
    result = client.post("/mouse", {"event": "click", "x": x, "y": y, "coordinateSpace": "gui", "button": 0})
    time.sleep(0.15)
    return result


def find_popup_setting(popup: dict[str, Any], setting_name: str) -> dict[str, Any]:
    entries = popup.get("entries")
    if not isinstance(entries, list):
        raise SystemExit(f"Popup {popup.get('displayName')!r} has no setting entries")
    for entry in entries:
        if isinstance(entry, dict) and entry.get("settingName") == setting_name:
            return entry
    raise SystemExit(f"Could not find popup setting {setting_name!r} in {popup.get('displayName')!r}")


def mouse_payload(rect: dict[str, Any], button: int) -> dict[str, Any]:
    x = (float(rect["left"]) + float(rect["right"])) / 2.0
    y = (float(rect["top"]) + float(rect["bottom"])) / 2.0
    return {"event": "click", "x": x, "y": y, "coordinateSpace": "gui", "button": button}


def nested(data: dict[str, Any], *keys: str) -> Any:
    current: Any = data
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


class BridgeClient:
    def __init__(self, bridge: Any, host: str, port: int, token: str, timeout: float) -> None:
        self.bridge = bridge
        self.host = host
        self.port = port
        self.token = token
        self.timeout = timeout

    def get(self, path: str) -> dict[str, Any]:
        return self.bridge.request_json(self.host, self.port, self.token, "GET", path, self.timeout)

    def post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        return self.bridge.request_json(self.host, self.port, self.token, "POST", path, self.timeout, payload)


if __name__ == "__main__":
    sys.exit(main())
