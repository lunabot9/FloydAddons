"""Exercise the real item browser in an already-running offline development client."""
import argparse
import json
import math
import pathlib
import time
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=pathlib.Path, required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--target", required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    base = f"http://127.0.0.1:{args.port}"

    def request(path, data=None):
        req = urllib.request.Request(base + path, data=None if data is None else json.dumps(data).encode(),
                                     headers={"Authorization": "Bearer " + config["token"], "Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=20) as response:
            return json.load(response)

    health = request("/health")
    assert pathlib.Path(health["settings"]).resolve() == args.config.resolve(), health["settings"]
    initial = request("/state")
    expected_version = next(line.split("=", 1)[1] for line in (pathlib.Path(__file__).resolve().parents[1] / "gradle.properties").read_text().splitlines() if line.startswith("mod_version="))
    assert initial["scaffold"]["version"] == expected_version
    assert initial["scaffold"]["minecraftVersion"] == args.target
    assert initial["connected"] and initial["server"]["singleplayer"], "Run only in the offline test world"
    assert any(c["name"] == "Render" and any(m["name"] == "Item Search" for m in c["modules"]) for c in initial["modules"]["categories"])
    screenshots = []
    run_stamp = time.strftime("%Y%m%d-%H%M%S")

    def shot(name):
        time.sleep(.35)
        screenshots.append(request("/screenshot", {"fileName": "item-search-" + run_stamp + "-" + name + ".png"})["path"])

    def mouse(x, y, event="click", **more):
        return request("/mouse", dict(event=event, x=x, y=y, coordinateSpace="gui", **more))

    def panel():
        return request("/state")["itemSearch"]["panel"]

    def query(text, label):
        p = panel()
        mouse(p["searchX"] + 6, p["searchY"] + 6, button=1)
        request("/type", {"text": text})
        shot(label)
        current = panel()
        assert current["query"] == text
        return current

    request("/chat", {"message": "/fa items"})
    shot("open")
    all_items = query("", "all")
    assert all_items["matches"] > 8000 and all_items["pages"] > 1
    mouse(all_items["x"] + 15, all_items["y"] + 70, event="scroll", amount=-1)
    shot("page-two")
    assert panel()["page"] == 1
    hyperion = query("hyperion", "hyperion")
    assert hyperion["matches"] == 1 and hyperion["tiles"][0]["id"] == "HYPERION"
    tile = hyperion["tiles"][0]
    mouse(tile["x"] + 8, tile["y"] + 8, event="move")
    shot("tooltip")
    mouse(tile["x"] + 8, tile["y"] + 8)
    shot("detail")
    assert request("/state")["itemSearch"]["detailItem"] == "HYPERION"
    detail = request("/state")["itemSearch"]["detail"]
    assert detail["mode"] == "crafting" and detail["recipeOnly"]
    assert detail["inventoryStyle"] and detail["inventorySlots"] == 36 and not detail["headerVisible"]
    cell = detail["recipeCell"]
    gx = detail["recipeGridX"]
    gy = detail["recipeGridY"]
    mouse(gx + cell + 8, gy + cell + 8)
    shot("ingredient")
    assert request("/state")["itemSearch"]["detailItem"] == "NECRON_BLADE"
    kat_views = []
    for pet, input_pet, coins, seconds, materials in [
        ("ARMADILLO;1", "ARMADILLO;0", "10000", "10800", []),
        ("SHEEP;4", "SHEEP;3", "50000", "1800", ["ENCHANTED_MUTTON", "ENCHANTED_WOOL"]),
        ("SCATHA;3", "SCATHA;2", "125000000", "604800", ["ENCHANTED_HARD_STONE"]),
    ]:
        request("/chat", {"message": "/fa items"})
        shot("kat-open")
        pet_panel = query("@" + pet, "kat-search")
        pet_tile = next(t for t in pet_panel["tiles"] if t["id"] == pet)
        mouse(pet_tile["x"] + 8, pet_tile["y"] + 8)
        shot("kat-" + pet.split(";")[0].lower())
        kat = request("/state")["itemSearch"]["detail"]
        layout = kat["katLayout"]
        assert kat["mode"] == "katgrade" and layout["style"] == "circular" and not layout["craftingGrid"]
        assert layout["input"] == input_pet and layout["output"] == pet
        assert layout["coins"] == coins and layout["seconds"] == seconds
        assert all(any(i["item"] == material for i in layout["ingredients"]) for material in materials)
        assert layout["petPreviewMinLevel"] == 1 and layout["petPreviewMaxLevel"] == 100 and "[Lvl 1-Lvl 100]" in layout["petName"]
        assert not any("{" in line for line in layout["petLore"])
        if pet == "SCATHA;3":
            assert layout["coinLabel"] == "125m" and layout["coinHover"] == "125,000,000"
            assert any("+1.25-125" in line for line in layout["petLore"])
            assert any("+1-100" in line for line in layout["petLore"])
            angle = 2 * math.pi * (len(layout["ingredients"]) - 1) / len(layout["ingredients"])
            mouse(layout["centerX"] + int(math.cos(angle) * 27), layout["centerY"] + int(math.sin(angle) * 27), event="move")
            shot("kat-scatha-coin-hover")
        mouse(layout["centerX"], layout["centerY"], event="move")
        shot("kat-hover-" + pet.split(";")[0].lower())
        kat_views.append(kat)
    purchase_views = []
    for pet, coins in [("ELEPHANT;0", "10000"), ("ELEPHANT;4", "15000000")]:
        request("/chat", {"message": "/fa items"})
        shot("purchase-open")
        purchase_panel = query("@" + pet, "purchase-search")
        pet_tile = next(t for t in purchase_panel["tiles"] if t["id"] == pet)
        mouse(pet_tile["x"] + 8, pet_tile["y"] + 8)
        shot("purchase-entry")
        detail = request("/state")["itemSearch"]["detail"]
        for _ in range(detail["pages"]):
            if detail["mode"] == "npc_shop":
                break
            navigation=detail["sourceNavigation"]
            assert navigation["position"] == "top_right"
            mouse(navigation["nextX"] + 5, navigation["y"] + 5)
            shot("purchase-next-source")
            detail = request("/state")["itemSearch"]["detail"]
        layout = detail["purchaseLayout"]
        assert detail["mode"] == "npc_shop" and layout["seller"] == "ORINGO_NPC"
        assert layout["result"] == pet and not layout["craftingGrid"]
        assert len(layout["costs"]) == 2 and layout["costs"][0]["x"] == layout["costs"][1]["x"]
        assert layout["costs"][1]["y"] - layout["costs"][0]["y"] == 24
        assert any(c["item"] == "SKYBLOCK_COIN" and c["count"] == coins for c in layout["costs"])
        if pet == "ELEPHANT;4":
            navigation=detail["sourceNavigation"]
            assert navigation["caption"] == f"{detail['page']+1}/{detail['pages']}"
            assert navigation["y"] < detail["y"] + 40 and navigation["nextX"] > detail["x"] + detail["width"]/2
            mouse(navigation["previousX"] + 5,navigation["y"] + 5)
            shot("purchase-previous-source")
            assert request("/state")["itemSearch"]["detail"]["mode"] == "katgrade"
            mouse(navigation["nextX"] + 5,navigation["y"] + 5)
            shot("purchase-restored-source")
            assert request("/state")["itemSearch"]["detail"]["mode"] == "npc_shop"
        mouse(layout["resultX"] + 8, layout["resultY"] + 8, event="move")
        shot("purchase-elephant-" + pet.split(";")[1])
        purchase_views.append(detail)
    request("/chat", {"message": "/fa items"})
    shot("unknown-open")
    unknown_panel = query("@6_ANNIVERSARY_BARN_SKIN", "unknown-search")
    unknown_tile = next(t for t in unknown_panel["tiles"] if t["id"] == "6_ANNIVERSARY_BARN_SKIN")
    assert not unknown_tile["canOpen"]
    mouse(unknown_tile["x"] + 8, unknown_tile["y"] + 8)
    shot("unknown-click-ignored")
    assert panel()["query"] == "@6_ANNIVERSARY_BARN_SKIN"
    mouse(unknown_tile["x"] + 8, unknown_tile["y"] + 8,event="move")
    shot("unknown-tooltip")
    assert panel()["hoveredItem"] == "6_ANNIVERSARY_BARN_SKIN" and panel()["nativeItemTooltips"]
    request("/chat", {"message": "/fa items"})
    shot("reopen")
    assert query("#ferocity !hyperion", "lore")["matches"] > 0
    assert query("@HYPERION", "id")["matches"] == 1
    assert query("unlikely_missing_item_qzx", "empty")["matches"] == 0
    query("hyperion", "restore")
    request("/screen", {"screen": "close"})
    request("/action", {"action": "inputKey", "keyCode": 69})
    shot("inventory")
    assert request("/state")["itemSearch"]["inventoryPanelVisible"]
    p = query("enchanted", "inventory-typing")
    assert request("/state")["itemSearch"]["inventoryPanelVisible"] and p["matches"] > 0
    # E is the inventory close key. It must be consumed while the search box is focused.
    request("/action", {"action": "inputKey", "keyCode": 69, "event": "press"})
    shot("inventory-key")
    assert request("/state")["itemSearch"]["inventoryPanelVisible"]
    request("/action", {"action": "setModuleEnabled", "module": "item search", "enabled": False})
    shot("disabled")
    assert not request("/state")["itemSearch"]["inventoryPanelVisible"]
    request("/action", {"action": "setModuleEnabled", "module": "item search", "enabled": True})
    for _ in range(100):
        if request("/state")["itemSearch"]["inventoryPanelVisible"]:
            break
        time.sleep(.1)
    shot("reenabled")
    assert request("/state")["itemSearch"]["inventoryPanelVisible"]
    query("hyperion", "inventory-final")
    p = panel()
    tile = p["tiles"][0]
    mouse(tile["x"] + 8, tile["y"] + 8, event="move")
    shot("sidebar-tooltip")
    p = panel()
    assert p["hoveredItem"] == "HYPERION" and p["tooltipLoreLines"] > 0
    assert p["rightInset"] == 8 and p["y"] == 8 and p["cornerRadius"] == 8
    assert not p["headerVisible"] and p["backgroundColor"] == "#80000000"
    p = query("@LAPIS_ARMOR_HELMET", "mob-search")
    tile = p["tiles"][0]
    mouse(tile["x"] + 8, tile["y"] + 8)
    shot("mob-drops")
    detail = request("/state")["itemSearch"]["detail"]
    assert detail["mode"] == "drops" and detail["mobPreview"] == "minecraft:zombie"
    assert detail["inventoryStyle"] and detail["inventorySlots"] == 36
    assert detail["mobDyeColors"]["CHEST"] == 255
    assert detail["dropChancesBelowIcons"] and detail["dropChancesOnHover"]
    assert detail["dropLabelFormat"] == "fraction" and detail["dropHoverFormat"] == "percentage_and_fraction"
    assert any(d["item"].split(":")[0] == "LAPIS_ARMOR_HELMET" and d["chance"] == "1%" for d in detail["drops"])
    assert any(d["item"].split(":")[0] == "LAPIS_ARMOR_HELMET" and d["percentage"] == "1%" and d["fraction"] == "1/100" and "1/100" in d["tooltip"] for d in detail["drops"])
    assert any(d["item"].split(":")[0] == "ROTTEN_FLESH" for d in detail["drops"])
    final = request("/state")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(dict(scaffold=final["scaffold"], server=final["server"], itemSearch=final["itemSearch"], katViews=kat_views, purchaseViews=purchase_views, screenshots=screenshots), indent=2), encoding="utf-8")
    print(f"PASS: Render registration, {all_items['matches']} items, search, paging, tooltip, recipe navigation, inventory typing, toggle; {len(screenshots)} screenshots")


if __name__ == "__main__":
    main()
