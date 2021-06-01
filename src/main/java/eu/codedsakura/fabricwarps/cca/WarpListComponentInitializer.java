package eu.codedsakura.fabricwarps.cca;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistryV3;
import dev.onyxstudios.cca.api.v3.component.ComponentV3;
import dev.onyxstudios.cca.api.v3.world.WorldComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.world.WorldComponentInitializer;
import eu.codedsakura.fabricwarps.FabricWarps;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class WarpListComponentInitializer implements WorldComponentInitializer {
    public static final ComponentKey<IWarpListComponent> WARP_LIST =
        ComponentRegistryV3.INSTANCE.getOrCreate(new Identifier("fabricwarps", "warplist"), IWarpListComponent.class);


    @Override
    public void registerWorldComponentFactories(WorldComponentFactoryRegistry registry) {
        registry.register(WARP_LIST, WarpListComponent.class, world -> new WarpListComponent());
    }

    public interface IWarpListComponent extends ComponentV3 {
        List<FabricWarps.Warp> getWarps();
        boolean addWarp(FabricWarps.Warp warp);
        boolean removeWarp(String name);
    }


    public static class WarpListComponent implements IWarpListComponent {
        private final List<FabricWarps.Warp> warps = new ArrayList<>();

        @Override
        public void readFromNbt(NbtCompound tag) {
            warps.clear();
            NbtList warpsTag = tag.getList("warps", NbtType.COMPOUND);
            for (NbtElement _warpTag : warpsTag) {
                NbtCompound warpTag = (NbtCompound) _warpTag;
                warps.add(new FabricWarps.Warp(
                        warpTag.getDouble("x"),
                        warpTag.getDouble("y"),
                        warpTag.getDouble("z"),
                        warpTag.getFloat("yaw"),
                        warpTag.getFloat("pitch"),
                        warpTag.getString("name"),
                        warpTag.getUuid("owner"),
                        warpTag.getUuid("id")
                ));
            }
        }

        @Override
        public void writeToNbt(NbtCompound tag) {
            NbtList warpsTag = new NbtList();
            for (FabricWarps.Warp warp : warps) {
                NbtCompound warpTag = new NbtCompound();
                warpTag.putUuid("id", warp.id);
                warpTag.putString("name", warp.name);
                warpTag.putUuid("owner", warp.owner);
                warpTag.putDouble("x", warp.x);
                warpTag.putDouble("y", warp.y);
                warpTag.putDouble("z", warp.z);
                warpTag.putFloat("yaw", warp.yaw);
                warpTag.putFloat("pitch", warp.pitch);
                warpsTag.add(warpTag);
            }
            tag.put("warps", warpsTag);
        }

        @Override
        public boolean addWarp(FabricWarps.Warp warp) {
            if (warps.stream().anyMatch(warp1 -> warp1.name.equalsIgnoreCase(warp.name))) return false;
            return warps.add(warp);
        }

        @Override
        public boolean removeWarp(String name) {
            if (warps.stream().noneMatch(warp -> warp.name.equalsIgnoreCase(name))) return false;
            return warps.removeIf(warp -> warp.name.equalsIgnoreCase(name));
        }

        @Override
        public List<FabricWarps.Warp> getWarps() {
            return warps;
        }
    }
}
