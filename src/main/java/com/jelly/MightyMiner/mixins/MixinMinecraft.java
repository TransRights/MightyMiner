package com.jelly.MightyMiner.mixins;

import com.jelly.MightyMiner.MightyMiner;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = { "startGame" }, at = { @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;setFramebufferColor(FFFF)V", shift = At.Shift.AFTER) })
    private void onInit(final CallbackInfo ci) {
        MightyMiner.onStartGame();
    }
}
