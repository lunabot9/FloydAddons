#!/usr/bin/env python3
"""Offline tests for verify-legacy-clickgui-runtime.py."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path
from typing import Any


def load_verifier() -> Any:
    path = Path(__file__).with_name("verify-legacy-clickgui-runtime.py")
    spec = importlib.util.spec_from_file_location("legacy_clickgui_verifier", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class LegacyClickGuiRuntimeVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.verifier = load_verifier()
        self.verifier.time.sleep = lambda _seconds: None

    def test_accepts_old_module_row_click_semantics(self) -> None:
        proof = self.verifier.verify_legacy_clickgui(FakeClient())

        self.assertEqual("X-Ray", proof["xrayLeftClick"]["popup"])
        self.assertEqual(False, proof["xrayLeftClick"]["before"])
        self.assertEqual(True, proof["xrayLeftClick"]["after"])
        self.assertEqual(["Opacity"], proof["xrayLeftClick"]["entries"])
        self.assertEqual(["Edit Blocks"], proof["xrayLeftClick"]["extras"])
        self.assertEqual(0.5, proof["xrayLeftClick"]["opacitySliderClick"]["before"])
        self.assertNotEqual(0.5, proof["xrayLeftClick"]["opacitySliderClick"]["after"])
        self.assertAlmostEqual(0.5, FakeClient.latest.xray_opacity, places=2)
        self.assertEqual("X-Ray", proof["xrayEditBlocksExtraInput"]["popup"])
        self.assertEqual("XRAY_BLOCKS", proof["xrayEditBlocksExtraInput"]["expandedExtra"])
        self.assertEqual("minecraft:barrier", proof["xrayEditBlocksExtraInput"]["block"])
        self.assertTrue(proof["xrayEditBlocksExtraInput"]["afterAddContains"])
        self.assertFalse(proof["xrayEditBlocksExtraInput"]["afterRemoveContains"])
        self.assertNotIn("minecraft:barrier", FakeClient.latest.xray_opaque_blocks)
        self.assertEqual("Mob ESP", proof["mobEspRightClick"]["popup"])
        self.assertEqual(False, proof["mobEspRightClick"]["before"])
        self.assertEqual(False, proof["mobEspRightClick"]["after"])
        self.assertEqual(["Tracers", "Hitboxes", "Star Mobs", "Default ESP Color"], proof["mobEspRightClick"]["entries"])
        self.assertEqual(["Edit Filters"], proof["mobEspRightClick"]["extras"])
        self.assertEqual(False, proof["mobEspRightClick"]["tracersPopupClick"]["before"])
        self.assertEqual(True, proof["mobEspRightClick"]["tracersPopupClick"]["after"])
        self.assertFalse(FakeClient.latest.mob_tracers)
        self.assertEqual(False, proof["mobEspRightClick"]["defaultEspColorChromaClick"]["before"])
        self.assertEqual(True, proof["mobEspRightClick"]["defaultEspColorChromaClick"]["after"])
        self.assertFalse(FakeClient.latest.mob_default_chroma)
        self.assertEqual("Mob ESP", proof["mobEspEditFiltersExtraInput"]["popup"])
        self.assertEqual("MOB_FILTERS", proof["mobEspEditFiltersExtraInput"]["expandedExtra"])
        self.assertEqual("FloydVerifierMob", proof["mobEspEditFiltersExtraInput"]["name"])
        self.assertTrue(proof["mobEspEditFiltersExtraInput"]["afterAddContains"])
        self.assertFalse(proof["mobEspEditFiltersExtraInput"]["afterRemoveContains"])
        self.assertNotIn("FloydVerifierMob", FakeClient.latest.mob_name_filters)
        self.assertTrue(proof["xrayPageControls"]["entriesPresent"])
        self.assertEqual("minecraft:barrier", proof["xrayPageControls"]["block"])
        self.assertTrue(proof["xrayPageControls"]["removeClickHandled"])
        self.assertFalse(proof["xrayPageControls"]["afterContains"])
        self.assertEqual(proof["xrayPageControls"]["beforeContains"], proof["xrayPageControls"]["restoredContains"])
        self.assertNotIn("minecraft:barrier", FakeClient.latest.xray_opaque_blocks)
        self.assertEqual("Instance Name", proof["instanceTitleStringEdit"]["popup"])
        self.assertEqual(["Instance Title"], proof["instanceTitleStringEdit"]["entries"])
        self.assertEqual("", proof["instanceTitleStringEdit"]["before"])
        self.assertEqual("Floyd Runtime Title", proof["instanceTitleStringEdit"]["after"])
        self.assertEqual("", FakeClient.latest.instance_title)
        self.assertEqual(["Speed"], proof["cameraPopupControls"]["freecam"]["entries"])
        self.assertNotEqual(
            proof["cameraPopupControls"]["freecam"]["speedClick"]["before"],
            proof["cameraPopupControls"]["freecam"]["speedClick"]["after"],
        )
        self.assertEqual(["Distance"], proof["cameraPopupControls"]["freelook"]["entries"])
        self.assertNotEqual(
            proof["cameraPopupControls"]["freelook"]["distanceClick"]["before"],
            proof["cameraPopupControls"]["freelook"]["distanceClick"]["after"],
        )
        self.assertEqual(
            ["Disable Front Cam", "Disable Back Cam", "No Third-Person Clipping", "Scrolling Changes Distance", "Reset F5 Scrolling", "Camera Distance"],
            proof["cameraPopupControls"]["f5Customizer"]["entries"],
        )
        for label in ["Disable Front Cam", "Disable Back Cam", "No Third-Person Clipping", "Scrolling Changes Distance", "Reset F5 Scrolling"]:
            self.assertFalse(proof["cameraPopupControls"]["f5Customizer"]["booleanClicks"][label]["before"])
            self.assertTrue(proof["cameraPopupControls"]["f5Customizer"]["booleanClicks"][label]["after"])
        self.assertAlmostEqual(1.0, FakeClient.latest.camera_speed, places=2)
        self.assertAlmostEqual(4.0, FakeClient.latest.freelook_distance, places=2)
        self.assertFalse(FakeClient.latest.f5_disable_front)
        self.assertFalse(FakeClient.latest.f5_disable_back)
        self.assertFalse(FakeClient.latest.f5_no_clip)
        self.assertFalse(FakeClient.latest.f5_scroll_enabled)
        self.assertFalse(FakeClient.latest.f5_reset_on_toggle)
        self.assertAlmostEqual(4.0, FakeClient.latest.f5_distance, places=2)
        self.assertEqual("No Armor", proof["hidersNoArmorSelector"]["popup"])
        self.assertEqual(["Target"], proof["hidersNoArmorSelector"]["entries"])
        self.assertEqual(["Off", "Self", "Others", "All"], proof["hidersNoArmorSelector"]["options"])
        self.assertEqual("Off", proof["hidersNoArmorSelector"]["selectorClick"]["before"])
        self.assertEqual("Self", proof["hidersNoArmorSelector"]["selectorClick"]["after"])
        self.assertEqual("Off", FakeClient.latest.no_armor_target)
        self.assertEqual("Player Size", proof["playerSizeSelectorAndNumber"]["popup"])
        self.assertEqual(["Target", "X", "Y", "Z"], proof["playerSizeSelectorAndNumber"]["entries"])
        self.assertEqual(["Self", "Real Players", "All"], proof["playerSizeSelectorAndNumber"]["options"])
        self.assertEqual("Self", proof["playerSizeSelectorAndNumber"]["targetSelectorClick"]["before"])
        self.assertEqual("Real Players", proof["playerSizeSelectorAndNumber"]["targetSelectorClick"]["after"])
        self.assertNotEqual(
            proof["playerSizeSelectorAndNumber"]["xSliderClick"]["before"],
            proof["playerSizeSelectorAndNumber"]["xSliderClick"]["after"],
        )
        self.assertNotEqual(
            proof["playerSizeSelectorAndNumber"]["ySliderClick"]["before"],
            proof["playerSizeSelectorAndNumber"]["ySliderClick"]["after"],
        )
        self.assertNotEqual(
            proof["playerSizeSelectorAndNumber"]["zSliderClick"]["before"],
            proof["playerSizeSelectorAndNumber"]["zSliderClick"]["after"],
        )
        self.assertEqual("Self", FakeClient.latest.player_size_target)
        self.assertAlmostEqual(1.0, FakeClient.latest.player_scale_x, places=2)
        self.assertAlmostEqual(1.0, FakeClient.latest.player_scale_y, places=2)
        self.assertAlmostEqual(1.0, FakeClient.latest.player_scale_z, places=2)
        self.assertEqual("Stalk Player", proof["stalkTargetExtraInput"]["popup"])
        self.assertEqual(["Tracer Color"], proof["stalkTargetExtraInput"]["entries"])
        self.assertEqual(["Target: <none>"], proof["stalkTargetExtraInput"]["extras"])
        self.assertEqual("STALK_TARGET", proof["stalkTargetExtraInput"]["expandedExtra"])
        self.assertEqual("", proof["stalkTargetExtraInput"]["before"])
        self.assertTrue(proof["stalkTargetExtraInput"]["tracerColorChromaClick"]["expandHandled"])
        self.assertTrue(proof["stalkTargetExtraInput"]["tracerColorChromaClick"]["handled"])
        self.assertEqual(False, proof["stalkTargetExtraInput"]["tracerColorChromaClick"]["before"])
        self.assertEqual(True, proof["stalkTargetExtraInput"]["tracerColorChromaClick"]["after"])
        self.assertEqual("FloydVerifierTarget", proof["stalkTargetExtraInput"]["after"])
        self.assertEqual("", FakeClient.latest.stalk_target)
        self.assertEqual("Neck Hider", proof["neckHiderEditNamesExtraInput"]["popup"])
        self.assertEqual("NAME_MAPPINGS", proof["neckHiderEditNamesExtraInput"]["expandedExtra"])
        self.assertEqual("FloydVerifierReal", proof["neckHiderEditNamesExtraInput"]["realName"])
        self.assertEqual("FloydVerifierFake", proof["neckHiderEditNamesExtraInput"]["fakeName"])
        self.assertEqual("FloydVerifierFake", proof["neckHiderEditNamesExtraInput"]["afterAddValue"])
        self.assertFalse(proof["neckHiderEditNamesExtraInput"]["afterRemoveContains"])
        self.assertNotIn("FloydVerifierReal", FakeClient.latest.nick_name_mappings)
        self.assertEqual("Cape", proof["capeImageCycleAndAction"]["popup"])
        self.assertEqual(["Image", "Open Cape Folder"], proof["capeImageCycleAndAction"]["entries"])
        self.assertTrue(proof["capeImageCycleAndAction"]["optionsContainVerifierPngs"])
        self.assertTrue(proof["capeImageCycleAndAction"]["openFolderHandled"])
        self.assertEqual("zz_floyd_verify_a.png", proof["capeImageCycleAndAction"]["cycleClick"]["before"])
        self.assertEqual("zz_floyd_verify_b.png", proof["capeImageCycleAndAction"]["cycleClick"]["after"])
        self.assertEqual("zz_floyd_verify_a.png", FakeClient.latest.cape_selected)
        self.assertEqual("Cone Hat", proof["coneHatImageCycleAndAction"]["popup"])
        self.assertEqual(
            ["Height", "Radius", "Y Offset", "Rotation", "Spin Speed", "Image", "Open Cone Folder"],
            proof["coneHatImageCycleAndAction"]["entries"],
        )
        self.assertTrue(proof["coneHatImageCycleAndAction"]["optionsContainVerifierPngs"])
        for label in ["Height", "Radius", "Y Offset", "Rotation", "Spin Speed"]:
            self.assertNotEqual(
                proof["coneHatImageCycleAndAction"]["numberClicks"][label]["before"],
                proof["coneHatImageCycleAndAction"]["numberClicks"][label]["after"],
            )
        self.assertTrue(proof["coneHatImageCycleAndAction"]["openFolderHandled"])
        self.assertEqual("zz_floyd_cone_verify_a.png", proof["coneHatImageCycleAndAction"]["cycleClick"]["before"])
        self.assertEqual("zz_floyd_cone_verify_b.png", proof["coneHatImageCycleAndAction"]["cycleClick"]["after"])
        self.assertEqual("zz_floyd_cone_verify_a.png", FakeClient.latest.cone_selected)
        self.assertAlmostEqual(0.9, FakeClient.latest.cone_height, places=2)
        self.assertAlmostEqual(0.45, FakeClient.latest.cone_radius, places=2)
        self.assertAlmostEqual(-0.7, FakeClient.latest.cone_y_offset, places=2)
        self.assertAlmostEqual(0.0, FakeClient.latest.cone_rotation, places=2)
        self.assertAlmostEqual(0.0, FakeClient.latest.cone_spin_speed, places=2)
        self.assertEqual("Custom Skin", proof["customSkinPopupControls"]["popup"])
        self.assertEqual(["Self", "Others", "Skin", "Open Skin Folder"], proof["customSkinPopupControls"]["entries"])
        self.assertTrue(proof["customSkinPopupControls"]["optionsContainVerifierPngs"])
        self.assertTrue(proof["customSkinPopupControls"]["openFolderHandled"])
        self.assertEqual(False, proof["customSkinPopupControls"]["selfToggle"]["before"])
        self.assertEqual(True, proof["customSkinPopupControls"]["selfToggle"]["after"])
        self.assertEqual(False, proof["customSkinPopupControls"]["othersToggle"]["before"])
        self.assertEqual(True, proof["customSkinPopupControls"]["othersToggle"]["after"])
        self.assertEqual("zz_floyd_skin_verify_a.png", proof["customSkinPopupControls"]["cycleClick"]["before"])
        self.assertEqual("zz_floyd_skin_verify_b.png", proof["customSkinPopupControls"]["cycleClick"]["after"])
        self.assertEqual("zz_floyd_skin_verify_a.png", FakeClient.latest.skin_selected)
        self.assertFalse(FakeClient.latest.skin_self)
        self.assertFalse(FakeClient.latest.skin_others)
        self.assertEqual(["TEXT", "BUTTON_BORDER", "GUI_BORDER"], proof["guiStyleColorPicker"]["targets"])
        self.assertTrue(proof["guiStyleColorPicker"]["styleClickHandled"])
        self.assertTrue(proof["guiStyleColorPicker"]["pickerClickHandled"])
        self.assertTrue(proof["guiStyleColorPicker"]["fadeClickHandled"])
        self.assertTrue(proof["guiStyleColorPicker"]["applyClickHandled"])
        self.assertEqual({"chroma": True, "fade": False}, proof["guiStyleColorPicker"]["before"])
        self.assertEqual({"chroma": False, "fade": True}, proof["guiStyleColorPicker"]["after"])
        self.assertEqual({"chroma": True, "fade": False}, proof["guiStyleColorPicker"]["restored"])
        self.assertTrue(FakeClient.latest.gui_text_chroma)
        self.assertFalse(FakeClient.latest.gui_text_fade)
        self.assertTrue(proof["renderPageTimeControls"]["rowsContainExpected"])
        self.assertTrue(proof["renderPageTimeControls"]["entriesContainExpected"])
        self.assertTrue(proof["renderPageTimeControls"]["renderClickHandled"])
        self.assertTrue(proof["renderPageTimeControls"]["serverClickHandled"])
        self.assertTrue(proof["renderPageTimeControls"]["profileClickHandled"])
        self.assertTrue(proof["renderPageTimeControls"]["xrayClickHandled"])
        self.assertTrue(proof["renderPageTimeControls"]["opacityClickHandled"])
        self.assertTrue(proof["renderPageTimeControls"]["reloadClickHandled"])
        self.assertTrue(proof["renderPageTimeControls"]["mobClickHandled"])
        self.assertTrue(proof["renderPageTimeControls"]["toggleClickHandled"])
        self.assertTrue(proof["renderPageTimeControls"]["numberClickHandled"])
        self.assertTrue(proof["renderPageTimeControls"]["borderlessClickHandled"])
        self.assertEqual(
            {
                "serverIdHider": False,
                "profileIdHider": False,
                "xrayEnabled": False,
                "xrayOpacity": 0.5,
                "mobEnabled": False,
                "timeEnabled": False,
                "timeValue": 50.0,
                "borderlessWindowed": False,
                "windowTitle": "",
            },
            proof["renderPageTimeControls"]["before"],
        )
        self.assertEqual(True, proof["renderPageTimeControls"]["after"]["serverIdHider"])
        self.assertEqual(True, proof["renderPageTimeControls"]["after"]["profileIdHider"])
        self.assertEqual(True, proof["renderPageTimeControls"]["after"]["xrayEnabled"])
        self.assertEqual(True, proof["renderPageTimeControls"]["after"]["mobEnabled"])
        self.assertEqual(True, proof["renderPageTimeControls"]["after"]["timeEnabled"])
        self.assertEqual(True, proof["renderPageTimeControls"]["after"]["borderlessWindowed"])
        self.assertNotEqual(0.5, proof["renderPageTimeControls"]["after"]["xrayOpacity"])
        self.assertNotEqual(50.0, proof["renderPageTimeControls"]["after"]["timeValue"])
        self.assertEqual(
            {
                "serverIdHider": False,
                "profileIdHider": False,
                "xrayEnabled": False,
                "xrayOpacity": 0.5,
                "mobEnabled": False,
                "timeEnabled": False,
                "timeValue": 50.0,
                "borderlessWindowed": False,
                "windowTitle": "",
            },
            proof["renderPageTimeControls"]["restored"],
        )
        self.assertFalse(FakeClient.latest.server_id_hider)
        self.assertFalse(FakeClient.latest.profile_id_hider)
        self.assertFalse(FakeClient.latest.xray_enabled)
        self.assertAlmostEqual(0.5, FakeClient.latest.xray_opacity, places=2)
        self.assertFalse(FakeClient.latest.mob_enabled)
        self.assertFalse(FakeClient.latest.render_custom_time)
        self.assertAlmostEqual(50.0, FakeClient.latest.render_time_value, places=2)
        self.assertFalse(FakeClient.latest.borderless_windowed)
        self.assertTrue(proof["cameraPageControls"]["entriesContainExpected"])
        self.assertEqual(
            {
                "speed": 1.0,
                "disableFront": False,
                "disableBack": False,
                "noClip": False,
                "scrollEnabled": False,
                "resetOnToggle": False,
                "f5Distance": 4.0,
            },
            proof["cameraPageControls"]["before"],
        )
        self.assertEqual(True, proof["cameraPageControls"]["after"]["disableFront"])
        self.assertEqual(True, proof["cameraPageControls"]["after"]["disableBack"])
        self.assertEqual(True, proof["cameraPageControls"]["after"]["noClip"])
        self.assertEqual(True, proof["cameraPageControls"]["after"]["scrollEnabled"])
        self.assertEqual(True, proof["cameraPageControls"]["after"]["resetOnToggle"])
        self.assertNotEqual(1.0, proof["cameraPageControls"]["after"]["speed"])
        self.assertNotEqual(4.0, proof["cameraPageControls"]["after"]["f5Distance"])
        self.assertEqual(
            {
                "speed": 1.0,
                "disableFront": False,
                "disableBack": False,
                "noClip": False,
                "scrollEnabled": False,
                "resetOnToggle": False,
                "f5Distance": 4.0,
            },
            proof["cameraPageControls"]["restored"],
        )
        self.assertAlmostEqual(1.0, FakeClient.latest.camera_speed, places=2)
        self.assertFalse(FakeClient.latest.f5_disable_front)
        self.assertFalse(FakeClient.latest.f5_disable_back)
        self.assertFalse(FakeClient.latest.f5_no_clip)
        self.assertFalse(FakeClient.latest.f5_scroll_enabled)
        self.assertFalse(FakeClient.latest.f5_reset_on_toggle)
        self.assertAlmostEqual(4.0, FakeClient.latest.f5_distance, places=2)
        self.assertTrue(proof["hidersPageControls"]["entriesContainExpected"])
        self.assertEqual(
            {
                "noHurtCamera": False,
                "removeFireOverlay": False,
                "hideEntityFire": False,
                "disableAttachedArrows": False,
                "removeExplosionParticles": False,
                "disableHungerBar": False,
                "hidePotionEffects": False,
                "thirdPersonCrosshair": False,
                "removeFallingBlocks": False,
                "removeTabPing": False,
                "noArmorMode": "Off",
            },
            proof["hidersPageControls"]["before"],
        )
        self.assertEqual(True, proof["hidersPageControls"]["after"]["noHurtCamera"])
        self.assertEqual(True, proof["hidersPageControls"]["after"]["removeFireOverlay"])
        self.assertEqual(True, proof["hidersPageControls"]["after"]["hideEntityFire"])
        self.assertEqual(True, proof["hidersPageControls"]["after"]["disableAttachedArrows"])
        self.assertEqual(True, proof["hidersPageControls"]["after"]["removeExplosionParticles"])
        self.assertEqual(True, proof["hidersPageControls"]["after"]["disableHungerBar"])
        self.assertEqual(True, proof["hidersPageControls"]["after"]["hidePotionEffects"])
        self.assertEqual(True, proof["hidersPageControls"]["after"]["thirdPersonCrosshair"])
        self.assertEqual(True, proof["hidersPageControls"]["after"]["removeFallingBlocks"])
        self.assertEqual(True, proof["hidersPageControls"]["after"]["removeTabPing"])
        self.assertNotEqual("Off", proof["hidersPageControls"]["after"]["noArmorMode"])
        self.assertEqual(
            {
                "noHurtCamera": False,
                "removeFireOverlay": False,
                "hideEntityFire": False,
                "disableAttachedArrows": False,
                "removeExplosionParticles": False,
                "disableHungerBar": False,
                "hidePotionEffects": False,
                "thirdPersonCrosshair": False,
                "removeFallingBlocks": False,
                "removeTabPing": False,
                "noArmorMode": "Off",
            },
            proof["hidersPageControls"]["restored"],
        )
        self.assertFalse(FakeClient.latest.no_hurt_camera)
        self.assertFalse(FakeClient.latest.remove_fire_overlay)
        self.assertFalse(FakeClient.latest.hide_entity_fire)
        self.assertFalse(FakeClient.latest.disable_attached_arrows)
        self.assertFalse(FakeClient.latest.remove_explosion_particles)
        self.assertFalse(FakeClient.latest.disable_hunger_bar)
        self.assertFalse(FakeClient.latest.hide_potion_effects)
        self.assertFalse(FakeClient.latest.third_person_crosshair)
        self.assertFalse(FakeClient.latest.remove_falling_blocks)
        self.assertFalse(FakeClient.latest.remove_tab_ping)
        self.assertEqual("Off", FakeClient.latest.no_armor_target)
        self.assertTrue(proof["animationsPageControls"]["entriesContainExpected"])
        self.assertEqual(
            {"enabled": False, "posX": 0.0, "cancelReEquip": False, "hideHand": False, "classicClick": False},
            proof["animationsPageControls"]["before"],
        )
        self.assertEqual(True, proof["animationsPageControls"]["after"]["enabled"])
        self.assertNotEqual(0.0, proof["animationsPageControls"]["after"]["posX"])
        self.assertEqual(True, proof["animationsPageControls"]["after"]["cancelReEquip"])
        self.assertEqual(True, proof["animationsPageControls"]["after"]["hideHand"])
        self.assertEqual(True, proof["animationsPageControls"]["after"]["classicClick"])
        self.assertTrue(proof["animationsPageControls"]["cancelClickHandled"])
        self.assertTrue(proof["animationsPageControls"]["hideHandClickHandled"])
        self.assertTrue(proof["animationsPageControls"]["classicClickHandled"])
        self.assertEqual(
            {"enabled": False, "posX": 0.0, "cancelReEquip": False, "hideHand": False, "classicClick": False},
            proof["animationsPageControls"]["restored"],
        )
        self.assertFalse(FakeClient.latest.animations_enabled)
        self.assertAlmostEqual(0.0, FakeClient.latest.anim_pos_x, places=2)
        self.assertTrue(proof["mobEspPageControls"]["entriesContainExpected"])
        self.assertEqual(
            {"tracers": False, "hitboxes": False, "starMobs": False, "defaultChroma": False, "stalkChroma": False},
            proof["mobEspPageControls"]["before"],
        )
        self.assertEqual(True, proof["mobEspPageControls"]["after"]["tracers"])
        self.assertEqual(True, proof["mobEspPageControls"]["after"]["hitboxes"])
        self.assertEqual(True, proof["mobEspPageControls"]["after"]["starMobs"])
        self.assertEqual(True, proof["mobEspPageControls"]["after"]["defaultChroma"])
        self.assertEqual(True, proof["mobEspPageControls"]["after"]["stalkChroma"])
        self.assertTrue(proof["mobEspPageControls"]["hitboxesClickHandled"])
        self.assertTrue(proof["mobEspPageControls"]["defaultColorClickHandled"])
        self.assertTrue(proof["mobEspPageControls"]["defaultChromaClickHandled"])
        self.assertTrue(proof["mobEspPageControls"]["defaultApplyClickHandled"])
        self.assertTrue(proof["mobEspPageControls"]["tracerColorClickHandled"])
        self.assertTrue(proof["mobEspPageControls"]["tracerChromaClickHandled"])
        self.assertTrue(proof["mobEspPageControls"]["tracerApplyClickHandled"])
        self.assertEqual("MOB_ESP_FILTERS", proof["mobEspPageControls"]["filtersPage"])
        self.assertEqual(
            {"tracers": False, "hitboxes": False, "starMobs": False, "defaultChroma": False, "stalkChroma": False},
            proof["mobEspPageControls"]["restored"],
        )
        self.assertFalse(FakeClient.latest.mob_tracers)
        self.assertFalse(FakeClient.latest.mob_hitboxes)
        self.assertFalse(FakeClient.latest.mob_star_mobs)
        self.assertFalse(FakeClient.latest.mob_default_chroma)
        self.assertFalse(FakeClient.latest.stalk_chroma)
        self.assertTrue(proof["mobEspFiltersPageControls"]["entriesContainAddName"])
        self.assertTrue(proof["mobEspFiltersPageControls"]["addNameClickHandled"])
        self.assertTrue(proof["mobEspFiltersPageControls"]["typeHandled"])
        self.assertTrue(proof["mobEspFiltersPageControls"]["colorClickHandled"])
        self.assertTrue(proof["mobEspFiltersPageControls"]["chromaClickHandled"])
        self.assertTrue(proof["mobEspFiltersPageControls"]["removeClickHandled"])
        self.assertEqual("Add Mob Name Filter", proof["mobEspFiltersPageControls"]["editor"])
        self.assertTrue(proof["mobEspFiltersPageControls"]["afterAddContains"])
        self.assertFalse(proof["mobEspFiltersPageControls"]["afterRemoveContains"])
        self.assertNotIn("FloydPageMob", FakeClient.latest.mob_name_filters)
        self.assertTrue(proof["cosmeticPageControls"]["entriesContainExpected"])
        self.assertEqual(
            {"customSkin": False, "cape": False, "cone": False, "target": "Self", "scaleX": 1.0, "scaleY": 1.0, "scaleZ": 1.0},
            proof["cosmeticPageControls"]["before"],
        )
        self.assertEqual(True, proof["cosmeticPageControls"]["after"]["customSkin"])
        self.assertEqual(True, proof["cosmeticPageControls"]["after"]["cape"])
        self.assertEqual(True, proof["cosmeticPageControls"]["after"]["cone"])
        self.assertNotEqual("Self", proof["cosmeticPageControls"]["after"]["target"])
        self.assertNotEqual(1.0, proof["cosmeticPageControls"]["after"]["scaleX"])
        self.assertNotEqual(1.0, proof["cosmeticPageControls"]["after"]["scaleY"])
        self.assertNotEqual(1.0, proof["cosmeticPageControls"]["after"]["scaleZ"])
        self.assertEqual(
            {"customSkin": False, "cape": False, "cone": False, "target": "Self", "scaleX": 1.0, "scaleY": 1.0, "scaleZ": 1.0},
            proof["cosmeticPageControls"]["restored"],
        )
        self.assertFalse(FakeClient.latest.cosmetic_custom_skin)
        self.assertFalse(FakeClient.latest.cosmetic_cape)
        self.assertFalse(FakeClient.latest.cosmetic_cone)
        self.assertAlmostEqual(1.0, FakeClient.latest.player_scale_x, places=2)
        self.assertAlmostEqual(1.0, FakeClient.latest.player_scale_y, places=2)
        self.assertAlmostEqual(1.0, FakeClient.latest.player_scale_z, places=2)
        self.assertTrue(proof["skinPageControls"]["entriesContainExpected"])
        self.assertTrue(proof["skinPageControls"]["availableContainVerifierPngs"])
        self.assertEqual({"customSkin": False, "self": False, "others": False, "selected": "zz_floyd_skin_verify_a.png"}, proof["skinPageControls"]["before"])
        self.assertEqual(False, proof["skinPageControls"]["after"]["customSkin"])
        self.assertEqual(True, proof["skinPageControls"]["after"]["self"])
        self.assertEqual(True, proof["skinPageControls"]["after"]["others"])
        self.assertEqual("zz_floyd_skin_verify_b.png", proof["skinPageControls"]["after"]["selected"])
        self.assertEqual({"customSkin": False, "self": False, "others": False, "selected": "zz_floyd_skin_verify_a.png"}, proof["skinPageControls"]["restored"])
        self.assertEqual("zz_floyd_skin_verify_a.png", FakeClient.latest.skin_selected)
        self.assertFalse(FakeClient.latest.skin_self)
        self.assertFalse(FakeClient.latest.skin_others)
        self.assertTrue(proof["capePageControls"]["buttonsPresent"])
        self.assertTrue(proof["capePageControls"]["availableContainVerifierPngs"])
        self.assertEqual({"selected": "zz_floyd_verify_a.png"}, proof["capePageControls"]["before"])
        self.assertEqual({"selected": "zz_floyd_verify_b.png"}, proof["capePageControls"]["afterNext"])
        self.assertEqual({"selected": "zz_floyd_verify_a.png"}, proof["capePageControls"]["afterPrevious"])
        self.assertEqual({"selected": "zz_floyd_verify_a.png"}, proof["capePageControls"]["restored"])
        self.assertEqual("zz_floyd_verify_a.png", FakeClient.latest.cape_selected)
        self.assertTrue(proof["coneHatPageControls"]["controlsPresent"])
        self.assertTrue(proof["coneHatPageControls"]["availableContainVerifierPngs"])
        self.assertEqual({"selected": "zz_floyd_cone_verify_a.png", "height": 0.9, "radius": 0.45, "yOffset": -0.7, "rotation": 0.0, "spinSpeed": 0.0}, proof["coneHatPageControls"]["before"])
        self.assertEqual("zz_floyd_cone_verify_b.png", proof["coneHatPageControls"]["after"]["selected"])
        self.assertNotEqual(0.9, proof["coneHatPageControls"]["after"]["height"])
        self.assertNotEqual(0.45, proof["coneHatPageControls"]["after"]["radius"])
        self.assertNotEqual(-0.7, proof["coneHatPageControls"]["after"]["yOffset"])
        self.assertNotEqual(0.0, proof["coneHatPageControls"]["after"]["rotation"])
        self.assertNotEqual(0.0, proof["coneHatPageControls"]["after"]["spinSpeed"])
        self.assertEqual({"selected": "zz_floyd_cone_verify_a.png", "height": 0.9, "radius": 0.45, "yOffset": -0.7, "rotation": 0.0, "spinSpeed": 0.0}, proof["coneHatPageControls"]["restored"])
        self.assertEqual("zz_floyd_cone_verify_a.png", FakeClient.latest.cone_selected)
        self.assertAlmostEqual(0.9, FakeClient.latest.cone_height, places=2)
        self.assertAlmostEqual(0.45, FakeClient.latest.cone_radius, places=2)
        self.assertAlmostEqual(-0.7, FakeClient.latest.cone_y_offset, places=2)
        self.assertAlmostEqual(0.0, FakeClient.latest.cone_rotation, places=2)
        self.assertAlmostEqual(0.0, FakeClient.latest.cone_spin_speed, places=2)
        self.assertTrue(proof["playerSizePageControls"]["rowsContainExpected"])
        self.assertEqual({"enabled": False, "target": "Self", "scaleX": 1.0, "scaleY": 1.0, "scaleZ": 1.0}, proof["playerSizePageControls"]["before"])
        self.assertEqual(True, proof["playerSizePageControls"]["after"]["enabled"])
        self.assertNotEqual("Self", proof["playerSizePageControls"]["after"]["target"])
        self.assertNotEqual(1.0, proof["playerSizePageControls"]["after"]["scaleX"])
        self.assertNotEqual(1.0, proof["playerSizePageControls"]["after"]["scaleY"])
        self.assertNotEqual(1.0, proof["playerSizePageControls"]["after"]["scaleZ"])
        self.assertTrue(proof["playerSizePageControls"]["zClickHandled"])
        self.assertEqual({"enabled": False, "target": "Self", "scaleX": 1.0, "scaleY": 1.0, "scaleZ": 1.0}, proof["playerSizePageControls"]["restored"])
        self.assertFalse(FakeClient.latest.player_size_enabled)
        self.assertEqual("Self", FakeClient.latest.player_size_target)
        self.assertAlmostEqual(1.0, FakeClient.latest.player_scale_x, places=2)
        self.assertAlmostEqual(1.0, FakeClient.latest.player_scale_y, places=2)
        self.assertTrue(proof["nameMappingsPageControls"]["entriesContainAddManual"])
        self.assertTrue(proof["nameMappingsPageControls"]["addClickHandled"])
        self.assertTrue(proof["nameMappingsPageControls"]["typeRealHandled"])
        self.assertTrue(proof["nameMappingsPageControls"]["typeFakeHandled"])
        self.assertTrue(proof["nameMappingsPageControls"]["revealClickHandled"])
        self.assertTrue(proof["nameMappingsPageControls"]["removeClickHandled"])
        self.assertEqual("Map Real Name", proof["nameMappingsPageControls"]["realEditor"])
        self.assertEqual("Fake Name for FloydPageReal", proof["nameMappingsPageControls"]["fakeEditor"])
        self.assertEqual("FloydPageFake", proof["nameMappingsPageControls"]["afterAddValue"])
        self.assertFalse(proof["nameMappingsPageControls"]["afterRemoveContains"])
        self.assertNotIn("FloydPageReal", FakeClient.latest.nick_name_mappings)
        self.assertEqual("Inventory HUD", proof["hudEditLayoutExtra"]["inventoryHud"]["popup"])
        self.assertEqual(["Edit Layout"], proof["hudEditLayoutExtra"]["inventoryHud"]["extras"])
        self.assertEqual("floydaddons.not.dogshit.client.clickgui.HudManager", proof["hudEditLayoutExtra"]["inventoryHud"]["screen"])
        self.assertEqual("HUD Manager", proof["hudEditLayoutExtra"]["inventoryHud"]["screenTitle"])
        self.assertEqual("Custom Scoreboard", proof["hudEditLayoutExtra"]["customScoreboard"]["popup"])
        self.assertEqual(["Edit Layout"], proof["hudEditLayoutExtra"]["customScoreboard"]["extras"])
        self.assertEqual("floydaddons.not.dogshit.client.clickgui.HudManager", proof["hudEditLayoutExtra"]["customScoreboard"]["screen"])
        self.assertEqual("HUD Manager", proof["hudEditLayoutExtra"]["customScoreboard"]["screenTitle"])

    def test_rejects_mob_right_click_toggle_regression(self) -> None:
        client = FakeClient(toggle_mob_on_right_click=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Mob ESP right click must not toggle enabled state", str(raised.exception))

    def test_rejects_missing_xray_popup_controls(self) -> None:
        client = FakeClient(omit_xray_extra=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("X-Ray popup extras expected ['Edit Blocks']", str(raised.exception))

    def test_rejects_popup_toggle_regression(self) -> None:
        client = FakeClient(ignore_popup_toggle=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Mob ESP Tracers popup click did not toggle value", str(raised.exception))

    def test_rejects_slider_regression(self) -> None:
        client = FakeClient(ignore_slider=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("X-Ray Opacity slider click did not change value", str(raised.exception))

    def test_rejects_xray_edit_blocks_regression(self) -> None:
        client = FakeClient(ignore_xray_block_add=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("X-Ray Edit Blocks add expected", str(raised.exception))

    def test_rejects_xray_page_regression(self) -> None:
        client = FakeClient(ignore_xray_page=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("X-Ray page remove expected", str(raised.exception))

    def test_rejects_color_chroma_regression(self) -> None:
        client = FakeClient(ignore_color_chroma=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Mob ESP Default ESP Color chroma click did not toggle value", str(raised.exception))

    def test_rejects_mob_edit_filters_regression(self) -> None:
        client = FakeClient(ignore_mob_filter_add=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Mob ESP Edit Filters add expected", str(raised.exception))

    def test_rejects_string_edit_regression(self) -> None:
        client = FakeClient(ignore_string_edit=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Instance Title edit expected", str(raised.exception))

    def test_rejects_camera_slider_regression(self) -> None:
        client = FakeClient(ignore_camera_slider=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Freecam Speed slider click did not change value", str(raised.exception))

    def test_rejects_selector_regression(self) -> None:
        client = FakeClient(ignore_selector=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("No Armor Target selector click did not change value", str(raised.exception))

    def test_rejects_player_size_slider_regression(self) -> None:
        client = FakeClient(ignore_player_size_slider=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Player Size X slider click did not change value", str(raised.exception))

    def test_rejects_stalk_target_extra_input_regression(self) -> None:
        client = FakeClient(ignore_stalk_target_input=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Stalk Player Target input expected", str(raised.exception))

    def test_rejects_neck_hider_edit_names_regression(self) -> None:
        client = FakeClient(ignore_name_mapping_save=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Neck Hider Edit Names add expected", str(raised.exception))

    def test_rejects_hud_edit_layout_regression(self) -> None:
        client = FakeClient(ignore_hud_layout=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Inventory HUD Edit Layout expected HudManager screen", str(raised.exception))

    def test_rejects_cape_image_cycle_regression(self) -> None:
        client = FakeClient(ignore_cape_cycle=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Cape Image cycle click did not change value", str(raised.exception))

    def test_rejects_cone_hat_image_cycle_regression(self) -> None:
        client = FakeClient(ignore_cone_cycle=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Cone Hat Image cycle click did not change value", str(raised.exception))

    def test_rejects_custom_skin_cycle_regression(self) -> None:
        client = FakeClient(ignore_skin_cycle=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Custom Skin Skin cycle click did not change value", str(raised.exception))

    def test_rejects_custom_skin_toggle_regression(self) -> None:
        client = FakeClient(ignore_skin_toggle=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Custom Skin Self popup click did not toggle value", str(raised.exception))

    def test_rejects_gui_style_fade_regression(self) -> None:
        client = FakeClient(ignore_gui_style_fade=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("GUI Style Button Text Fade did not toggle value", str(raised.exception))

    def test_rejects_render_page_time_regression(self) -> None:
        client = FakeClient(ignore_render_page_time=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Render Time Changer row did not toggle value", str(raised.exception))

    def test_rejects_cosmetic_page_regression(self) -> None:
        client = FakeClient(ignore_cosmetic_page=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Cosmetic Custom Skin row did not toggle value", str(raised.exception))

    def test_rejects_camera_page_regression(self) -> None:
        client = FakeClient(ignore_camera_page=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Camera Disable Front row did not toggle value", str(raised.exception))

    def test_rejects_hiders_page_regression(self) -> None:
        client = FakeClient(ignore_hiders_page=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Hiders No Hurt Camera row did not toggle value", str(raised.exception))

    def test_rejects_animations_page_regression(self) -> None:
        client = FakeClient(ignore_animations_page=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Animations module row did not toggle value", str(raised.exception))

    def test_rejects_mob_esp_page_regression(self) -> None:
        client = FakeClient(ignore_mob_esp_page=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Mob ESP Tracers page row did not toggle value", str(raised.exception))

    def test_rejects_skin_page_regression(self) -> None:
        client = FakeClient(ignore_skin_page=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Skin Self page row did not toggle value", str(raised.exception))

    def test_rejects_cape_page_regression(self) -> None:
        client = FakeClient(ignore_cape_page=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Cape page Next click did not change value", str(raised.exception))

    def test_rejects_cone_hat_page_regression(self) -> None:
        client = FakeClient(ignore_cone_page=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Cone Hat page Height slider did not change value", str(raised.exception))

    def test_rejects_player_size_page_regression(self) -> None:
        client = FakeClient(ignore_player_size_page=True)

        with self.assertRaises(SystemExit) as raised:
            self.verifier.verify_legacy_clickgui(client)

        self.assertIn("Player Size page module toggle did not change value", str(raised.exception))


class FakeClient:
    latest: "FakeClient"

    def __init__(
        self,
        toggle_mob_on_right_click: bool = False,
        omit_xray_extra: bool = False,
        ignore_popup_toggle: bool = False,
        ignore_slider: bool = False,
        ignore_color_chroma: bool = False,
        ignore_string_edit: bool = False,
        ignore_selector: bool = False,
        ignore_stalk_target_input: bool = False,
        ignore_xray_block_add: bool = False,
        ignore_xray_page: bool = False,
        ignore_mob_filter_add: bool = False,
        ignore_name_mapping_save: bool = False,
        ignore_hud_layout: bool = False,
        ignore_cape_cycle: bool = False,
        ignore_cone_cycle: bool = False,
        ignore_skin_cycle: bool = False,
        ignore_skin_toggle: bool = False,
        ignore_player_size_slider: bool = False,
        ignore_camera_slider: bool = False,
        ignore_gui_style_fade: bool = False,
        ignore_render_page_time: bool = False,
        ignore_camera_page: bool = False,
        ignore_hiders_page: bool = False,
        ignore_animations_page: bool = False,
        ignore_mob_esp_page: bool = False,
        ignore_cosmetic_page: bool = False,
        ignore_skin_page: bool = False,
        ignore_cape_page: bool = False,
        ignore_cone_page: bool = False,
        ignore_player_size_page: bool = False,
    ) -> None:
        FakeClient.latest = self
        self.screen = "floydaddons.not.dogshit.client.clickgui.LegacyFloydClickGUI"
        self.screen_title: str | None = None
        self.page = "HUB"
        self.xray_enabled = False
        self.xray_opacity = 0.5
        self.xray_opaque_blocks = ["minecraft:glass"]
        self.expanded_xray_blocks = False
        self.mob_enabled = False
        self.mob_tracers = False
        self.mob_hitboxes = False
        self.mob_star_mobs = False
        self.mob_default_chroma = False
        self.mob_name_filters: list[str] = []
        self.expanded_mob_filters = False
        self.expanded_color = False
        self.instance_title = ""
        self.editing_string = False
        self.server_id_hider = False
        self.profile_id_hider = False
        self.borderless_windowed = False
        self.camera_speed = 1.0
        self.freelook_distance = 4.0
        self.f5_disable_front = False
        self.f5_disable_back = False
        self.f5_no_clip = False
        self.f5_scroll_enabled = False
        self.f5_reset_on_toggle = False
        self.f5_distance = 4.0
        self.no_hurt_camera = False
        self.remove_fire_overlay = False
        self.hide_entity_fire = False
        self.disable_attached_arrows = False
        self.remove_explosion_particles = False
        self.disable_hunger_bar = False
        self.hide_potion_effects = False
        self.third_person_crosshair = False
        self.remove_falling_blocks = False
        self.remove_tab_ping = False
        self.no_armor_target = "Off"
        self.player_size_enabled = False
        self.player_size_target = "Self"
        self.player_scale_x = 1.0
        self.player_scale_y = 1.0
        self.player_scale_z = 1.0
        self.stalk_target = ""
        self.stalk_chroma = False
        self.expanded_stalk_target = False
        self.expanded_stalk_color = False
        self.nick_name_mappings: dict[str, str] = {}
        self.cape_selected = "zz_floyd_verify_a.png"
        self.cape_available = ["zz_floyd_verify_a.png", "zz_floyd_verify_b.png"]
        self.cone_selected = "zz_floyd_cone_verify_a.png"
        self.cone_available = ["zz_floyd_cone_verify_a.png", "zz_floyd_cone_verify_b.png"]
        self.cone_height = 0.9
        self.cone_radius = 0.45
        self.cone_y_offset = -0.7
        self.cone_rotation = 0.0
        self.cone_spin_speed = 0.0
        self.skin_selected = "zz_floyd_skin_verify_a.png"
        self.skin_available = ["zz_floyd_skin_verify_a.png", "zz_floyd_skin_verify_b.png"]
        self.skin_self = False
        self.skin_others = False
        self.gui_text_chroma = True
        self.gui_text_fade = False
        self.render_custom_time = False
        self.render_time_value = 50.0
        self.animations_enabled = False
        self.anim_pos_x = 0.0
        self.anim_cancel_re_equip = False
        self.anim_hide_hand = False
        self.anim_classic_click = False
        self.cosmetic_custom_skin = False
        self.cosmetic_cape = False
        self.cosmetic_cone = False
        self.color_picker_open = False
        self.color_picker_title = "Button Text"
        self.color_picker_target = "gui_text"
        self.skin_dropdown_open = False
        self.cone_dropdown_open = False
        self.cone_editing_index = -1
        self.expanded_name_mappings = False
        self.mapping_focused_field: str | None = None
        self.mapping_original_buffer = ""
        self.mapping_fake_buffer = ""
        self.text_editor_title: str | None = None
        self.pending_mapping_real: str | None = None
        self.active_action_input: str | None = None
        self.action_input_buffer = ""
        self.popup: dict[str, Any] | None = None
        self.toggle_mob_on_right_click = toggle_mob_on_right_click
        self.omit_xray_extra = omit_xray_extra
        self.ignore_popup_toggle = ignore_popup_toggle
        self.ignore_slider = ignore_slider
        self.ignore_color_chroma = ignore_color_chroma
        self.ignore_string_edit = ignore_string_edit
        self.ignore_selector = ignore_selector
        self.ignore_stalk_target_input = ignore_stalk_target_input
        self.ignore_xray_block_add = ignore_xray_block_add
        self.ignore_xray_page = ignore_xray_page
        self.ignore_mob_filter_add = ignore_mob_filter_add
        self.ignore_name_mapping_save = ignore_name_mapping_save
        self.ignore_hud_layout = ignore_hud_layout
        self.ignore_cape_cycle = ignore_cape_cycle
        self.ignore_cone_cycle = ignore_cone_cycle
        self.ignore_skin_cycle = ignore_skin_cycle
        self.ignore_skin_toggle = ignore_skin_toggle
        self.ignore_player_size_slider = ignore_player_size_slider
        self.ignore_camera_slider = ignore_camera_slider
        self.ignore_gui_style_fade = ignore_gui_style_fade
        self.ignore_render_page_time = ignore_render_page_time
        self.ignore_camera_page = ignore_camera_page
        self.ignore_hiders_page = ignore_hiders_page
        self.ignore_animations_page = ignore_animations_page
        self.ignore_mob_esp_page = ignore_mob_esp_page
        self.ignore_cosmetic_page = ignore_cosmetic_page
        self.ignore_skin_page = ignore_skin_page
        self.ignore_cape_page = ignore_cape_page
        self.ignore_cone_page = ignore_cone_page
        self.ignore_player_size_page = ignore_player_size_page

    def get(self, path: str) -> dict[str, Any]:
        if path != "/state":
            raise AssertionError(path)
        return self.state()

    def post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        if path == "/action":
            return {"ok": True}
        if path == "/screenshot":
            return {"ok": True, "path": payload.get("fileName", "fake.png"), "screen": self.screen}
        if path == "/type":
            handled = self.editing_string or self.active_action_input is not None or self.mapping_focused_field is not None or self.cone_editing_index >= 0 or self.text_editor_title is not None
            if self.editing_string and not self.ignore_string_edit:
                self.instance_title = payload["text"]
            elif self.text_editor_title == "Map Real Name":
                if payload.get("submit", False):
                    self.pending_mapping_real = payload["text"].strip()
                    self.text_editor_title = f"Fake Name for {self.pending_mapping_real}"
            elif self.text_editor_title is not None and self.text_editor_title.startswith("Fake Name for "):
                if payload.get("submit", False):
                    real = self.pending_mapping_real or self.text_editor_title.removeprefix("Fake Name for ")
                    fake = payload["text"].strip()
                    if real and fake and not self.ignore_name_mapping_save:
                        self.nick_name_mappings[real] = fake
                    self.pending_mapping_real = None
                    self.text_editor_title = None
            elif self.text_editor_title == "Add Mob Name Filter":
                if payload.get("submit", False):
                    name = payload["text"].strip()
                    if name and not self.ignore_mob_filter_add and name not in self.mob_name_filters:
                        self.mob_name_filters.append(name)
                        self.mob_name_filters.sort(key=str.lower)
                    self.text_editor_title = None
            elif self.cone_editing_index >= 0:
                if not self.ignore_cone_page:
                    self.set_cone_value(self.cone_editing_index, float(payload["text"]))
                self.cone_editing_index = -1
            elif self.active_action_input == "STALK_TARGET":
                self.action_input_buffer = payload["text"]
                if payload.get("submit", False) and not self.ignore_stalk_target_input:
                    self.stalk_target = self.action_input_buffer.strip()
                    self.action_input_buffer = ""
            elif self.active_action_input == "XRAY_ADD_BLOCK":
                self.action_input_buffer = payload["text"]
                if payload.get("submit", False) and not self.ignore_xray_block_add:
                    block_id = self.action_input_buffer.strip().lower()
                    if block_id and block_id not in self.xray_opaque_blocks:
                        self.xray_opaque_blocks.append(block_id)
                        self.xray_opaque_blocks.sort()
                    self.action_input_buffer = ""
            elif self.active_action_input == "MOB_ADD_NAME":
                self.action_input_buffer = payload["text"]
                if payload.get("submit", False) and not self.ignore_mob_filter_add:
                    name = self.action_input_buffer.strip()
                    if name and name not in self.mob_name_filters:
                        self.mob_name_filters.append(name)
                        self.mob_name_filters.sort(key=str.lower)
                    self.action_input_buffer = ""
            elif self.mapping_focused_field == "ORIGINAL":
                self.mapping_original_buffer = payload["text"]
            elif self.mapping_focused_field == "FAKE":
                self.mapping_fake_buffer = payload["text"]
            self.editing_string = False
            if self.popup is not None and self.popup.get("displayName") == "Stalk Player":
                self.popup = self.stalk_popup()
            elif self.popup is not None and self.popup.get("displayName") == "X-Ray":
                self.popup = self.xray_popup()
            elif self.popup is not None and self.popup.get("displayName") == "Mob ESP":
                self.popup = self.mob_popup()
            elif self.popup is not None and self.popup.get("displayName") == "Neck Hider":
                self.popup = self.neck_hider_popup()
            elif self.page == "CONE_HAT":
                self.popup = None
            else:
                self.popup = self.instance_popup()
            return {"ok": True, "handled": handled}
        if path == "/screen":
            if payload.get("screen") == "close":
                self.screen = ""
                self.screen_title = None
                self.popup = None
            else:
                self.screen = "floydaddons.not.dogshit.client.clickgui.LegacyFloydClickGUI"
                self.screen_title = None
                self.page = "HUB"
                self.popup = None
                self.expanded_xray_blocks = False
                self.expanded_mob_filters = False
                self.expanded_color = False
                self.expanded_stalk_target = False
                self.expanded_name_mappings = False
                self.mapping_focused_field = None
                self.mapping_original_buffer = ""
                self.mapping_fake_buffer = ""
                self.active_action_input = None
                self.action_input_buffer = ""
                self.text_editor_title = None
                self.pending_mapping_real = None
            return {"ok": True}
        if path != "/mouse":
            raise AssertionError(path)

        x = payload["x"]
        y = payload["y"]
        button = payload["button"]
        handled = False
        if self.page == "HUB" and inside(x, y, self.hub_button()):
            self.page = "CLICK_GUI"
            handled = True
        elif self.page == "HUB" and inside(x, y, self.hub_edit_ui_button()):
            self.page = "GUI_STYLE"
            handled = True
        elif self.page == "HUB" and inside(x, y, self.hub_render_label()):
            self.page = "RENDER"
            handled = True
        elif self.page == "HUB" and inside(x, y, self.hub_camera_label()):
            self.page = "CAMERA"
            handled = True
        elif self.page == "HUB" and inside(x, y, self.hub_cosmetic_label()):
            self.page = "COSMETIC"
            handled = True
        elif self.page == "HUB" and inside(x, y, self.hub_neck_hider_label()):
            self.page = "NICK_HIDER"
            handled = True
        elif self.page == "NICK_HIDER" and inside(x, y, self.nick_hider_player_size_bounds()):
            self.page = "PLAYER_SIZE"
            handled = True
        elif self.page == "NICK_HIDER" and inside(x, y, self.nick_hider_edit_names_bounds()):
            self.page = "NAME_MAPPINGS"
            handled = True
        elif self.page == "NAME_MAPPINGS" and inside(x, y, self.name_mapping_page_add_manual_bounds()):
            self.text_editor_title = "Map Real Name"
            handled = True
        elif self.page == "NAME_MAPPINGS" and inside(x, y, self.name_mapping_page_reveal_bounds()):
            handled = True
        elif self.page == "NAME_MAPPINGS" and inside(x, y, self.name_mapping_page_remove_bounds()):
            self.nick_name_mappings.pop("FloydPageReal", None)
            handled = True
        elif self.page == "PLAYER_SIZE" and inside(x, y, self.player_size_page_toggle_bounds()):
            if not self.ignore_player_size_page:
                self.player_size_enabled = not self.player_size_enabled
            handled = True
        elif self.page == "PLAYER_SIZE" and inside(x, y, self.player_size_page_target_bounds()):
            if not self.ignore_player_size_page:
                options = self.player_size_options()
                self.player_size_target = options[(options.index(self.player_size_target) + 1) % len(options)]
            handled = True
        elif self.page == "PLAYER_SIZE" and inside(x, y, self.player_size_page_x_bounds()):
            if not self.ignore_player_size_page:
                self.player_scale_x += -0.1 if button == 1 else 0.1
            handled = True
        elif self.page == "PLAYER_SIZE" and inside(x, y, self.player_size_page_y_bounds()):
            if not self.ignore_player_size_page:
                self.player_scale_y += -0.1 if button == 1 else 0.1
            handled = True
        elif self.page == "PLAYER_SIZE" and inside(x, y, self.player_size_page_z_bounds()):
            if not self.ignore_player_size_page:
                self.player_scale_z += -0.1 if button == 1 else 0.1
            handled = True
        elif self.page == "CAMERA" and inside(x, y, self.camera_disable_front_bounds()):
            if not self.ignore_camera_page:
                self.f5_disable_front = not self.f5_disable_front
            handled = True
        elif self.page == "CAMERA" and inside(x, y, self.camera_disable_back_bounds()):
            if not self.ignore_camera_page:
                self.f5_disable_back = not self.f5_disable_back
            handled = True
        elif self.page == "CAMERA" and inside(x, y, self.camera_no_clip_bounds()):
            if not self.ignore_camera_page:
                self.f5_no_clip = not self.f5_no_clip
            handled = True
        elif self.page == "CAMERA" and inside(x, y, self.camera_scroll_bounds()):
            if not self.ignore_camera_page:
                self.f5_scroll_enabled = not self.f5_scroll_enabled
            handled = True
        elif self.page == "CAMERA" and inside(x, y, self.camera_reset_bounds()):
            if not self.ignore_camera_page:
                self.f5_reset_on_toggle = not self.f5_reset_on_toggle
            handled = True
        elif self.page == "CAMERA" and inside(x, y, self.camera_speed_bounds()):
            if not self.ignore_camera_page:
                bounds = self.camera_speed_bounds()
                self.camera_speed = 0.1 + ((x - bounds["left"]) / (bounds["right"] - bounds["left"])) * 9.9
            handled = True
        elif self.page == "CAMERA" and inside(x, y, self.camera_f5_distance_bounds()):
            if not self.ignore_camera_page:
                bounds = self.camera_f5_distance_bounds()
                self.f5_distance = 1.0 + ((x - bounds["left"]) / (bounds["right"] - bounds["left"])) * 19.0
            handled = True
        elif self.page == "HIDERS" and inside(x, y, self.hiders_no_hurt_camera_bounds()):
            if not self.ignore_hiders_page:
                self.no_hurt_camera = not self.no_hurt_camera
            handled = True
        elif self.page == "HIDERS" and inside(x, y, self.hiders_remove_fire_overlay_bounds()):
            if not self.ignore_hiders_page:
                self.remove_fire_overlay = not self.remove_fire_overlay
            handled = True
        elif self.page == "HIDERS" and inside(x, y, self.hiders_hide_entity_fire_bounds()):
            if not self.ignore_hiders_page:
                self.hide_entity_fire = not self.hide_entity_fire
            handled = True
        elif self.page == "HIDERS" and inside(x, y, self.hiders_disable_arrows_bounds()):
            if not self.ignore_hiders_page:
                self.disable_attached_arrows = not self.disable_attached_arrows
            handled = True
        elif self.page == "HIDERS" and inside(x, y, self.hiders_no_explosion_particles_bounds()):
            if not self.ignore_hiders_page:
                self.remove_explosion_particles = not self.remove_explosion_particles
            handled = True
        elif self.page == "HIDERS" and inside(x, y, self.hiders_disable_hunger_bar_bounds()):
            if not self.ignore_hiders_page:
                self.disable_hunger_bar = not self.disable_hunger_bar
            handled = True
        elif self.page == "HIDERS" and inside(x, y, self.hiders_hide_potion_effects_bounds()):
            if not self.ignore_hiders_page:
                self.hide_potion_effects = not self.hide_potion_effects
            handled = True
        elif self.page == "HIDERS" and inside(x, y, self.hiders_third_person_crosshair_bounds()):
            if not self.ignore_hiders_page:
                self.third_person_crosshair = not self.third_person_crosshair
            handled = True
        elif self.page == "HIDERS" and inside(x, y, self.hiders_remove_falling_blocks_bounds()):
            if not self.ignore_hiders_page:
                self.remove_falling_blocks = not self.remove_falling_blocks
            handled = True
        elif self.page == "HIDERS" and inside(x, y, self.hiders_remove_tab_ping_bounds()):
            if not self.ignore_hiders_page:
                self.remove_tab_ping = not self.remove_tab_ping
            handled = True
        elif self.page == "HIDERS" and inside(x, y, self.hiders_no_armor_bounds()):
            if not self.ignore_hiders_page:
                options = self.no_armor_options()
                self.no_armor_target = options[(options.index(self.no_armor_target) + 1) % len(options)]
            handled = True
        elif self.page == "XRAY" and inside(x, y, self.xray_page_barrier_remove_bounds()):
            if not self.ignore_xray_page and "minecraft:barrier" in self.xray_opaque_blocks:
                self.xray_opaque_blocks.remove("minecraft:barrier")
            handled = True
        elif self.page == "ANIMATIONS" and inside(x, y, self.animations_enabled_bounds()):
            if not self.ignore_animations_page:
                self.animations_enabled = not self.animations_enabled
            handled = True
        elif self.page == "ANIMATIONS" and inside(x, y, self.animations_pos_x_bounds()):
            if not self.ignore_animations_page:
                bounds = self.animations_pos_x_bounds()
                self.anim_pos_x = -150.0 + ((x - bounds["left"]) / (bounds["right"] - bounds["left"])) * 300.0
            handled = True
        elif self.page == "ANIMATIONS" and inside(x, y, self.animations_cancel_re_equip_bounds()):
            if not self.ignore_animations_page:
                self.anim_cancel_re_equip = not self.anim_cancel_re_equip
            handled = True
        elif self.page == "ANIMATIONS" and inside(x, y, self.animations_hide_hand_bounds()):
            if not self.ignore_animations_page:
                self.anim_hide_hand = not self.anim_hide_hand
            handled = True
        elif self.page == "ANIMATIONS" and inside(x, y, self.animations_classic_click_bounds()):
            if not self.ignore_animations_page:
                self.anim_classic_click = not self.anim_classic_click
            handled = True
        elif self.page == "MOB_ESP" and inside(x, y, self.mob_esp_tracers_page_bounds()):
            if not self.ignore_mob_esp_page:
                self.mob_tracers = not self.mob_tracers
            handled = True
        elif self.page == "MOB_ESP" and inside(x, y, self.mob_esp_hitboxes_page_bounds()):
            if not self.ignore_mob_esp_page:
                self.mob_hitboxes = not self.mob_hitboxes
            handled = True
        elif self.page == "MOB_ESP" and inside(x, y, self.mob_esp_star_page_bounds()):
            if not self.ignore_mob_esp_page:
                self.mob_star_mobs = not self.mob_star_mobs
            handled = True
        elif self.page == "MOB_ESP" and inside(x, y, self.mob_esp_default_color_page_bounds()):
            self.color_picker_open = True
            self.color_picker_title = "Default ESP"
            self.color_picker_target = "mob_default"
            handled = True
        elif self.page == "MOB_ESP" and inside(x, y, self.mob_esp_tracer_color_page_bounds()):
            self.color_picker_open = True
            self.color_picker_title = "Stalk Tracer"
            self.color_picker_target = "mob_stalk"
            handled = True
        elif self.page == "MOB_ESP" and inside(x, y, self.mob_esp_edit_filters_page_bounds()):
            self.page = "MOB_ESP_FILTERS"
            handled = True
        elif self.page == "MOB_ESP_FILTERS" and inside(x, y, self.mob_filter_page_add_name_bounds()):
            self.text_editor_title = "Add Mob Name Filter"
            handled = True
        elif self.page == "MOB_ESP_FILTERS" and inside(x, y, self.mob_filter_page_color_bounds()):
            handled = True
        elif self.page == "MOB_ESP_FILTERS" and inside(x, y, self.mob_filter_page_chroma_bounds()):
            handled = True
        elif self.page == "MOB_ESP_FILTERS" and inside(x, y, self.mob_filter_page_remove_bounds()):
            if "FloydPageMob" in self.mob_name_filters:
                self.mob_name_filters.remove("FloydPageMob")
            handled = True
        elif self.page == "COSMETIC" and inside(x, y, self.cosmetic_custom_skin_bounds()):
            if not self.ignore_cosmetic_page:
                self.cosmetic_custom_skin = not self.cosmetic_custom_skin
            handled = True
        elif self.page == "COSMETIC" and inside(x, y, self.cosmetic_cape_bounds()):
            if not self.ignore_cosmetic_page:
                self.cosmetic_cape = not self.cosmetic_cape
            handled = True
        elif self.page == "COSMETIC" and inside(x, y, self.cosmetic_skin_config_bounds()):
            self.page = "SKIN"
            self.skin_dropdown_open = False
            handled = True
        elif self.page == "COSMETIC" and inside(x, y, self.cosmetic_cape_config_bounds()):
            self.page = "CAPE"
            handled = True
        elif self.page == "COSMETIC" and inside(x, y, self.cosmetic_cone_config_bounds()):
            self.page = "CONE_HAT"
            self.cone_dropdown_open = False
            self.cone_editing_index = -1
            handled = True
        elif self.page == "COSMETIC" and inside(x, y, self.cosmetic_cone_bounds()):
            if not self.ignore_cosmetic_page:
                self.cosmetic_cone = not self.cosmetic_cone
            handled = True
        elif self.page == "COSMETIC" and inside(x, y, self.cosmetic_target_bounds()):
            if not self.ignore_cosmetic_page:
                options = self.player_size_options()
                self.player_size_target = options[(options.index(self.player_size_target) + 1) % len(options)]
            handled = True
        elif self.page == "CAPE" and inside(x, y, self.cape_page_next_bounds()):
            if not self.ignore_cape_page:
                index = self.cape_available.index(self.cape_selected) if self.cape_selected in self.cape_available else -1
                self.cape_selected = self.cape_available[(index + 1) % len(self.cape_available)]
            handled = True
        elif self.page == "CAPE" and inside(x, y, self.cape_page_previous_bounds()):
            if not self.ignore_cape_page:
                index = self.cape_available.index(self.cape_selected) if self.cape_selected in self.cape_available else 0
                self.cape_selected = self.cape_available[(index - 1) % len(self.cape_available)]
            handled = True
        elif self.page == "CAPE" and inside(x, y, self.cape_page_open_folder_bounds()):
            handled = True
        elif self.page == "CONE_HAT" and self.cone_dropdown_open and inside(x, y, self.cone_dropdown_bounds()):
            row_height = (self.cone_dropdown_bounds()["bottom"] - self.cone_dropdown_bounds()["top"]) / max(1, len(self.cone_available))
            index = int((y - self.cone_dropdown_bounds()["top"]) / row_height)
            if not self.ignore_cone_page and 0 <= index < len(self.cone_available):
                self.cone_selected = self.cone_available[index]
            self.cone_dropdown_open = False
            handled = True
        elif self.page == "CONE_HAT" and inside(x, y, self.cone_page_slider_bounds(0)):
            if not self.ignore_cone_page:
                self.cone_height = self.cone_slider_value(0, x)
            handled = True
        elif self.page == "CONE_HAT" and any(inside(x, y, self.cone_page_input_bounds(index)) for index in range(5)):
            for index in range(5):
                if inside(x, y, self.cone_page_input_bounds(index)):
                    self.cone_editing_index = index
                    handled = True
                    break
        elif self.page == "CONE_HAT" and inside(x, y, self.cone_page_dropdown_button_bounds()):
            self.cone_dropdown_open = not self.cone_dropdown_open
            handled = True
        elif self.page == "CONE_HAT" and inside(x, y, self.cone_page_open_folder_bounds()):
            handled = True
        elif self.page == "SKIN" and self.skin_dropdown_open and inside(x, y, self.skin_dropdown_bounds()):
            row_height = (self.skin_dropdown_bounds()["bottom"] - self.skin_dropdown_bounds()["top"]) / max(1, len(self.skin_available))
            index = int((y - self.skin_dropdown_bounds()["top"]) / row_height)
            if not self.ignore_skin_page and 0 <= index < len(self.skin_available):
                self.skin_selected = self.skin_available[index]
            self.skin_dropdown_open = False
            handled = True
        elif self.page == "SKIN" and inside(x, y, self.skin_page_custom_bounds()):
            if not self.ignore_skin_page:
                self.cosmetic_custom_skin = not self.cosmetic_custom_skin
            handled = True
        elif self.page == "SKIN" and inside(x, y, self.skin_page_self_bounds()):
            if not self.ignore_skin_page:
                self.skin_self = not self.skin_self
            handled = True
        elif self.page == "SKIN" and inside(x, y, self.skin_page_others_bounds()):
            if not self.ignore_skin_page:
                self.skin_others = not self.skin_others
            handled = True
        elif self.page == "SKIN" and inside(x, y, self.skin_page_open_folder_bounds()):
            handled = True
        elif self.page == "SKIN" and inside(x, y, self.skin_page_dropdown_button_bounds()):
            self.skin_dropdown_open = not self.skin_dropdown_open
            handled = True
        elif self.page == "COSMETIC" and inside(x, y, self.cosmetic_x_bounds()):
            if not self.ignore_cosmetic_page:
                bounds = self.cosmetic_x_bounds()
                self.player_scale_x = -1.0 + ((x - bounds["left"]) / (bounds["right"] - bounds["left"])) * 6.0
            handled = True
        elif self.page == "COSMETIC" and inside(x, y, self.cosmetic_y_bounds()):
            if not self.ignore_cosmetic_page:
                bounds = self.cosmetic_y_bounds()
                self.player_scale_y = -1.0 + ((x - bounds["left"]) / (bounds["right"] - bounds["left"])) * 6.0
            handled = True
        elif self.page == "COSMETIC" and inside(x, y, self.cosmetic_z_bounds()):
            if not self.ignore_cosmetic_page:
                bounds = self.cosmetic_z_bounds()
                self.player_scale_z = -1.0 + ((x - bounds["left"]) / (bounds["right"] - bounds["left"])) * 6.0
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_time_changer_bounds()):
            if not self.ignore_render_page_time:
                self.render_custom_time = not self.render_custom_time
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_time_bounds()):
            if not self.ignore_render_page_time:
                bounds = self.render_time_bounds()
                self.render_time_value = ((x - bounds["left"]) / (bounds["right"] - bounds["left"])) * 100.0
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_server_id_bounds()):
            self.server_id_hider = not self.server_id_hider
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_profile_id_bounds()):
            self.profile_id_hider = not self.profile_id_hider
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_xray_toggle_bounds()):
            self.xray_enabled = not self.xray_enabled
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_opacity_bounds()):
            bounds = self.render_opacity_bounds()
            self.xray_opacity = 0.05 + ((x - bounds["left"]) / (bounds["right"] - bounds["left"])) * 0.95
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_reload_blocks_bounds()):
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_mob_toggle_bounds()):
            self.mob_enabled = not self.mob_enabled
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_edit_blocks_bounds()):
            self.page = "XRAY"
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_hiders_bounds()):
            self.page = "HIDERS"
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_animations_bounds()):
            self.page = "ANIMATIONS"
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_mob_config_bounds()):
            self.page = "MOB_ESP"
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_stalk_bounds()):
            self.stalk_target = ""
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_borderless_bounds()):
            self.borderless_windowed = not self.borderless_windowed
            handled = True
        elif self.page == "RENDER" and inside(x, y, self.render_title_bounds()):
            self.editing_string = True
            handled = True
        elif self.page == "GUI_STYLE" and inside(x, y, self.gui_style_text_pick_bounds()):
            self.color_picker_open = True
            self.color_picker_title = "Button Text"
            self.color_picker_target = "gui_text"
            handled = True
        elif self.color_picker_open and inside(x, y, self.color_picker_fade_bounds()):
            if self.color_picker_target == "gui_text" and not self.ignore_gui_style_fade:
                self.gui_text_fade = not self.gui_text_fade
                if self.gui_text_fade:
                    self.gui_text_chroma = False
            handled = True
        elif self.color_picker_open and inside(x, y, self.color_picker_chroma_bounds()):
            if self.color_picker_target == "gui_text":
                self.gui_text_chroma = not self.gui_text_chroma
                if self.gui_text_chroma:
                    self.gui_text_fade = False
            elif self.color_picker_target == "mob_default":
                self.mob_default_chroma = not self.mob_default_chroma
            elif self.color_picker_target == "mob_stalk":
                self.stalk_chroma = not self.stalk_chroma
            handled = True
        elif self.color_picker_open and inside(x, y, self.color_picker_apply_bounds()):
            self.color_picker_open = False
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.xray_bounds()):
            if button == 0:
                self.xray_enabled = not self.xray_enabled
            self.expanded_xray_blocks = False
            self.active_action_input = None
            self.action_input_buffer = ""
            self.popup = self.xray_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.mob_bounds()):
            if button == 0 or self.toggle_mob_on_right_click:
                self.mob_enabled = not self.mob_enabled
            self.expanded_mob_filters = False
            self.expanded_color = False
            self.active_action_input = None
            self.action_input_buffer = ""
            self.popup = self.mob_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.stalk_bounds()):
            if button == 0:
                self.stalk_target = ""
            self.expanded_stalk_target = False
            self.expanded_stalk_color = False
            self.active_action_input = None
            self.action_input_buffer = ""
            self.popup = self.stalk_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.inventory_hud_bounds()):
            self.popup = self.inventory_hud_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.custom_scoreboard_bounds()):
            self.popup = self.custom_scoreboard_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.neck_hider_bounds()):
            self.expanded_name_mappings = False
            self.mapping_focused_field = None
            self.mapping_original_buffer = ""
            self.mapping_fake_buffer = ""
            self.popup = self.neck_hider_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.cape_bounds()):
            self.popup = self.cape_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.cone_hat_bounds()):
            self.popup = self.cone_hat_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.custom_skin_bounds()):
            self.popup = self.custom_skin_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.player_size_bounds()):
            self.popup = self.player_size_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "X-Ray" and inside(x, y, self.opacity_bounds()):
            if not self.ignore_slider:
                self.xray_opacity = 0.05 + ((x - (self.opacity_bounds()["left"] + 8)) / (self.opacity_bounds()["right"] - self.opacity_bounds()["left"] - 16)) * 0.95
            self.popup = self.xray_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "X-Ray" and inside(x, y, self.xray_extra_bounds()):
            self.expanded_xray_blocks = not self.expanded_xray_blocks
            self.popup = self.xray_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "X-Ray" and self.expanded_xray_blocks and inside(x, y, self.xray_input_bounds()):
            self.active_action_input = "XRAY_ADD_BLOCK"
            self.action_input_buffer = ""
            self.popup = self.xray_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "X-Ray" and self.expanded_xray_blocks and inside(x, y, self.xray_submit_bounds()):
            if self.action_input_buffer and not self.ignore_xray_block_add:
                block_id = self.action_input_buffer.strip().lower()
                if block_id not in self.xray_opaque_blocks:
                    self.xray_opaque_blocks.append(block_id)
                    self.xray_opaque_blocks.sort()
            self.action_input_buffer = ""
            self.active_action_input = "XRAY_ADD_BLOCK"
            self.popup = self.xray_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "X-Ray" and self.expanded_xray_blocks and inside(x, y, self.xray_barrier_remove_bounds()):
            if "minecraft:barrier" in self.xray_opaque_blocks:
                self.xray_opaque_blocks.remove("minecraft:barrier")
            self.popup = self.xray_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Mob ESP" and self.expanded_color and inside(x, y, self.default_color_chroma_bounds()):
            if not self.ignore_color_chroma:
                self.mob_default_chroma = not self.mob_default_chroma
            self.popup = self.mob_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Mob ESP" and inside(x, y, self.tracers_bounds()):
            if not self.ignore_popup_toggle:
                self.mob_tracers = not self.mob_tracers
            self.popup = self.mob_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Mob ESP" and inside(x, y, self.default_color_bounds()):
            self.expanded_color = True
            self.popup = self.mob_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Mob ESP" and inside(x, y, self.mob_filters_extra_bounds()):
            self.expanded_mob_filters = not self.expanded_mob_filters
            self.popup = self.mob_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Mob ESP" and self.expanded_mob_filters and inside(x, y, self.mob_name_input_bounds()):
            self.active_action_input = "MOB_ADD_NAME"
            self.action_input_buffer = ""
            self.popup = self.mob_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Mob ESP" and self.expanded_mob_filters and inside(x, y, self.mob_name_submit_bounds()):
            if self.action_input_buffer and not self.ignore_mob_filter_add:
                name = self.action_input_buffer.strip()
                if name not in self.mob_name_filters:
                    self.mob_name_filters.append(name)
                    self.mob_name_filters.sort(key=str.lower)
            self.action_input_buffer = ""
            self.active_action_input = "MOB_ADD_NAME"
            self.popup = self.mob_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Mob ESP" and self.expanded_mob_filters and inside(x, y, self.mob_verifier_remove_bounds()):
            if "FloydVerifierMob" in self.mob_name_filters:
                self.mob_name_filters.remove("FloydVerifierMob")
            self.popup = self.mob_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.instance_bounds()):
            if button == 0:
                self.instance_title = ""
            self.popup = self.instance_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.freecam_bounds()):
            self.popup = self.freecam_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.freelook_bounds()):
            self.popup = self.freelook_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.f5_bounds()):
            self.popup = self.f5_popup()
            handled = True
        elif self.page == "CLICK_GUI" and inside(x, y, self.no_armor_bounds()):
            if button == 0 and not self.ignore_selector:
                self.no_armor_target = "Self" if self.no_armor_target == "Off" else "Off"
            self.popup = self.no_armor_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Instance Name" and inside(x, y, self.instance_title_bounds()):
            self.editing_string = True
            self.popup = self.instance_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Freecam" and inside(x, y, self.freecam_speed_bounds()):
            if not self.ignore_camera_slider:
                bounds = self.freecam_speed_bounds()
                self.camera_speed = 0.1 + ((x - (bounds["left"] + 8)) / (bounds["right"] - bounds["left"] - 16)) * 9.9
            self.popup = self.freecam_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Freelook" and inside(x, y, self.freelook_distance_bounds()):
            if not self.ignore_camera_slider:
                bounds = self.freelook_distance_bounds()
                self.freelook_distance = 1.0 + ((x - (bounds["left"] + 8)) / (bounds["right"] - bounds["left"] - 16)) * 19.0
            self.popup = self.freelook_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "F5 Customizer" and inside(x, y, self.f5_disable_front_bounds()):
            self.f5_disable_front = not self.f5_disable_front
            self.popup = self.f5_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "F5 Customizer" and inside(x, y, self.f5_disable_back_bounds()):
            self.f5_disable_back = not self.f5_disable_back
            self.popup = self.f5_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "F5 Customizer" and inside(x, y, self.f5_no_clip_bounds()):
            self.f5_no_clip = not self.f5_no_clip
            self.popup = self.f5_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "F5 Customizer" and inside(x, y, self.f5_scroll_bounds()):
            self.f5_scroll_enabled = not self.f5_scroll_enabled
            self.popup = self.f5_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "F5 Customizer" and inside(x, y, self.f5_reset_bounds()):
            self.f5_reset_on_toggle = not self.f5_reset_on_toggle
            self.popup = self.f5_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "F5 Customizer" and inside(x, y, self.f5_distance_bounds()):
            if not self.ignore_camera_slider:
                bounds = self.f5_distance_bounds()
                self.f5_distance = 1.0 + ((x - (bounds["left"] + 8)) / (bounds["right"] - bounds["left"] - 16)) * 19.0
            self.popup = self.f5_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "No Armor" and inside(x, y, self.no_armor_target_bounds()):
            if not self.ignore_selector:
                options = self.no_armor_options()
                self.no_armor_target = options[(options.index(self.no_armor_target) + 1) % len(options)]
            self.popup = self.no_armor_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Player Size" and inside(x, y, self.player_size_target_bounds()):
            if not self.ignore_selector:
                options = self.player_size_options()
                self.player_size_target = options[(options.index(self.player_size_target) + 1) % len(options)]
            self.popup = self.player_size_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Player Size" and inside(x, y, self.player_size_x_bounds()):
            if not self.ignore_player_size_slider:
                bounds = self.player_size_x_bounds()
                self.player_scale_x = -1.0 + ((x - (bounds["left"] + 8)) / (bounds["right"] - bounds["left"] - 16)) * 6.0
            self.popup = self.player_size_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Player Size" and inside(x, y, self.player_size_y_bounds()):
            if not self.ignore_player_size_slider:
                bounds = self.player_size_y_bounds()
                self.player_scale_y = -1.0 + ((x - (bounds["left"] + 8)) / (bounds["right"] - bounds["left"] - 16)) * 6.0
            self.popup = self.player_size_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Player Size" and inside(x, y, self.player_size_z_bounds()):
            if not self.ignore_player_size_slider:
                bounds = self.player_size_z_bounds()
                self.player_scale_z = -1.0 + ((x - (bounds["left"] + 8)) / (bounds["right"] - bounds["left"] - 16)) * 6.0
            self.popup = self.player_size_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Stalk Player" and inside(x, y, self.stalk_extra_bounds()):
            self.expanded_stalk_target = not self.expanded_stalk_target
            self.popup = self.stalk_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Stalk Player" and self.expanded_stalk_color and inside(x, y, self.stalk_color_chroma_bounds()):
            if not self.ignore_color_chroma:
                self.stalk_chroma = not self.stalk_chroma
            self.popup = self.stalk_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Stalk Player" and self.expanded_stalk_target and inside(x, y, self.stalk_input_bounds()):
            self.active_action_input = "STALK_TARGET"
            self.action_input_buffer = ""
            self.popup = self.stalk_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Stalk Player" and self.expanded_stalk_target and inside(x, y, self.stalk_submit_bounds()):
            if self.action_input_buffer and not self.ignore_stalk_target_input:
                self.stalk_target = self.action_input_buffer.strip()
                self.action_input_buffer = ""
            self.active_action_input = "STALK_TARGET"
            self.popup = self.stalk_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Stalk Player" and inside(x, y, self.stalk_color_bounds()):
            self.expanded_stalk_color = True
            self.popup = self.stalk_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Neck Hider" and inside(x, y, self.name_mappings_extra_bounds()):
            self.expanded_name_mappings = not self.expanded_name_mappings
            self.popup = self.neck_hider_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Neck Hider" and self.expanded_name_mappings and inside(x, y, self.name_mapping_original_bounds()):
            self.mapping_focused_field = "ORIGINAL"
            self.popup = self.neck_hider_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Neck Hider" and self.expanded_name_mappings and inside(x, y, self.name_mapping_fake_bounds()):
            self.mapping_focused_field = "FAKE"
            self.popup = self.neck_hider_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Neck Hider" and self.expanded_name_mappings and inside(x, y, self.name_mapping_save_bounds()):
            real = self.mapping_original_buffer.strip()
            fake = self.mapping_fake_buffer.strip()
            if real and fake and not self.ignore_name_mapping_save:
                self.nick_name_mappings[real] = fake
            self.mapping_original_buffer = ""
            self.mapping_fake_buffer = ""
            self.mapping_focused_field = "ORIGINAL"
            self.popup = self.neck_hider_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Neck Hider" and self.expanded_name_mappings and inside(x, y, self.name_mapping_remove_bounds()):
            self.nick_name_mappings.pop("FloydVerifierReal", None)
            self.popup = self.neck_hider_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Cape" and inside(x, y, self.cape_image_bounds()):
            if not self.ignore_cape_cycle:
                index = self.cape_available.index(self.cape_selected) if self.cape_selected in self.cape_available else -1
                self.cape_selected = self.cape_available[(index + 1) % len(self.cape_available)]
            self.popup = self.cape_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Cape" and inside(x, y, self.cape_open_folder_bounds()):
            self.popup = self.cape_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Cone Hat" and any(inside(x, y, self.cone_number_bounds(index)) for index in range(5)):
            for index in range(5):
                if inside(x, y, self.cone_number_bounds(index)):
                    self.set_cone_value(index, self.cone_popup_slider_value(index, x))
                    break
            self.popup = self.cone_hat_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Cone Hat" and inside(x, y, self.cone_image_bounds()):
            if not self.ignore_cone_cycle:
                index = self.cone_available.index(self.cone_selected) if self.cone_selected in self.cone_available else -1
                self.cone_selected = self.cone_available[(index + 1) % len(self.cone_available)]
            self.popup = self.cone_hat_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Cone Hat" and inside(x, y, self.cone_open_folder_bounds()):
            self.popup = self.cone_hat_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Custom Skin" and inside(x, y, self.skin_self_bounds()):
            if not self.ignore_skin_toggle:
                self.skin_self = not self.skin_self
            self.popup = self.custom_skin_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Custom Skin" and inside(x, y, self.skin_others_bounds()):
            if not self.ignore_skin_toggle:
                self.skin_others = not self.skin_others
            self.popup = self.custom_skin_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Custom Skin" and inside(x, y, self.skin_image_bounds()):
            if not self.ignore_skin_cycle:
                index = self.skin_available.index(self.skin_selected) if self.skin_selected in self.skin_available else -1
                self.skin_selected = self.skin_available[(index + 1) % len(self.skin_available)]
            self.popup = self.custom_skin_popup()
            handled = True
        elif self.popup is not None and self.popup.get("displayName") == "Custom Skin" and inside(x, y, self.skin_open_folder_bounds()):
            self.popup = self.custom_skin_popup()
            handled = True
        elif (
            self.popup is not None
            and self.popup.get("displayName") in {"Inventory HUD", "Custom Scoreboard"}
            and inside(x, y, self.inventory_hud_layout_bounds())
        ):
            if not self.ignore_hud_layout:
                self.screen = "floydaddons.not.dogshit.client.clickgui.HudManager"
                self.screen_title = "HUD Manager"
                self.popup = None
            handled = True
        return {"ok": True, "handled": handled}

    def state(self) -> dict[str, Any]:
        return {
            "screen": self.screen,
            "screenTitle": self.screen_title,
            "render": {
                "xray": {"xrayEnabled": self.xray_enabled, "opacity": self.xray_opacity, "opaqueBlocks": list(self.xray_opaque_blocks)},
                "mobEsp": {
                    "enabled": self.mob_enabled,
                    "tracers": self.mob_tracers,
                    "hitboxes": self.mob_hitboxes,
                    "starMobs": self.mob_star_mobs,
                    "nameFilters": list(self.mob_name_filters),
                    "typeFilters": [],
                    "stalkEnabled": bool(self.stalk_target),
                    "stalkTarget": self.stalk_target,
                    "settings": {"stalkChroma": self.stalk_chroma, "defaultChroma": self.mob_default_chroma},
                },
                "core": {
                    "windowTitle": self.instance_title,
                    "customTime": self.render_custom_time,
                    "customTimeValue": self.render_time_value,
                    "borderlessWindowed": self.borderless_windowed,
                },
                "hiders": {
                    "settings": {
                        "noHurtCamera": self.no_hurt_camera,
                        "removeFireOverlay": self.remove_fire_overlay,
                        "hideEntityFire": self.hide_entity_fire,
                        "disableAttachedArrows": self.disable_attached_arrows,
                        "removeExplosionParticles": self.remove_explosion_particles,
                        "disableHungerBar": self.disable_hunger_bar,
                        "hidePotionEffects": self.hide_potion_effects,
                        "thirdPersonCrosshair": self.third_person_crosshair,
                        "removeFallingBlocks": self.remove_falling_blocks,
                        "removeTabPing": self.remove_tab_ping,
                        "serverIdHider": self.server_id_hider,
                        "profileIdHider": self.profile_id_hider,
                        "noArmorMode": self.no_armor_target,
                    }
                },
                "animations": {
                    "enabled": self.animations_enabled,
                    "position": {"x": self.anim_pos_x / 100.0, "y": 0.0, "z": 0.0},
                    "rotation": {"x": 0.0, "y": 0.0, "z": 0.0},
                    "scale": 1.0,
                    "swingDuration": 6,
                    "cancelReEquip": self.anim_cancel_re_equip,
                    "hideEmptyMainHand": self.anim_hide_hand,
                    "classicClick": self.anim_classic_click,
                    "settings": {
                        "posX": self.anim_pos_x,
                        "cancelReEquip": self.anim_cancel_re_equip,
                        "hideHand": self.anim_hide_hand,
                        "classicClick": self.anim_classic_click,
                    },
                },
            },
            "camera": {
                "features": {
                    "freecamSpeed": self.camera_speed,
                    "freelookDistance": self.freelook_distance,
                    "f5": {
                        "disableFront": self.f5_disable_front,
                        "disableBack": self.f5_disable_back,
                        "noClip": self.f5_no_clip,
                        "scrollEnabled": self.f5_scroll_enabled,
                        "resetOnToggle": self.f5_reset_on_toggle,
                        "distance": self.f5_distance,
                    },
                }
            },
            "playerFeatures": {
                "nickHider": {
                    "settings": {
                        "nameMappings": dict(sorted(self.nick_name_mappings.items(), key=lambda item: item[0].lower())),
                    }
                },
                "playerSize": {
                    "enabled": self.player_size_enabled,
                    "settings": {
                        "scaleX": self.player_scale_x,
                        "scaleY": self.player_scale_y,
                        "scaleZ": self.player_scale_z,
                        "sizeTarget": self.player_size_target,
                    }
                }
            },
            "cosmetics": {
                "cape": {
                    "capeEnabled": self.cosmetic_cape,
                    "selectedCape": self.cape_selected,
                    "availableCapes": list(self.cape_available),
                },
                "coneHat": {
                    "coneHatEnabled": self.cosmetic_cone,
                    "selectedImage": self.cone_selected,
                    "availableImages": list(self.cone_available),
                    "height": self.cone_height,
                    "radius": self.cone_radius,
                    "yOffset": self.cone_y_offset,
                    "rotation": self.cone_rotation,
                    "rotationSpeed": self.cone_spin_speed,
                },
                "skin": {
                    "settings": {
                        "customSkin": self.cosmetic_custom_skin,
                        "self": self.skin_self,
                        "others": self.skin_others,
                        "selectedSkin": self.skin_selected,
                        "availableSkins": list(self.skin_available),
                    }
                }
            },
            "clickGui": {
                "legacyButtonTextChroma": self.gui_text_chroma,
                "legacyButtonTextFade": self.gui_text_fade,
            },
            "legacyGui": {
                "page": self.page,
                "labels": {
                    "Render": self.hub_render_label(),
                    "Camera": self.hub_camera_label(),
                    "Cosmetic": self.hub_cosmetic_label(),
                    "Neck Hider": self.hub_neck_hider_label(),
                },
                "rows": self.page_rows(),
                "textEditor": self.text_editor_title,
                "renderEditor": {"entries": self.render_page_entries() if self.page == "RENDER" else [], "activeSlider": None, "titleFocused": self.editing_string},
                "xrayEditor": {"entries": self.xray_page_entries() if self.page == "XRAY" else []},
                "mobFilterEditor": {
                    "entries": self.mob_filter_page_entries() if self.page == "MOB_ESP_FILTERS" else [],
                    "expandedColor": {"key": "FloydPageMob", "isName": True} if self.page == "MOB_ESP_FILTERS" and "FloydPageMob" in self.mob_name_filters else None,
                },
                "nameMappingEditor": {"entries": self.name_mapping_page_entries() if self.page == "NAME_MAPPINGS" else []},
                "animationsEditor": {"entries": self.animations_page_entries() if self.page == "ANIMATIONS" else []},
                "mobEspEditor": {"entries": self.mob_esp_page_entries() if self.page == "MOB_ESP" else []},
                "cameraEditor": {"entries": self.camera_page_entries() if self.page == "CAMERA" else []},
                "hidersEditor": {"entries": self.hiders_page_entries() if self.page == "HIDERS" else []},
                "cosmeticEditor": {"entries": self.cosmetic_page_entries() if self.page == "COSMETIC" else []},
                "nickHiderEditor": {"entries": self.nick_hider_page_entries() if self.page == "NICK_HIDER" else []},
                "skinSettingsEditor": {"entries": self.skin_page_entries() if self.page == "SKIN" else []},
                "skinDropdownOpen": self.skin_dropdown_open,
                "skinDropdownButton": self.skin_page_dropdown_button_bounds() if self.page == "SKIN" else {"left": 0, "top": 0, "right": 0, "bottom": 0},
                "skinDropdownBounds": self.skin_dropdown_bounds() if self.page == "SKIN" and self.skin_dropdown_open else {"left": 0, "top": 0, "right": 0, "bottom": 0},
                "skinDropdownItems": list(self.skin_available),
                "capeButtons": {
                    "previous": self.cape_page_previous_bounds() if self.page == "CAPE" else {"left": 0, "top": 0, "right": 0, "bottom": 0},
                    "next": self.cape_page_next_bounds() if self.page == "CAPE" else {"left": 0, "top": 0, "right": 0, "bottom": 0},
                    "openFolder": self.cape_page_open_folder_bounds() if self.page == "CAPE" else {"left": 0, "top": 0, "right": 0, "bottom": 0},
                },
                "coneControls": self.cone_controls_state() if self.page == "CONE_HAT" else {
                    "sliders": [],
                    "inputs": [],
                    "dropdownButton": {"left": 0, "top": 0, "right": 0, "bottom": 0},
                    "dropdownBounds": {"left": 0, "top": 0, "right": 0, "bottom": 0},
                    "dropdownOpen": False,
                    "dropdownItems": list(self.cone_available),
                    "openFolder": {"left": 0, "top": 0, "right": 0, "bottom": 0},
                    "editingIndex": -1,
                    "editBuffer": "",
                },
                "hubButtons": {"clickGui": self.hub_button(), "editUi": self.hub_edit_ui_button()},
                "colorPicker": self.color_picker_title if self.color_picker_open else None,
                "colorPickerBounds": self.color_picker_bounds() if self.color_picker_open else None,
                "guiStyleEditor": {
                    "entries": [
                        {"target": "TEXT", "bounds": self.gui_style_text_pick_bounds()},
                        {"target": "BUTTON_BORDER", "bounds": {"left": 194, "top": 246, "right": 230, "bottom": 264}},
                        {"target": "GUI_BORDER", "bounds": {"left": 194, "top": 270, "right": 230, "bottom": 288}},
                    ]
                },
                "moduleBrowser": {
                    "entries": [
                        {"displayName": "X-Ray", "bounds": self.xray_bounds()},
                        {"displayName": "Mob ESP", "bounds": self.mob_bounds()},
                        {"displayName": "Stalk Player", "bounds": self.stalk_bounds()},
                        {"displayName": "Inventory HUD", "bounds": self.inventory_hud_bounds()},
                        {"displayName": "Custom Scoreboard", "bounds": self.custom_scoreboard_bounds()},
                        {"displayName": "Instance Name", "bounds": self.instance_bounds()},
                        {"displayName": "Freecam", "bounds": self.freecam_bounds()},
                        {"displayName": "Freelook", "bounds": self.freelook_bounds()},
                        {"displayName": "F5 Customizer", "bounds": self.f5_bounds()},
                        {"displayName": "Neck Hider", "bounds": self.neck_hider_bounds()},
                        {"displayName": "No Armor", "bounds": self.no_armor_bounds()},
                        {"displayName": "Cape", "bounds": self.cape_bounds()},
                        {"displayName": "Cone Hat", "bounds": self.cone_hat_bounds()},
                        {"displayName": "Custom Skin", "bounds": self.custom_skin_bounds()},
                        {"displayName": "Player Size", "bounds": self.player_size_bounds()},
                    ]
                },
                "modulePopup": self.popup,
            },
        }

    @staticmethod
    def hub_button() -> dict[str, int]:
        return {"left": 246, "top": 191, "right": 320, "bottom": 209}

    @staticmethod
    def hub_edit_ui_button() -> dict[str, int]:
        return {"left": 650, "top": 169, "right": 714, "bottom": 187}

    @staticmethod
    def hub_render_label() -> dict[str, int]:
        return {"left": 915, "top": 566, "right": 1006, "bottom": 589}

    @staticmethod
    def hub_camera_label() -> dict[str, int]:
        return {"left": 928, "top": 630, "right": 994, "bottom": 653}

    @staticmethod
    def hub_cosmetic_label() -> dict[str, int]:
        return {"left": 907, "top": 532, "right": 1013, "bottom": 555}

    @staticmethod
    def hub_neck_hider_label() -> dict[str, int]:
        return {"left": 893, "top": 600, "right": 1027, "bottom": 623}

    @staticmethod
    def render_server_id_bounds() -> dict[str, int]:
        return {"left": 360, "top": 56, "right": 600, "bottom": 74}

    @staticmethod
    def render_profile_id_bounds() -> dict[str, int]:
        return {"left": 360, "top": 79, "right": 600, "bottom": 97}

    @staticmethod
    def render_xray_toggle_bounds() -> dict[str, int]:
        return {"left": 360, "top": 125, "right": 600, "bottom": 143}

    @staticmethod
    def render_opacity_bounds() -> dict[str, int]:
        return {"left": 360, "top": 148, "right": 600, "bottom": 166}

    @staticmethod
    def render_time_changer_bounds() -> dict[str, int]:
        return {"left": 360, "top": 286, "right": 476, "bottom": 304}

    def page_rows(self) -> list[dict[str, Any]]:
        if self.page == "RENDER":
            return self.render_page_rows()
        if self.page == "PLAYER_SIZE":
            return self.player_size_page_rows()
        return []

    @staticmethod
    def render_time_bounds() -> dict[str, int]:
        return {"left": 480, "top": 286, "right": 596, "bottom": 304}

    @staticmethod
    def render_edit_blocks_bounds() -> dict[str, int]:
        return {"left": 360, "top": 171, "right": 476, "bottom": 189}

    @staticmethod
    def render_reload_blocks_bounds() -> dict[str, int]:
        return {"left": 480, "top": 171, "right": 596, "bottom": 189}

    @staticmethod
    def render_mob_toggle_bounds() -> dict[str, int]:
        return {"left": 360, "top": 217, "right": 526, "bottom": 235}

    @staticmethod
    def render_hiders_bounds() -> dict[str, int]:
        return {"left": 360, "top": 263, "right": 476, "bottom": 281}

    @staticmethod
    def render_mob_config_bounds() -> dict[str, int]:
        return {"left": 530, "top": 217, "right": 596, "bottom": 235}

    @staticmethod
    def render_animations_bounds() -> dict[str, int]:
        return {"left": 480, "top": 263, "right": 596, "bottom": 281}

    @staticmethod
    def render_stalk_bounds() -> dict[str, int]:
        return {"left": 360, "top": 309, "right": 600, "bottom": 327}

    @staticmethod
    def render_borderless_bounds() -> dict[str, int]:
        return {"left": 360, "top": 332, "right": 600, "bottom": 350}

    @staticmethod
    def render_title_bounds() -> dict[str, int]:
        return {"left": 360, "top": 355, "right": 600, "bottom": 373}

    def render_page_entries(self) -> list[dict[str, Any]]:
        return [
            {"kind": "BOOLEAN_TOGGLE", "settingName": "Server ID Hider", "bounds": self.render_server_id_bounds()},
            {"kind": "BOOLEAN_TOGGLE", "settingName": "Profile ID Hider", "bounds": self.render_profile_id_bounds()},
            {"kind": "XRAY_TOGGLE", "settingName": "X-Ray", "bounds": self.render_xray_toggle_bounds()},
            {"kind": "SLIDER", "settingName": "Opacity", "bounds": self.render_opacity_bounds()},
            {"kind": "NAV_XRAY", "settingName": "Edit Blocks", "bounds": self.render_edit_blocks_bounds()},
            {"kind": "RELOAD_XRAY", "settingName": "Reload Blocks", "bounds": self.render_reload_blocks_bounds()},
            {"kind": "MODULE_TOGGLE", "settingName": "Mob ESP", "bounds": self.render_mob_toggle_bounds()},
            {"kind": "NAV_MOB_ESP", "settingName": "Config", "bounds": self.render_mob_config_bounds()},
            {"kind": "NAV_HIDERS", "settingName": "Hiders", "bounds": self.render_hiders_bounds()},
            {"kind": "NAV_ANIMATIONS", "settingName": "Attack Animation", "bounds": self.render_animations_bounds()},
            {"kind": "BOOLEAN_TOGGLE", "settingName": "Time Changer", "bounds": self.render_time_changer_bounds()},
            {"kind": "SLIDER", "settingName": "Time", "bounds": self.render_time_bounds()},
            {"kind": "STALK", "settingName": "Stalk", "bounds": self.render_stalk_bounds()},
            {"kind": "BORDERLESS", "settingName": "Borderless Window", "bounds": self.render_borderless_bounds()},
            {"kind": "TITLE_FIELD", "settingName": "Instance Title", "bounds": self.render_title_bounds()},
        ]

    def render_page_rows(self) -> list[dict[str, Any]]:
        return [
            {"label": f"Server ID Hider: {'ON' if self.server_id_hider else 'OFF'}", "kind": "BUTTON", "bounds": self.render_server_id_bounds()},
            {"label": f"Profile ID Hider: {'ON' if self.profile_id_hider else 'OFF'}", "kind": "BUTTON", "bounds": self.render_profile_id_bounds()},
            {"label": "X-Ray", "kind": "HEADER", "bounds": {"left": 360, "top": 102, "right": 600, "bottom": 120}},
            {"label": f"X-Ray: {'ON' if self.xray_enabled else 'OFF'}", "kind": "BUTTON", "bounds": self.render_xray_toggle_bounds()},
            {"label": f"X-Ray Opacity: {round(self.xray_opacity * 100)}%", "kind": "BUTTON", "bounds": self.render_opacity_bounds()},
            {"label": "Edit Blocks", "kind": "BUTTON", "bounds": self.render_edit_blocks_bounds()},
            {"label": "Reload Blocks", "kind": "BUTTON", "bounds": self.render_reload_blocks_bounds()},
            {"label": "Mob ESP", "kind": "HEADER", "bounds": {"left": 360, "top": 194, "right": 600, "bottom": 212}},
            {"label": f"Mob ESP: {'ON' if self.mob_enabled else 'OFF'}", "kind": "BUTTON", "bounds": self.render_mob_toggle_bounds()},
            {"label": "Config", "kind": "BUTTON", "bounds": self.render_mob_config_bounds()},
            {"label": "Other", "kind": "HEADER", "bounds": {"left": 360, "top": 240, "right": 600, "bottom": 258}},
            {"label": "Hiders", "kind": "BUTTON", "bounds": self.render_hiders_bounds()},
            {"label": "Attack Animation", "kind": "BUTTON", "bounds": self.render_animations_bounds()},
            {"label": f"Time Changer: {'ON' if self.render_custom_time else 'OFF'}", "kind": "BUTTON", "bounds": self.render_time_changer_bounds()},
            {"label": f"Time: {round(self.render_time_value)}%", "kind": "BUTTON", "bounds": self.render_time_bounds()},
            {"label": "Stalk", "kind": "BUTTON", "bounds": self.render_stalk_bounds()},
            {"label": f"Borderless Window: {'ON' if self.borderless_windowed else 'OFF'}", "kind": "BUTTON", "bounds": self.render_borderless_bounds()},
            {"label": f"Instance Title: {self.instance_title}", "kind": "BUTTON", "bounds": self.render_title_bounds()},
        ]

    @staticmethod
    def xray_page_barrier_remove_bounds() -> dict[str, int]:
        return {"left": 676, "top": 90, "right": 694, "bottom": 106}

    def xray_page_entries(self) -> list[dict[str, Any]]:
        entries: list[dict[str, Any]] = []
        if "minecraft:barrier" in self.xray_opaque_blocks:
            entries.append({"block": "minecraft:barrier", "add": False, "bounds": self.xray_page_barrier_remove_bounds()})
        return entries

    @staticmethod
    def animations_enabled_bounds() -> dict[str, int]:
        return {"left": 850, "top": 512, "right": 1070, "bottom": 532}

    @staticmethod
    def animations_pos_x_bounds() -> dict[str, int]:
        return {"left": 850, "top": 536, "right": 1070, "bottom": 556}

    @staticmethod
    def animations_cancel_re_equip_bounds() -> dict[str, int]:
        return {"left": 850, "top": 728, "right": 1070, "bottom": 748}

    @staticmethod
    def animations_hide_hand_bounds() -> dict[str, int]:
        return {"left": 850, "top": 752, "right": 1070, "bottom": 772}

    @staticmethod
    def animations_classic_click_bounds() -> dict[str, int]:
        return {"left": 850, "top": 776, "right": 1070, "bottom": 796}

    def animations_page_entries(self) -> list[dict[str, Any]]:
        sliders = [
            ("Pos X", self.animations_pos_x_bounds()),
            ("Pos Y", {"left": 850, "top": 560, "right": 1070, "bottom": 580}),
            ("Pos Z", {"left": 850, "top": 584, "right": 1070, "bottom": 604}),
            ("Rot X", {"left": 850, "top": 608, "right": 1070, "bottom": 628}),
            ("Rot Y", {"left": 850, "top": 632, "right": 1070, "bottom": 652}),
            ("Rot Z", {"left": 850, "top": 656, "right": 1070, "bottom": 676}),
            ("Scale", {"left": 850, "top": 680, "right": 1070, "bottom": 700}),
            ("Swing Duration", {"left": 850, "top": 704, "right": 1070, "bottom": 724}),
        ]
        return [{"kind": "TOGGLE_MODULE", "settingName": "", "bounds": self.animations_enabled_bounds()}] + [
            {"kind": "SLIDER", "settingName": name, "bounds": bounds} for name, bounds in sliders
        ] + [
            {"kind": "TOGGLE_SETTING", "settingName": "Cancel Re-Equip", "bounds": self.animations_cancel_re_equip_bounds()},
            {"kind": "TOGGLE_SETTING", "settingName": "Hide Hand", "bounds": self.animations_hide_hand_bounds()},
            {"kind": "TOGGLE_SETTING", "settingName": "Classic Click", "bounds": self.animations_classic_click_bounds()},
        ]

    @staticmethod
    def mob_esp_tracers_page_bounds() -> dict[str, int]:
        return {"left": 850, "top": 508, "right": 1070, "bottom": 528}

    @staticmethod
    def mob_esp_hitboxes_page_bounds() -> dict[str, int]:
        return {"left": 850, "top": 534, "right": 1070, "bottom": 554}

    @staticmethod
    def mob_esp_star_page_bounds() -> dict[str, int]:
        return {"left": 850, "top": 560, "right": 1070, "bottom": 580}

    @staticmethod
    def mob_esp_default_color_page_bounds() -> dict[str, int]:
        return {"left": 1002, "top": 612, "right": 1070, "bottom": 632}

    @staticmethod
    def mob_esp_tracer_color_page_bounds() -> dict[str, int]:
        return {"left": 1002, "top": 638, "right": 1070, "bottom": 658}

    @staticmethod
    def mob_esp_edit_filters_page_bounds() -> dict[str, int]:
        return {"left": 850, "top": 664, "right": 1070, "bottom": 684}

    @staticmethod
    def mob_filter_page_add_name_bounds() -> dict[str, int]:
        return {"left": 794, "top": 472, "right": 959, "bottom": 488}

    @staticmethod
    def mob_filter_page_color_bounds() -> dict[str, int]:
        return {"left": 816, "top": 518, "right": 826, "bottom": 528}

    @staticmethod
    def mob_filter_page_chroma_bounds() -> dict[str, int]:
        return {"left": 1040, "top": 626, "right": 1128, "bottom": 638}

    @staticmethod
    def mob_filter_page_remove_bounds() -> dict[str, int]:
        return {"left": 1110, "top": 510, "right": 1128, "bottom": 526}

    def mob_filter_page_entries(self) -> list[dict[str, Any]]:
        entries: list[dict[str, Any]] = [
            {"kind": "ADD_MANUAL_NAME", "key": "", "bounds": self.mob_filter_page_add_name_bounds()},
        ]
        if "FloydPageMob" in self.mob_name_filters:
            entries.extend([
                {"kind": "REMOVE_NAME", "key": "FloydPageMob", "bounds": self.mob_filter_page_remove_bounds()},
                {"kind": "COLOR", "key": "FloydPageMob", "bounds": self.mob_filter_page_color_bounds()},
                {"kind": "PICKER_CHROMA", "key": "FloydPageMob", "bounds": self.mob_filter_page_chroma_bounds()},
            ])
        return entries

    def mob_esp_page_entries(self) -> list[dict[str, Any]]:
        return [
            {"kind": "TOGGLE", "settingName": "Tracers", "bounds": self.mob_esp_tracers_page_bounds()},
            {"kind": "TOGGLE", "settingName": "Hitboxes", "bounds": self.mob_esp_hitboxes_page_bounds()},
            {"kind": "TOGGLE", "settingName": "Star Mobs", "bounds": self.mob_esp_star_page_bounds()},
            {"kind": "COLOR_PICK", "settingName": "Default ESP Color", "bounds": self.mob_esp_default_color_page_bounds()},
            {"kind": "COLOR_PICK", "settingName": "Tracer Color", "bounds": self.mob_esp_tracer_color_page_bounds()},
            {"kind": "NAV_FILTERS", "settingName": "Edit Filters", "bounds": self.mob_esp_edit_filters_page_bounds()},
        ]

    @staticmethod
    def hiders_no_hurt_camera_bounds() -> dict[str, int]:
        return {"left": 850, "top": 512, "right": 1090, "bottom": 532}

    @staticmethod
    def hiders_remove_fire_overlay_bounds() -> dict[str, int]:
        return {"left": 850, "top": 536, "right": 1090, "bottom": 556}

    @staticmethod
    def hiders_hide_entity_fire_bounds() -> dict[str, int]:
        return {"left": 850, "top": 560, "right": 1090, "bottom": 580}

    @staticmethod
    def hiders_disable_arrows_bounds() -> dict[str, int]:
        return {"left": 850, "top": 584, "right": 1090, "bottom": 604}

    @staticmethod
    def hiders_no_explosion_particles_bounds() -> dict[str, int]:
        return {"left": 850, "top": 608, "right": 1090, "bottom": 628}

    @staticmethod
    def hiders_disable_hunger_bar_bounds() -> dict[str, int]:
        return {"left": 850, "top": 632, "right": 1090, "bottom": 652}

    @staticmethod
    def hiders_hide_potion_effects_bounds() -> dict[str, int]:
        return {"left": 850, "top": 656, "right": 1090, "bottom": 676}

    @staticmethod
    def hiders_third_person_crosshair_bounds() -> dict[str, int]:
        return {"left": 850, "top": 680, "right": 1090, "bottom": 700}

    @staticmethod
    def hiders_remove_falling_blocks_bounds() -> dict[str, int]:
        return {"left": 850, "top": 704, "right": 1090, "bottom": 724}

    @staticmethod
    def hiders_remove_tab_ping_bounds() -> dict[str, int]:
        return {"left": 850, "top": 728, "right": 1090, "bottom": 748}

    @staticmethod
    def hiders_no_armor_bounds() -> dict[str, int]:
        return {"left": 850, "top": 752, "right": 1090, "bottom": 772}

    @staticmethod
    def no_armor_options() -> list[str]:
        return ["Off", "Self", "Others", "All"]

    def hiders_page_entries(self) -> list[dict[str, Any]]:
        settings = [
            ("No Hurt Camera", self.hiders_no_hurt_camera_bounds()),
            ("Remove Fire Overlay", self.hiders_remove_fire_overlay_bounds()),
            ("Hide Entity Fire", self.hiders_hide_entity_fire_bounds()),
            ("Disable Arrows", self.hiders_disable_arrows_bounds()),
            ("No Explosion Particles", self.hiders_no_explosion_particles_bounds()),
            ("Disable Hunger Bar", self.hiders_disable_hunger_bar_bounds()),
            ("Hide Potion Effects", self.hiders_hide_potion_effects_bounds()),
            ("3rd Person Crosshair", self.hiders_third_person_crosshair_bounds()),
            ("Remove Falling Blocks", self.hiders_remove_falling_blocks_bounds()),
            ("Remove Tab Ping", self.hiders_remove_tab_ping_bounds()),
        ]
        return [{"kind": "TOGGLE", "settingName": name, "bounds": bounds} for name, bounds in settings] + [
            {"kind": "NO_ARMOR", "settingName": "Target", "bounds": self.hiders_no_armor_bounds()}
        ]

    @staticmethod
    def camera_freecam_bounds() -> dict[str, int]:
        return {"left": 850, "top": 512, "right": 958, "bottom": 532}

    @staticmethod
    def camera_speed_bounds() -> dict[str, int]:
        return {"left": 962, "top": 512, "right": 1070, "bottom": 532}

    @staticmethod
    def camera_freelook_bounds() -> dict[str, int]:
        return {"left": 850, "top": 564, "right": 958, "bottom": 584}

    @staticmethod
    def camera_distance_bounds() -> dict[str, int]:
        return {"left": 962, "top": 564, "right": 1070, "bottom": 584}

    @staticmethod
    def camera_disable_front_bounds() -> dict[str, int]:
        return {"left": 850, "top": 616, "right": 958, "bottom": 636}

    @staticmethod
    def camera_disable_back_bounds() -> dict[str, int]:
        return {"left": 962, "top": 616, "right": 1070, "bottom": 636}

    @staticmethod
    def camera_no_clip_bounds() -> dict[str, int]:
        return {"left": 850, "top": 642, "right": 1070, "bottom": 662}

    @staticmethod
    def camera_scroll_bounds() -> dict[str, int]:
        return {"left": 850, "top": 668, "right": 1070, "bottom": 688}

    @staticmethod
    def camera_reset_bounds() -> dict[str, int]:
        return {"left": 850, "top": 694, "right": 1070, "bottom": 714}

    @staticmethod
    def camera_f5_distance_bounds() -> dict[str, int]:
        return {"left": 850, "top": 720, "right": 1070, "bottom": 740}

    def camera_page_entries(self) -> list[dict[str, Any]]:
        return [
            {"kind": "RUNTIME_TOGGLE", "settingName": "Freecam", "bounds": self.camera_freecam_bounds()},
            {"kind": "SLIDER", "settingName": "Speed", "bounds": self.camera_speed_bounds()},
            {"kind": "RUNTIME_TOGGLE", "settingName": "Freelook", "bounds": self.camera_freelook_bounds()},
            {"kind": "SLIDER", "settingName": "Distance", "bounds": self.camera_distance_bounds()},
            {"kind": "BOOLEAN_TOGGLE", "settingName": "Disable Front Cam", "bounds": self.camera_disable_front_bounds()},
            {"kind": "BOOLEAN_TOGGLE", "settingName": "Disable Back Cam", "bounds": self.camera_disable_back_bounds()},
            {"kind": "BOOLEAN_TOGGLE", "settingName": "No Third-Person Clipping", "bounds": self.camera_no_clip_bounds()},
            {"kind": "BOOLEAN_TOGGLE", "settingName": "Scrolling Changes Distance", "bounds": self.camera_scroll_bounds()},
            {"kind": "BOOLEAN_TOGGLE", "settingName": "Reset F5 Scrolling", "bounds": self.camera_reset_bounds()},
            {"kind": "SLIDER", "settingName": "Camera Distance", "bounds": self.camera_f5_distance_bounds()},
        ]

    @staticmethod
    def cosmetic_custom_skin_bounds() -> dict[str, int]:
        return {"left": 850, "top": 512, "right": 998, "bottom": 532}

    @staticmethod
    def cosmetic_skin_config_bounds() -> dict[str, int]:
        return {"left": 1002, "top": 512, "right": 1070, "bottom": 532}

    @staticmethod
    def cosmetic_cape_bounds() -> dict[str, int]:
        return {"left": 850, "top": 538, "right": 998, "bottom": 558}

    @staticmethod
    def cosmetic_cape_config_bounds() -> dict[str, int]:
        return {"left": 1002, "top": 538, "right": 1070, "bottom": 558}

    @staticmethod
    def cosmetic_cone_bounds() -> dict[str, int]:
        return {"left": 850, "top": 564, "right": 998, "bottom": 584}

    @staticmethod
    def cosmetic_cone_config_bounds() -> dict[str, int]:
        return {"left": 1002, "top": 564, "right": 1070, "bottom": 584}

    @staticmethod
    def cosmetic_target_bounds() -> dict[str, int]:
        return {"left": 850, "top": 616, "right": 1070, "bottom": 636}

    @staticmethod
    def cosmetic_x_bounds() -> dict[str, int]:
        return {"left": 850, "top": 642, "right": 1070, "bottom": 662}

    @staticmethod
    def cosmetic_y_bounds() -> dict[str, int]:
        return {"left": 850, "top": 668, "right": 1070, "bottom": 688}

    @staticmethod
    def cosmetic_z_bounds() -> dict[str, int]:
        return {"left": 850, "top": 694, "right": 1070, "bottom": 714}

    def cosmetic_page_entries(self) -> list[dict[str, Any]]:
        return [
            {"kind": "TOGGLE_SKIN", "settingName": "Custom Skin", "bounds": self.cosmetic_custom_skin_bounds()},
            {"kind": "NAV_CONFIG", "settingName": "SKIN", "bounds": self.cosmetic_skin_config_bounds()},
            {"kind": "TOGGLE_CAPE", "settingName": "Cape", "bounds": self.cosmetic_cape_bounds()},
            {"kind": "NAV_CONFIG", "settingName": "CAPE", "bounds": self.cosmetic_cape_config_bounds()},
            {"kind": "TOGGLE_CONE", "settingName": "Cone Hat", "bounds": self.cosmetic_cone_bounds()},
            {"kind": "NAV_CONFIG", "settingName": "CONE_HAT", "bounds": self.cosmetic_cone_config_bounds()},
            {"kind": "TARGET", "settingName": "Target", "bounds": self.cosmetic_target_bounds()},
            {"kind": "SLIDER", "settingName": "X", "bounds": self.cosmetic_x_bounds()},
            {"kind": "SLIDER", "settingName": "Y", "bounds": self.cosmetic_y_bounds()},
            {"kind": "SLIDER", "settingName": "Z", "bounds": self.cosmetic_z_bounds()},
        ]

    @staticmethod
    def skin_page_custom_bounds() -> dict[str, int]:
        return {"left": 370, "top": 195, "right": 590, "bottom": 215}

    @staticmethod
    def skin_page_self_bounds() -> dict[str, int]:
        return {"left": 370, "top": 221, "right": 590, "bottom": 241}

    @staticmethod
    def skin_page_others_bounds() -> dict[str, int]:
        return {"left": 370, "top": 247, "right": 590, "bottom": 267}

    @staticmethod
    def skin_page_open_folder_bounds() -> dict[str, int]:
        return {"left": 370, "top": 273, "right": 590, "bottom": 293}

    @staticmethod
    def skin_page_dropdown_button_bounds() -> dict[str, int]:
        return {"left": 370, "top": 299, "right": 590, "bottom": 315}

    @staticmethod
    def skin_dropdown_bounds() -> dict[str, int]:
        return {"left": 370, "top": 317, "right": 590, "bottom": 349}

    def skin_page_entries(self) -> list[dict[str, Any]]:
        return [
            {"kind": "TOGGLE", "settingName": "Custom Skin", "bounds": self.skin_page_custom_bounds()},
            {"kind": "TOGGLE", "settingName": "Self", "bounds": self.skin_page_self_bounds()},
            {"kind": "TOGGLE", "settingName": "Others", "bounds": self.skin_page_others_bounds()},
            {"kind": "OPEN_FOLDER", "settingName": "Open Skin Folder", "bounds": self.skin_page_open_folder_bounds()},
            {"kind": "DROPDOWN", "settingName": "Skin", "bounds": self.skin_page_dropdown_button_bounds()},
        ]

    @staticmethod
    def cape_page_previous_bounds() -> dict[str, int]:
        return {"left": 856, "top": 580, "right": 886, "bottom": 598}

    @staticmethod
    def cape_page_next_bounds() -> dict[str, int]:
        return {"left": 1034, "top": 580, "right": 1064, "bottom": 598}

    @staticmethod
    def cape_page_open_folder_bounds() -> dict[str, int]:
        return {"left": 905, "top": 606, "right": 1015, "bottom": 624}

    @staticmethod
    def cone_page_slider_bounds(index: int) -> dict[str, int]:
        top = 503 + index * 26
        return {"left": 780, "top": top, "right": 946, "bottom": top + 20, "width": 166, "height": 20}

    @staticmethod
    def cone_page_input_bounds(index: int) -> dict[str, int]:
        top = 503 + index * 26
        return {"left": 950, "top": top, "right": 1000, "bottom": top + 20, "width": 50, "height": 20}

    @staticmethod
    def cone_page_dropdown_button_bounds() -> dict[str, int]:
        return {"left": 780, "top": 633, "right": 928, "bottom": 653, "width": 148, "height": 20}

    @staticmethod
    def cone_dropdown_bounds() -> dict[str, int]:
        return {"left": 780, "top": 655, "right": 928, "bottom": 687, "width": 148, "height": 32}

    @staticmethod
    def cone_page_open_folder_bounds() -> dict[str, int]:
        return {"left": 932, "top": 633, "right": 1000, "bottom": 653, "width": 68, "height": 20}

    @staticmethod
    def cone_number_bounds(index: int) -> dict[str, int]:
        top = 74 + index * 28
        return {"left": 562, "top": top, "right": 742, "bottom": top + 28}

    def cone_controls_state(self) -> dict[str, Any]:
        return {
            "sliders": [self.cone_page_slider_bounds(index) for index in range(5)],
            "inputs": [self.cone_page_input_bounds(index) for index in range(5)],
            "dropdownButton": self.cone_page_dropdown_button_bounds(),
            "dropdownBounds": self.cone_dropdown_bounds() if self.cone_dropdown_open else {"left": 0, "top": 0, "right": 0, "bottom": 0, "width": 0, "height": 0},
            "dropdownOpen": self.cone_dropdown_open,
            "dropdownItems": list(self.cone_available),
            "openFolder": self.cone_page_open_folder_bounds(),
            "editingIndex": self.cone_editing_index,
            "editBuffer": "",
        }

    def cone_slider_value(self, index: int, x: float) -> float:
        bounds = self.cone_page_slider_bounds(index)
        fraction = (x - bounds["left"]) / (bounds["right"] - bounds["left"])
        if index == 0:
            return 0.1 + fraction * 1.4
        if index == 1:
            return 0.05 + fraction * 0.75
        return fraction

    def cone_popup_slider_value(self, index: int, x: float) -> float:
        bounds = self.cone_number_bounds(index)
        fraction = (x - (bounds["left"] + 8)) / (bounds["right"] - bounds["left"] - 16)
        if index == 0:
            return 0.1 + fraction * 1.4
        if index == 1:
            return 0.05 + fraction * 0.75
        if index == 2:
            return -1.5 + fraction * 2.0
        if index in {3, 4}:
            return fraction * 360.0
        return fraction

    def set_cone_value(self, index: int, value: float) -> None:
        if index == 0:
            self.cone_height = value
        elif index == 1:
            self.cone_radius = value
        elif index == 2:
            self.cone_y_offset = value
        elif index == 3:
            self.cone_rotation = value
        elif index == 4:
            self.cone_spin_speed = value

    @staticmethod
    def gui_style_text_pick_bounds() -> dict[str, int]:
        return {"left": 194, "top": 222, "right": 230, "bottom": 240}

    @staticmethod
    def color_picker_apply_bounds() -> dict[str, int]:
        return {"left": 316, "top": 411, "right": 412, "bottom": 429}

    @staticmethod
    def color_picker_chroma_bounds() -> dict[str, int]:
        return {"left": 416, "top": 371, "right": 490, "bottom": 389}

    @staticmethod
    def color_picker_fade_bounds() -> dict[str, int]:
        return {"left": 496, "top": 371, "right": 570, "bottom": 389}

    def color_picker_bounds(self) -> dict[str, dict[str, int]]:
        return {
            "modal": {"left": 300, "top": 156, "right": 660, "bottom": 444},
            "apply": self.color_picker_apply_bounds(),
            "chroma": self.color_picker_chroma_bounds(),
            "fade": self.color_picker_fade_bounds(),
        }

    @staticmethod
    def xray_bounds() -> dict[str, int]:
        return {"left": 10, "top": 54, "right": 138, "bottom": 74}

    @staticmethod
    def mob_bounds() -> dict[str, int]:
        return {"left": 10, "top": 74, "right": 138, "bottom": 94}

    @staticmethod
    def stalk_bounds() -> dict[str, int]:
        return {"left": 10, "top": 154, "right": 138, "bottom": 174}

    @staticmethod
    def inventory_hud_bounds() -> dict[str, int]:
        return {"left": 10, "top": 174, "right": 138, "bottom": 194}

    @staticmethod
    def custom_scoreboard_bounds() -> dict[str, int]:
        return {"left": 10, "top": 194, "right": 138, "bottom": 214}

    @staticmethod
    def neck_hider_bounds() -> dict[str, int]:
        return {"left": 430, "top": 54, "right": 558, "bottom": 74}

    @staticmethod
    def tracers_bounds() -> dict[str, int]:
        return {"left": 142, "top": 94, "right": 392, "bottom": 112}

    @staticmethod
    def opacity_bounds() -> dict[str, int]:
        return {"left": 142, "top": 94, "right": 392, "bottom": 122}

    @staticmethod
    def xray_extra_bounds() -> dict[str, int]:
        return {"left": 142, "top": 122, "right": 392, "bottom": 140}

    @staticmethod
    def xray_input_bounds() -> dict[str, int]:
        return {"left": 150, "top": 142, "right": 362, "bottom": 156}

    @staticmethod
    def xray_submit_bounds() -> dict[str, int]:
        return {"left": 366, "top": 142, "right": 384, "bottom": 156}

    @staticmethod
    def xray_barrier_remove_bounds() -> dict[str, int]:
        return {"left": 372, "top": 174, "right": 388, "bottom": 188}

    def default_color_bounds(self) -> dict[str, int]:
        if self.expanded_color:
            return {"left": 142, "top": 148, "right": 392, "bottom": 302}
        return {"left": 142, "top": 148, "right": 392, "bottom": 166}

    def default_color_chroma_bounds(self) -> dict[str, int]:
        bounds = self.default_color_bounds()
        sv_top = bounds["top"] + 18 + 6
        sv_bottom = sv_top + 100
        return {"left": bounds["right"] - 82, "top": sv_bottom + 6, "right": bounds["right"] - 8, "bottom": sv_bottom + 24}

    def stalk_color_bounds(self) -> dict[str, int]:
        top = 212 if self.expanded_stalk_target else 174
        if self.expanded_stalk_color:
            return {"left": 142, "top": top, "right": 392, "bottom": top + 154}
        return {"left": 142, "top": top, "right": 392, "bottom": top + 18}

    def stalk_color_chroma_bounds(self) -> dict[str, int]:
        bounds = self.stalk_color_bounds()
        sv_top = bounds["top"] + 18 + 6
        sv_bottom = sv_top + 100
        return {"left": bounds["right"] - 82, "top": sv_bottom + 6, "right": bounds["right"] - 8, "bottom": sv_bottom + 24}

    @staticmethod
    def mob_filters_extra_bounds() -> dict[str, int]:
        return {"left": 142, "top": 166, "right": 392, "bottom": 184}

    @staticmethod
    def mob_name_input_bounds() -> dict[str, int]:
        return {"left": 150, "top": 186, "right": 362, "bottom": 200}

    @staticmethod
    def mob_name_submit_bounds() -> dict[str, int]:
        return {"left": 366, "top": 186, "right": 384, "bottom": 200}

    @staticmethod
    def mob_type_input_bounds() -> dict[str, int]:
        return {"left": 150, "top": 204, "right": 362, "bottom": 218}

    @staticmethod
    def mob_type_submit_bounds() -> dict[str, int]:
        return {"left": 366, "top": 204, "right": 384, "bottom": 218}

    @staticmethod
    def mob_verifier_remove_bounds() -> dict[str, int]:
        return {"left": 372, "top": 236, "right": 388, "bottom": 250}

    @staticmethod
    def instance_bounds() -> dict[str, int]:
        return {"left": 10, "top": 234, "right": 138, "bottom": 254}

    @staticmethod
    def instance_title_bounds() -> dict[str, int]:
        return {"left": 142, "top": 254, "right": 300, "bottom": 272}

    @staticmethod
    def freecam_bounds() -> dict[str, int]:
        return {"left": 1010, "top": 54, "right": 1138, "bottom": 74}

    @staticmethod
    def freelook_bounds() -> dict[str, int]:
        return {"left": 1010, "top": 74, "right": 1138, "bottom": 94}

    @staticmethod
    def f5_bounds() -> dict[str, int]:
        return {"left": 1010, "top": 94, "right": 1138, "bottom": 114}

    @staticmethod
    def freecam_speed_bounds() -> dict[str, int]:
        return {"left": 1142, "top": 74, "right": 1322, "bottom": 102}

    @staticmethod
    def freelook_distance_bounds() -> dict[str, int]:
        return {"left": 1142, "top": 74, "right": 1322, "bottom": 102}

    @staticmethod
    def f5_disable_front_bounds() -> dict[str, int]:
        return {"left": 1142, "top": 74, "right": 1322, "bottom": 92}

    @staticmethod
    def f5_disable_back_bounds() -> dict[str, int]:
        return {"left": 1142, "top": 92, "right": 1322, "bottom": 110}

    @staticmethod
    def f5_no_clip_bounds() -> dict[str, int]:
        return {"left": 1142, "top": 110, "right": 1322, "bottom": 128}

    @staticmethod
    def f5_scroll_bounds() -> dict[str, int]:
        return {"left": 1142, "top": 128, "right": 1322, "bottom": 146}

    @staticmethod
    def f5_reset_bounds() -> dict[str, int]:
        return {"left": 1142, "top": 146, "right": 1322, "bottom": 164}

    @staticmethod
    def f5_distance_bounds() -> dict[str, int]:
        return {"left": 1142, "top": 164, "right": 1322, "bottom": 192}

    @staticmethod
    def no_armor_bounds() -> dict[str, int]:
        return {"left": 220, "top": 54, "right": 348, "bottom": 74}

    @staticmethod
    def cape_bounds() -> dict[str, int]:
        return {"left": 830, "top": 54, "right": 958, "bottom": 74}

    @staticmethod
    def cone_hat_bounds() -> dict[str, int]:
        return {"left": 830, "top": 74, "right": 958, "bottom": 94}

    @staticmethod
    def custom_skin_bounds() -> dict[str, int]:
        return {"left": 830, "top": 94, "right": 958, "bottom": 114}

    @staticmethod
    def player_size_bounds() -> dict[str, int]:
        return {"left": 830, "top": 114, "right": 958, "bottom": 134}

    @staticmethod
    def no_armor_target_bounds() -> dict[str, int]:
        return {"left": 352, "top": 74, "right": 532, "bottom": 92}

    @staticmethod
    def no_armor_options() -> list[str]:
        return ["Off", "Self", "Others", "All"]

    @staticmethod
    def player_size_options() -> list[str]:
        return ["Self", "Real Players", "All"]

    @staticmethod
    def stalk_extra_bounds() -> dict[str, int]:
        return {"left": 142, "top": 174, "right": 392, "bottom": 192}

    @staticmethod
    def stalk_input_bounds() -> dict[str, int]:
        return {"left": 150, "top": 194, "right": 362, "bottom": 208}

    @staticmethod
    def stalk_submit_bounds() -> dict[str, int]:
        return {"left": 366, "top": 194, "right": 384, "bottom": 208}

    @staticmethod
    def name_mappings_extra_bounds() -> dict[str, int]:
        return {"left": 562, "top": 74, "right": 812, "bottom": 92}

    @staticmethod
    def name_mapping_original_bounds() -> dict[str, int]:
        return {"left": 570, "top": 94, "right": 662, "bottom": 108}

    @staticmethod
    def name_mapping_fake_bounds() -> dict[str, int]:
        return {"left": 682, "top": 94, "right": 774, "bottom": 108}

    @staticmethod
    def name_mapping_save_bounds() -> dict[str, int]:
        return {"left": 788, "top": 94, "right": 804, "bottom": 108}

    @staticmethod
    def name_mapping_remove_bounds() -> dict[str, int]:
        return {"left": 794, "top": 126, "right": 810, "bottom": 140}

    @staticmethod
    def inventory_hud_layout_bounds() -> dict[str, int]:
        return {"left": 142, "top": 194, "right": 322, "bottom": 212}

    @staticmethod
    def cape_image_bounds() -> dict[str, int]:
        return {"left": 562, "top": 94, "right": 742, "bottom": 112}

    @staticmethod
    def cape_open_folder_bounds() -> dict[str, int]:
        return {"left": 562, "top": 112, "right": 742, "bottom": 130}

    @staticmethod
    def cone_image_bounds() -> dict[str, int]:
        return {"left": 562, "top": 214, "right": 742, "bottom": 232}

    @staticmethod
    def cone_open_folder_bounds() -> dict[str, int]:
        return {"left": 562, "top": 232, "right": 742, "bottom": 250}

    @staticmethod
    def skin_self_bounds() -> dict[str, int]:
        return {"left": 562, "top": 74, "right": 742, "bottom": 92}

    @staticmethod
    def skin_others_bounds() -> dict[str, int]:
        return {"left": 562, "top": 92, "right": 742, "bottom": 110}

    @staticmethod
    def skin_image_bounds() -> dict[str, int]:
        return {"left": 562, "top": 110, "right": 742, "bottom": 128}

    @staticmethod
    def skin_open_folder_bounds() -> dict[str, int]:
        return {"left": 562, "top": 128, "right": 742, "bottom": 146}

    @staticmethod
    def player_size_target_bounds() -> dict[str, int]:
        return {"left": 562, "top": 74, "right": 742, "bottom": 92}

    @staticmethod
    def player_size_x_bounds() -> dict[str, int]:
        return {"left": 562, "top": 92, "right": 742, "bottom": 120}

    @staticmethod
    def player_size_y_bounds() -> dict[str, int]:
        return {"left": 562, "top": 120, "right": 742, "bottom": 148}

    @staticmethod
    def player_size_z_bounds() -> dict[str, int]:
        return {"left": 562, "top": 148, "right": 742, "bottom": 176}

    @staticmethod
    def nick_hider_player_size_bounds() -> dict[str, int]:
        return {"left": 850, "top": 621, "right": 1070, "bottom": 641}

    @staticmethod
    def nick_hider_edit_names_bounds() -> dict[str, int]:
        return {"left": 850, "top": 593, "right": 957, "bottom": 613}

    def nick_hider_page_entries(self) -> list[dict[str, Any]]:
        return [
            {"kind": "TOGGLE", "bounds": {"left": 850, "top": 565, "right": 1070, "bottom": 585}},
            {"kind": "EDIT_NAMES", "bounds": self.nick_hider_edit_names_bounds()},
            {"kind": "RELOAD_NAMES", "bounds": {"left": 963, "top": 593, "right": 1070, "bottom": 613}},
            {"kind": "PLAYER_SIZE", "bounds": self.nick_hider_player_size_bounds()},
        ]

    @staticmethod
    def name_mapping_page_add_manual_bounds() -> dict[str, int]:
        return {"left": 816, "top": 399, "right": 1084, "bottom": 417}

    @staticmethod
    def name_mapping_page_reveal_bounds() -> dict[str, int]:
        return {"left": 830, "top": 433, "right": 1030, "bottom": 442}

    @staticmethod
    def name_mapping_page_remove_bounds() -> dict[str, int]:
        return {"left": 1062, "top": 426, "right": 1078, "bottom": 442}

    def name_mapping_page_entries(self) -> list[dict[str, Any]]:
        entries: list[dict[str, Any]] = [
            {"kind": "ADD_MANUAL", "realName": "", "bounds": self.name_mapping_page_add_manual_bounds()},
        ]
        if "FloydPageReal" in self.nick_name_mappings:
            entries.append({"kind": "REMOVE", "realName": "FloydPageReal", "bounds": self.name_mapping_page_remove_bounds()})
            entries.append({"kind": "REVEAL", "realName": "FloydPageReal", "bounds": self.name_mapping_page_reveal_bounds()})
        return entries

    @staticmethod
    def player_size_page_toggle_bounds() -> dict[str, int]:
        return {"left": 840, "top": 551, "right": 1080, "bottom": 569}

    @staticmethod
    def player_size_page_target_bounds() -> dict[str, int]:
        return {"left": 840, "top": 597, "right": 1080, "bottom": 615}

    @staticmethod
    def player_size_page_x_bounds() -> dict[str, int]:
        return {"left": 840, "top": 620, "right": 1080, "bottom": 638}

    @staticmethod
    def player_size_page_y_bounds() -> dict[str, int]:
        return {"left": 840, "top": 643, "right": 1080, "bottom": 661}

    @staticmethod
    def player_size_page_z_bounds() -> dict[str, int]:
        return {"left": 840, "top": 666, "right": 1080, "bottom": 684}

    @staticmethod
    def player_size_page_scale_value(bounds: dict[str, int], x: float) -> float:
        return -1.0 + ((x - (bounds["left"] + 8)) / (bounds["right"] - bounds["left"] - 16)) * 6.0

    def player_size_page_rows(self) -> list[dict[str, Any]]:
        return [
            {"label": f"Player Size: {'ON' if self.player_size_enabled else 'OFF'}", "kind": "BUTTON", "bounds": self.player_size_page_toggle_bounds()},
            {"label": "Player Size", "kind": "HEADER", "bounds": {"left": 840, "top": 574, "right": 1080, "bottom": 592}},
            {"label": f"Target: {self.player_size_target}", "kind": "BUTTON", "bounds": self.player_size_page_target_bounds()},
            {"label": f"Size X: {self.player_scale_x:.1f}", "kind": "BUTTON", "bounds": self.player_size_page_x_bounds()},
            {"label": f"Size Y: {self.player_scale_y:.1f}", "kind": "BUTTON", "bounds": self.player_size_page_y_bounds()},
            {"label": f"Size Z: {self.player_scale_z:.1f}", "kind": "BUTTON", "bounds": self.player_size_page_z_bounds()},
        ]

    def xray_popup(self) -> dict[str, Any]:
        xray_entries = []
        if self.expanded_xray_blocks:
            xray_entries.extend([
                {"block": "", "add": True, "action": "XRAY_ADD_BLOCK", "submit": False, "bounds": self.xray_input_bounds()},
                {"block": "", "add": True, "action": "XRAY_ADD_BLOCK", "submit": True, "bounds": self.xray_submit_bounds()},
            ])
            if "minecraft:barrier" in self.xray_opaque_blocks:
                xray_entries.append({"block": "minecraft:barrier", "add": False, "action": None, "submit": False, "bounds": self.xray_barrier_remove_bounds()})
        return {
            "displayName": "X-Ray",
            "expandedExtra": "XRAY_BLOCKS" if self.expanded_xray_blocks else None,
            "actionInput": self.active_action_input,
            "actionInputBuffer": self.action_input_buffer if self.active_action_input is not None else None,
            "entries": [{"settingName": "Opacity", "value": self.xray_opacity, "bounds": self.opacity_bounds()}],
            "extraEntries": [] if self.omit_xray_extra else [{"label": "Edit Blocks", "bounds": self.xray_extra_bounds()}],
            "xrayEntries": xray_entries,
        }

    def mob_popup(self) -> dict[str, Any]:
        mob_entries = []
        if self.expanded_mob_filters:
            mob_entries.extend([
                {"key": "", "kind": "ADD_MANUAL_NAME", "submit": False, "bounds": self.mob_name_input_bounds()},
                {"key": "", "kind": "ADD_MANUAL_NAME", "submit": True, "bounds": self.mob_name_submit_bounds()},
                {"key": "", "kind": "ADD_MANUAL_TYPE", "submit": False, "bounds": self.mob_type_input_bounds()},
                {"key": "", "kind": "ADD_MANUAL_TYPE", "submit": True, "bounds": self.mob_type_submit_bounds()},
            ])
            if "FloydVerifierMob" in self.mob_name_filters:
                mob_entries.append({"key": "FloydVerifierMob", "kind": "REMOVE_NAME", "submit": False, "bounds": self.mob_verifier_remove_bounds()})
        return {
            "displayName": "Mob ESP",
            "expandedExtra": "MOB_FILTERS" if self.expanded_mob_filters else None,
            "actionInput": self.active_action_input,
            "actionInputBuffer": self.action_input_buffer if self.active_action_input is not None else None,
            "entries": [
                {"settingName": "Tracers", "value": self.mob_tracers, "bounds": self.tracers_bounds()},
                {"settingName": "Hitboxes", "value": False, "bounds": {"left": 142, "top": 112, "right": 392, "bottom": 130}},
                {"settingName": "Star Mobs", "value": False, "bounds": {"left": 142, "top": 130, "right": 392, "bottom": 148}},
                {
                    "settingName": "Default ESP Color",
                    "value": "#FFFFFFFF",
                    "chroma": self.mob_default_chroma,
                    "bounds": self.default_color_bounds(),
                },
            ],
            "extraEntries": [{"label": "Edit Filters", "bounds": self.mob_filters_extra_bounds()}],
            "mobFilterEntries": mob_entries,
        }

    def stalk_popup(self) -> dict[str, Any]:
        return {
            "displayName": "Stalk Player",
            "expandedExtra": "STALK_TARGET" if self.expanded_stalk_target else None,
            "actionInput": self.active_action_input,
            "actionInputBuffer": self.action_input_buffer if self.active_action_input is not None else None,
            "entries": [
                {
                    "settingName": "Tracer Color",
                    "value": "#FFFFFFFF",
                    "chroma": self.stalk_chroma,
                    "bounds": self.stalk_color_bounds(),
                }
            ],
            "extraEntries": [{"label": f"Target: {self.stalk_target or '<none>'}", "bounds": self.stalk_extra_bounds()}],
            "playerEntries": [
                {"target": None, "submit": False, "bounds": self.stalk_input_bounds()},
                {"target": None, "submit": True, "bounds": self.stalk_submit_bounds()},
            ] if self.expanded_stalk_target else [],
        }

    def neck_hider_popup(self) -> dict[str, Any]:
        name_entries = []
        if self.expanded_name_mappings:
            name_entries.extend([
                {"realName": "", "kind": "ADD_MANUAL_ORIGINAL", "bounds": self.name_mapping_original_bounds()},
                {"realName": "", "kind": "ADD_MANUAL_FAKE", "bounds": self.name_mapping_fake_bounds()},
                {"realName": "", "kind": "ADD_MANUAL_SAVE", "bounds": self.name_mapping_save_bounds()},
            ])
            if "FloydVerifierReal" in self.nick_name_mappings:
                name_entries.append({"realName": "FloydVerifierReal", "kind": "REMOVE", "bounds": self.name_mapping_remove_bounds()})
        return {
            "displayName": "Neck Hider",
            "expandedExtra": "NAME_MAPPINGS" if self.expanded_name_mappings else None,
            "mappingInput": {
                "original": self.mapping_original_buffer,
                "fake": self.mapping_fake_buffer,
                "focused": self.mapping_focused_field,
            },
            "entries": [
                {
                    "settingName": "Default Nick",
                    "value": "George Floyd",
                    "bounds": {"left": 562, "top": 132 if self.expanded_name_mappings else 94, "right": 742, "bottom": 150 if self.expanded_name_mappings else 112},
                }
            ],
            "extraEntries": [
                {"label": "Edit Names", "bounds": self.name_mappings_extra_bounds()},
                {"label": "Reload Names", "bounds": {"left": 562, "top": 132, "right": 812, "bottom": 150}},
            ],
            "nameMappingEntries": name_entries,
        }

    def inventory_hud_popup(self) -> dict[str, Any]:
        return {
            "displayName": "Inventory HUD",
            "entries": [],
            "extraEntries": [{"label": "Edit Layout", "bounds": self.inventory_hud_layout_bounds()}],
        }

    def custom_scoreboard_popup(self) -> dict[str, Any]:
        return {
            "displayName": "Custom Scoreboard",
            "entries": [],
            "extraEntries": [{"label": "Edit Layout", "bounds": self.inventory_hud_layout_bounds()}],
        }

    def instance_popup(self) -> dict[str, Any]:
        return {
            "displayName": "Instance Name",
            "entries": [
                {
                    "settingName": "Instance Title",
                    "value": self.instance_title,
                    "bounds": self.instance_title_bounds(),
                }
            ],
            "extraEntries": [],
        }

    def freecam_popup(self) -> dict[str, Any]:
        return {
            "displayName": "Freecam",
            "entries": [
                {"settingName": "Speed", "value": self.camera_speed, "bounds": self.freecam_speed_bounds()},
            ],
            "extraEntries": [],
        }

    def freelook_popup(self) -> dict[str, Any]:
        return {
            "displayName": "Freelook",
            "entries": [
                {"settingName": "Distance", "value": self.freelook_distance, "bounds": self.freelook_distance_bounds()},
            ],
            "extraEntries": [],
        }

    def f5_popup(self) -> dict[str, Any]:
        return {
            "displayName": "F5 Customizer",
            "entries": [
                {"settingName": "Disable Front Cam", "value": self.f5_disable_front, "bounds": self.f5_disable_front_bounds()},
                {"settingName": "Disable Back Cam", "value": self.f5_disable_back, "bounds": self.f5_disable_back_bounds()},
                {"settingName": "No Third-Person Clipping", "value": self.f5_no_clip, "bounds": self.f5_no_clip_bounds()},
                {"settingName": "Scrolling Changes Distance", "value": self.f5_scroll_enabled, "bounds": self.f5_scroll_bounds()},
                {"settingName": "Reset F5 Scrolling", "value": self.f5_reset_on_toggle, "bounds": self.f5_reset_bounds()},
                {"settingName": "Camera Distance", "value": self.f5_distance, "bounds": self.f5_distance_bounds()},
            ],
            "extraEntries": [],
        }

    def no_armor_popup(self) -> dict[str, Any]:
        return {
            "displayName": "No Armor",
            "entries": [
                {
                    "settingName": "Target",
                    "value": self.no_armor_target,
                    "options": self.no_armor_options(),
                    "bounds": self.no_armor_target_bounds(),
                }
            ],
            "extraEntries": [],
        }

    def player_size_popup(self) -> dict[str, Any]:
        return {
            "displayName": "Player Size",
            "entries": [
                {
                    "settingName": "Target",
                    "value": self.player_size_target,
                    "options": self.player_size_options(),
                    "bounds": self.player_size_target_bounds(),
                },
                {
                    "settingName": "X",
                    "value": self.player_scale_x,
                    "bounds": self.player_size_x_bounds(),
                },
                {
                    "settingName": "Y",
                    "value": self.player_scale_y,
                    "bounds": self.player_size_y_bounds(),
                },
                {
                    "settingName": "Z",
                    "value": self.player_scale_z,
                    "bounds": self.player_size_z_bounds(),
                },
            ],
            "extraEntries": [],
        }

    def cape_popup(self) -> dict[str, Any]:
        return {
            "displayName": "Cape",
            "entries": [
                {
                    "settingName": "Image",
                    "value": self.cape_selected,
                    "options": list(self.cape_available),
                    "bounds": self.cape_image_bounds(),
                },
                {
                    "settingName": "Open Cape Folder",
                    "value": None,
                    "bounds": self.cape_open_folder_bounds(),
                },
            ],
            "extraEntries": [],
        }

    def cone_hat_popup(self) -> dict[str, Any]:
        return {
            "displayName": "Cone Hat",
            "entries": [
                {"settingName": "Height", "value": self.cone_height, "bounds": self.cone_number_bounds(0)},
                {"settingName": "Radius", "value": self.cone_radius, "bounds": self.cone_number_bounds(1)},
                {"settingName": "Y Offset", "value": self.cone_y_offset, "bounds": self.cone_number_bounds(2)},
                {"settingName": "Rotation", "value": self.cone_rotation, "bounds": self.cone_number_bounds(3)},
                {"settingName": "Spin Speed", "value": self.cone_spin_speed, "bounds": self.cone_number_bounds(4)},
                {
                    "settingName": "Image",
                    "value": self.cone_selected,
                    "options": list(self.cone_available),
                    "bounds": self.cone_image_bounds(),
                },
                {
                    "settingName": "Open Cone Folder",
                    "value": None,
                    "bounds": self.cone_open_folder_bounds(),
                },
            ],
            "extraEntries": [],
        }

    def custom_skin_popup(self) -> dict[str, Any]:
        return {
            "displayName": "Custom Skin",
            "entries": [
                {
                    "settingName": "Self",
                    "value": self.skin_self,
                    "bounds": self.skin_self_bounds(),
                },
                {
                    "settingName": "Others",
                    "value": self.skin_others,
                    "bounds": self.skin_others_bounds(),
                },
                {
                    "settingName": "Skin",
                    "value": self.skin_selected,
                    "options": list(self.skin_available),
                    "bounds": self.skin_image_bounds(),
                },
                {
                    "settingName": "Open Skin Folder",
                    "value": None,
                    "bounds": self.skin_open_folder_bounds(),
                },
            ],
            "extraEntries": [],
        }


def inside(x: float, y: float, rect: dict[str, int]) -> bool:
    return rect["left"] <= x <= rect["right"] and rect["top"] <= y <= rect["bottom"]


if __name__ == "__main__":
    unittest.main()
