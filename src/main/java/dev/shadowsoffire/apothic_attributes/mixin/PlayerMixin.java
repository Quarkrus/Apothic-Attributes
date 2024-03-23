package dev.shadowsoffire.apothic_attributes.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

import dev.shadowsoffire.apothic_attributes.api.ALObjects.Attributes;
import dev.shadowsoffire.apothic_attributes.util.IEntityOwned;
import dev.shadowsoffire.apothic_attributes.util.IFlying;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

@Mixin(value = Player.class, remap = false)
public class PlayerMixin implements IFlying {

    @Shadow
    private Abilities abilities;

    /**
     * This field is used to record the value of {@link Abilities#flying} right after deserialization to restore it when attributes are read.
     */
    @Nullable
    private boolean apoth_flying;

    /**
     * Constructor mixin to call {@link IEntityOwned#setOwner(LivingEntity)} on {@link #abilities}.<br>
     * Supports {@link Attributes#CREATIVE_FLIGHT}.
     */
    @Inject(at = @At(value = "TAIL"), method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;FLcom/mojang/authlib/GameProfile;)V", require = 1, remap = false)
    public void apoth_ownedAbilities(Level level, BlockPos pos, float yRot, GameProfile profile, CallbackInfo ci) {
        ((IEntityOwned) abilities).setOwner((LivingEntity) (Object) this);
    }

    /**
     * Records the value of {@link Abilities#flying} immediately after deserialization, so it can be re-set when attributes are read.<br>
     * Without this, players with attribute-provided flight will lose it when logging in and logging back out.
     */
    @Inject(at = @At(value = "TAIL"), method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", require = 1)
    public void apoth_cacheFlying(CompoundTag tag, CallbackInfo ci) {
        if (abilities.flying) {
            markFlying();
        }
    }

    @Override
    public boolean getAndDestroyFlyingCache() {
        boolean value = this.apoth_flying;
        this.apoth_flying = false;
        return value;
    }

    @Override
    public void markFlying() {
        this.apoth_flying = true;
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", ordinal = 0), method = "attack(Lnet/minecraft/world/entity/Entity;)V")
    private boolean apoth_handleKilledByAuxDmg(LivingEntity target, DamageSource src, float dmg) {
        boolean res = target.hurt(src, dmg);
        return res || target.getPersistentData().getBoolean("apoth.killed_by_aux_dmg");
    }

}
