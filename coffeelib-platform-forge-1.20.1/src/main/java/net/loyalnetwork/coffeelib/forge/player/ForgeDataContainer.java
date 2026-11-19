package net.loyalnetwork.coffeelib.forge.player;

import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import net.minecraft.nbt.CompoundTag;

/** One namespaced native capability per player, shared by CoffeeLib consumers. */
@AutoRegisterCapability
public interface ForgeDataContainer {
    String get(String identifier);
    void put(String identifier, String value);
    CompoundTag serialize();
    void deserialize(CompoundTag tag);
}
