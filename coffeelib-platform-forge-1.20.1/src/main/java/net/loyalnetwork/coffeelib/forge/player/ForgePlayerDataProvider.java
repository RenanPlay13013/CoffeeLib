package net.loyalnetwork.coffeelib.forge.player;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

/** Capability storage tied to the lifetime of one player entity. */
public final class ForgePlayerDataProvider implements ICapabilitySerializable<CompoundTag>, ForgeDataContainer {
    private CompoundTag data = new CompoundTag();
    private final LazyOptional<ForgeDataContainer> optional = LazyOptional.of(() -> this);

    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return ForgePlayerDataFactory.CAPABILITY.orEmpty(cap, optional);
    }

    @Override public CompoundTag serializeNBT() { return serialize(); }
    @Override public void deserializeNBT(CompoundTag nbt) { deserialize(nbt); }
    @Override public CompoundTag serialize() { return data.copy(); }
    @Override public void deserialize(CompoundTag tag) { data = tag.copy(); }
    @Override public String get(String identifier) {
        if (!data.contains(identifier)) return null;
        if (!data.contains(identifier, Tag.TAG_STRING))
            throw new IllegalStateException("Invalid stored player-data type for " + identifier);
        return data.getString(identifier);
    }
    @Override public void put(String identifier, String value) {
        if (value == null) data.remove(identifier);
        else data.putString(identifier, value);
    }
    public void invalidate() { optional.invalidate(); }
}
