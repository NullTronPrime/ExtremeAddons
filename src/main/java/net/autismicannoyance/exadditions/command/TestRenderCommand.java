package net.autismicannoyance.exadditions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.autismicannoyance.exadditions.client.VectorRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class TestRenderCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("testrender")
                .requires(source -> source.hasPermission(2))
                .executes(TestRenderCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        Vec3 playerPos = context.getSource().getPosition();
        System.out.println("TestRender command executed at: " + playerPos);

        if (context.getSource().getLevel().isClientSide) {
            testRender(playerPos);
            context.getSource().sendSuccess(() -> Component.literal("Test render executed on client"), false);
        } else {
            // Execute on client side from server
            context.getSource().sendSuccess(() -> Component.literal("Test render command received on server, executing on client"), false);
            testRender(playerPos);
        }
        return 1;
    }

    @OnlyIn(Dist.CLIENT)
    private static void testRender(Vec3 playerPos) {
        System.out.println("=== TEST RENDER START ===");
        System.out.println("Test render at position: " + playerPos);

        // Clear any existing commands first
        VectorRenderer.clearAll();

        // Test basic triangle - make it big and obvious
        Vec3 a = playerPos.add(2, 2, 2);
        Vec3 b = playerPos.add(0, 2, 2);
        Vec3 c = playerPos.add(1, 4, 2);
        int[] colors = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF}; // Red, Green, Blue vertices

        System.out.println("Creating plane with vertices:");
        System.out.println("A: " + a);
        System.out.println("B: " + b);
        System.out.println("C: " + c);

        VectorRenderer.drawPlane(a, b, c, colors, true, 200, VectorRenderer.Transform.IDENTITY);

        // Test basic line - make it thick and obvious
        Vec3 start = playerPos.add(-2, 1, 0);
        Vec3 end = playerPos.add(-2, 3, 0);
        System.out.println("Creating line from " + start + " to " + end);
        VectorRenderer.drawLine(start, end, 0xFFFFFF00, 0.2f, false, 200, VectorRenderer.Transform.IDENTITY);

        System.out.println("Commands added to VectorRenderer.");
        System.out.println("=== TEST RENDER END ===");
    }
}