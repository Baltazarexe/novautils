package dev.novautils.mixin;

import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** AutoSmelt asks the furnace menu itself what it can smelt and burn (the server's recipe sets). */
@Mixin(AbstractFurnaceMenu.class)
public interface FurnaceMenuInvoker {
    @Invoker("canSmelt")
    boolean novautils$canSmelt(ItemStack stack);

    @Invoker("isFuel")
    boolean novautils$isFuel(ItemStack stack);
}
