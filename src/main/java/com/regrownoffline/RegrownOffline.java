package com.regrownoffline;

import com.mojang.logging.LogUtils;
import com.regrownoffline.command.DecayDebugCommand;
import com.regrownoffline.config.RegrownOfflineConfig;
import com.regrownoffline.decay.DecayChainReloadListener;
import com.regrownoffline.decay.DecayScheduler;
import com.regrownoffline.event.PlacementListener;
import com.regrownoffline.event.PlayerActivityListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(RegrownOffline.MODID)
public final class RegrownOffline {

    public static final String MODID = "regrownoffline";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RegrownOffline(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, RegrownOfflineConfig.SPEC);

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.addListener(PlacementListener::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(PlacementListener::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(PlacementListener::onEntityPlace);
        NeoForge.EVENT_BUS.addListener(PlayerActivityListener::onLevelTick);
        NeoForge.EVENT_BUS.addListener(DecayScheduler::onLevelTick);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("RegrownOffline loaded - decay starts after {} offline day(s), {} block(s)/tick",
                RegrownOfflineConfig.OFFLINE_DAYS_BEFORE_DECAY.get(),
                RegrownOfflineConfig.DECAY_BLOCKS_PER_TICK.get());
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new DecayChainReloadListener());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        DecayDebugCommand.register(event.getDispatcher());
    }
}
