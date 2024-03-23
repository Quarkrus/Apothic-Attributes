package dev.shadowsoffire.apothic_attributes;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import dev.shadowsoffire.apothic_attributes.client.AttributesLibClient;
import dev.shadowsoffire.apothic_attributes.compat.CuriosCompat;
import dev.shadowsoffire.apothic_attributes.impl.AttributeEvents;
import dev.shadowsoffire.apothic_attributes.packet.CritParticleMessage;
import dev.shadowsoffire.apothic_attributes.util.MiscDatagen;
import dev.shadowsoffire.placebo.network.PayloadHelper;
import dev.shadowsoffire.placebo.registry.DeferredHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput.Target;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

@Mod(ApothicAttributes.MODID)
public class ApothicAttributes {

    public static final String MODID = "apothic_attributes";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static final DeferredHelper R = DeferredHelper.create(MODID);

    /**
     * Static record of {@link Player#getAttackStrengthScale(float)} for use in damage events.<br>
     * Recorded in the {@link PlayerAttackEvent} and valid for the entire chain, when a player attacks.
     */
    private static float localAtkStrength = 1;

    public ApothicAttributes(IEventBus bus) {
        bus.register(this);
        NeoForge.EVENT_BUS.register(new AttributeEvents());
        NeoForge.EVENT_BUS.addListener(ApothicAttributes::trackCooldown);
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(new AttributesLibClient());
            bus.register(AttributesLibClient.ModBusSub.class);
        }

        PayloadHelper.registerPayload(new CritParticleMessage.Provider());
        ALObjects.bootstrap(bus);
        ALConfig.load();
    }

    @SubscribeEvent
    public void init(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            MobEffects.BLINDNESS.addAttributeModifier(Attributes.FOLLOW_RANGE, "f8c3de3d-1fea-4d7c-a8b0-22f63c4c3454", -0.75, Operation.MULTIPLY_TOTAL);
            // TODO: Update to show in GUI without applying attribute to entity
            // if (MobEffects.SLOW_FALLING.getAttributeModifiers().isEmpty()) {
            // MobEffects.SLOW_FALLING.addAttributeModifier(ForgeMod.ENTITY_GRAVITY.get(), "A5B6CF2A-2F7C-31EF-9022-7C3E7D5E6ABA", -0.07, Operation.ADDITION);
            // }
            BuiltInRegistries.ATTRIBUTE.addAlias(loc("creative_flight"), new ResourceLocation("neoforge", "creative_flight"));
        });
    }

    // TODO - Update impls to reflect new default values.
    @SubscribeEvent
    public void applyAttribs(EntityAttributeModificationEvent e) {
        e.getTypes().forEach(type -> {
            addAll(type, e::add,
                ALObjects.Attributes.DRAW_SPEED,
                ALObjects.Attributes.CRIT_CHANCE,
                ALObjects.Attributes.CRIT_DAMAGE,
                ALObjects.Attributes.COLD_DAMAGE,
                ALObjects.Attributes.FIRE_DAMAGE,
                ALObjects.Attributes.LIFE_STEAL,
                ALObjects.Attributes.CURRENT_HP_DAMAGE,
                ALObjects.Attributes.OVERHEAL,
                ALObjects.Attributes.GHOST_HEALTH,
                ALObjects.Attributes.MINING_SPEED,
                ALObjects.Attributes.ARROW_DAMAGE,
                ALObjects.Attributes.ARROW_VELOCITY,
                ALObjects.Attributes.EXPERIENCE_GAINED,
                ALObjects.Attributes.HEALING_RECEIVED,
                ALObjects.Attributes.ARMOR_PIERCE,
                ALObjects.Attributes.ARMOR_SHRED,
                ALObjects.Attributes.PROT_PIERCE,
                ALObjects.Attributes.PROT_SHRED,
                ALObjects.Attributes.DODGE_CHANCE,
                ALObjects.Attributes.ELYTRA_FLIGHT);
        });
        // Change the base value of Step Height to reflect the real base value of a Player.
        // The alternative is a bunch of special casing in the display.
        // This is course-corrected in IForgeEntityMixin.
        e.add(EntityType.PLAYER, NeoForgeMod.STEP_HEIGHT.value(), 0.6);
    }

    @SafeVarargs
    private static void addAll(EntityType<? extends LivingEntity> type, BiConsumer<EntityType<? extends LivingEntity>, Attribute> add, Supplier<? extends Attribute>... attribs) {
        for (Supplier<? extends Attribute> a : attribs)
            add.accept(type, a.get());
    }

    @SubscribeEvent
    public void setup(FMLCommonSetupEvent e) {
        AttributeSupplier playerAttribs = DefaultAttributes.getSupplier(EntityType.PLAYER);
        for (Attribute attr : BuiltInRegistries.ATTRIBUTE) {
            if (playerAttribs.hasAttribute(attr)) attr.setSyncable(true);
        }
        if (ModList.get().isLoaded("curios")) {
            e.enqueueWork(CuriosCompat::init);
        }
    }

    @SubscribeEvent
    public void data(GatherDataEvent e) {
        MiscDatagen gen = new MiscDatagen(e.getGenerator().getPackOutput().getOutputFolder(Target.DATA_PACK).resolve(MODID));
        e.getGenerator().addProvider(true, gen);
    }

    /**
     * Gets the local attack strength of an entity.
     * <p>
     * For players, this is recorded in {@link AttackEntityEvent} and is valid for other damage events.
     * <p>
     * For non-players, this value is always 1.
     */
    public static float getLocalAtkStrength(Entity entity) {
        if (entity instanceof Player) return localAtkStrength;
        return 1;
    }

    /**
     * Gets the current tooltip flag.
     * 
     * @return If called on the client, the current tooltip flag, otherwise {@link TooltipFlag#NORMAL}
     */
    public static TooltipFlag getTooltipFlag() {
        if (FMLEnvironment.dist.isClient()) return ClientAccess.getTooltipFlag();
        return TooltipFlag.NORMAL;
    }

    public static ResourceLocation loc(String path) {
        return new ResourceLocation(MODID, path);
    }

    private static class ClientAccess {
        static TooltipFlag getTooltipFlag() {
            return Minecraft.getInstance().options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
        }
    }

    private static void trackCooldown(AttackEntityEvent e) {
        Player p = e.getEntity();
        ApothicAttributes.localAtkStrength = p.getAttackStrengthScale(0.5F);
    }
}