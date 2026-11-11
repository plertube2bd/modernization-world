package com.example.jail;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/** Who is in jail, kept with the world (in the overworld's data), and which version of the jail has been built. */
public final class JailData extends SavedData {
    private static final Codec<JailData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Prisoner.CODEC).fieldOf("prisoners").forGetter(JailData::byString),
            Codec.INT.fieldOf("built").forGetter(d -> d.built),
            Codec.STRING.listOf().optionalFieldOf("rooms", List.of()).forGetter(d -> List.copyOf(d.rooms))
    ).apply(i, JailData::new));
    public static final SavedDataType<JailData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(JailMod.ID, "prisoners"), JailData::new, CODEC, null);

    private final Map<UUID, Prisoner> prisoners = new HashMap<>();
    private int built;
    /** Rooms built, as "key@version". */
    private final Set<String> rooms = new HashSet<>();

    public JailData() {
    }

    private JailData(Map<String, Prisoner> saved, int built, List<String> rooms) {
        saved.forEach((id, prisoner) -> this.prisoners.put(UUID.fromString(id), prisoner));
        this.built = built;
        this.rooms.addAll(rooms);
    }

    public boolean roomBuilt(String key) {
        return this.rooms.contains(key);
    }

    public void setRoomBuilt(String key) {
        this.rooms.add(key);
        setDirty();
    }

    private Map<String, Prisoner> byString() {
        Map<String, Prisoner> out = new HashMap<>();
        this.prisoners.forEach((id, prisoner) -> out.put(id.toString(), prisoner));
        return out;
    }

    public static JailData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable Prisoner get(UUID id) {
        return this.prisoners.get(id);
    }

    public Collection<Prisoner> all() {
        return this.prisoners.values();
    }

    public Collection<Map.Entry<UUID, Prisoner>> entries() {
        return this.prisoners.entrySet();
    }

    public void put(UUID id, Prisoner prisoner) {
        this.prisoners.put(id, prisoner);
        setDirty();
    }

    public @Nullable Prisoner remove(UUID id) {
        Prisoner removed = this.prisoners.remove(id);
        setDirty();
        return removed;
    }

    public int built() {
        return this.built;
    }

    public void setBuilt(int version) {
        this.built = version;
        setDirty();
    }
}
