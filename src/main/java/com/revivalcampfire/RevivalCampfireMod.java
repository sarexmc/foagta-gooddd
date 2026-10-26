package com.revivalcampfire;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Date;

@Mod(RevivalCampfireMod.MODID)
public class RevivalCampfireMod {
    public static final String MODID = "revivalcampfire";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> CAMPFIRE = BLOCKS.register("resurrection_campfire",
            () -> new ResurrectionCampfireBlock(BlockBehaviour.Properties.copy(Blocks.SOUL_CAMPFIRE)
                    .lightLevel(s -> 15).noOcclusion()));

    public static final RegistryObject<Item> CAMPFIRE_ITEM = ITEMS.register("resurrection_campfire",
            () -> new ResurrectionCampfireItem(CAMPFIRE.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<ResurrectionCampfireBlockEntity>> CAMPFIRE_BE = BLOCK_ENTITIES.register("resurrection_campfire",
            () -> BlockEntityType.Builder.of(ResurrectionCampfireBlockEntity::new, CAMPFIRE.get()).build(null));

    public static final RegistryObject<Item> SPOON = ITEMS.register("resurrection_spoon",
            () -> new SpoonItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<SoundEvent> RITUAL_MAIN = SOUNDS.register("ritual_main",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "ritual_main")));
    public static final RegistryObject<SoundEvent> REVIVED = SOUNDS.register("revived",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "revived")));
    public static final RegistryObject<SoundEvent> SPOON_USE = SOUNDS.register("spoon_use",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "spoon_use")));

    public static final RegistryObject<MenuType<ReviveMenu>> REVIVE_MENU = MENUS.register("revive_menu",
            () -> IForgeMenuType.create(ReviveMenu::new));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID))
                    .icon(() -> new ItemStack(CAMPFIRE_ITEM.get()))
                    .displayItems((params, out) -> {
                        out.accept(CAMPFIRE_ITEM.get());
                        out.accept(SPOON.get());
                    })
                    .build());

    public RevivalCampfireMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
        SOUNDS.register(bus);
        TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        bus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEvents.init(bus);
        }
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(Network::init);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent e) {
        ReviveCommand.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) RitualManager.tick();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) ReviveData.get(sp.server).teleportIfPending(sp);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        RitualManager.shutdown();
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (!Config.BAN_ON_DEATH.get()) return;
        if (e.getEntity() instanceof ServerPlayer p) {
            MinecraftServer server = p.server;
            GameProfile profile = p.getGameProfile();
            String reason = Config.BAN_REASON.get();
            server.getPlayerList().getBans().add(
                    new UserBanListEntry(profile, new Date(), "RevivalCampfire", null, reason));
            server.execute(() -> p.connection.disconnect(Component.literal(reason)));
        }
    }
}
