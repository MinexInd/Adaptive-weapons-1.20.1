package net.minex.adaptiveweapons;

import net.fabricmc.api.ModInitializer;

public class AdaptiveWeapons implements ModInitializer {
    public static final String MOD_ID = "combomod";

    @Override
    public void onInitialize() {
        ModRegistries.registerEvents();
    }
}
