// SOURCE: https://github.com/Noamm9/NoammAddons/blob/master/src/main/kotlin/noammaddons/features/FeatureManager.kt
// Complete list of all 100+ registered features in NoammAddons
// Sorted alphabetically after registration

package noammaddons.features

object FeatureManager {
    val features = mutableSetOf(
        // === ESP ===
        EspSettings, PartyESP, TeammatesESP, StarMobESP, WitherESP, HiddenMobs, PestESP, ChamNametags,

        // === GENERAL ===
        CustomMenuSettings, MotionBlur, GyroHelper, ChatEmojis, Etherwarp, SlotBinding,
        SBKick, CakeNumbers, EnderPearlFix, ItemEntity, Gloomlock, VisualWords, Chat,
        ItemRarity, ItemsPrice, AutoClicker, EnchantmentsColors, PartyHelper,
        CustomSlotHighlight,

        // === DUNGEONS ===
        AbilityKeybinds, IHateDiorite, MimicDetector, DoorKeys, AutoUlt, PartyFinder,
        TickTimers, LeapMenu, AutoI4, ArchitectDraft, AutoGFS, AutoPotion, CryptsDone,
        M7Relics, MaxorsCrystals, ChestProfit, FpsBoost, AutoRequeue, WitherDragons,
        BloodRoom, F7Titles, Floor4BossFight, Secrets, TerminalNumbers, MelodyAlert,
        ScoreCalculator, DungeonBreaker, DungeonMap, DungeonWaypoints,

        // === SOLVERS ===
        PuzzleSolvers, LividSolver, SimonSaysSolver, ArrowAlignSolver, TerminalSolver,

        // === ALERTS ===
        DungeonPlayerDeath, RoomAlerts, RNGSound, Ragnarock, ShadowAssassin,

        // === HUD ===
        PlayerHud, FpsDisplay, ClockDisplay, TpsDisplay, PetDisplay, MaskTimers,
        SpringBootsDisplay, WitherShieldTimer, CustomScoreboard, CustomTabList,
        InventoryDisplay, BlessingDisplay, RunSplits, FreezeDisplay, LifelineHud,
        DarkMode, WarpCooldown,

        // === MISC ===
        Camera, PlayerModel, DamageSplash, FullBlock, SmoothBossBar, TimeChanger,
        BlockOverlay, Animations, BowHitSound, ArrowFix, NoBlockAnimation,
        ClientTimer, StopCloseMyChat,

        // === GUI ===
        InventorySearchbar, ScalableTooltips, WardrobeMenu, CustomPetMenu, SalvageOverlay,

        // === SLAYERS ===
        ExtraSlayerInfo, SlayerFeatures,

        // === DEV ===
        DevOptions, CompTest, ConfigGui, RatProtection,

        // === DISABLED/DEPRECATED ===
        // ZeroPingTeleportation,  // R.I.P
        // BetterFloors, StonkSwap, GhostPick,  // R.I.P stonking
        // ReaperArmor,  // R.I.P
        // NoRotate,  // R.I.P Prediction AC
    ).sortedBy { it.name }

    fun registerFeatures() { features.forEach(Feature::_init); Config.load() }
    fun getFeatureByName(name: String) = features.find { it.name == name }
}
