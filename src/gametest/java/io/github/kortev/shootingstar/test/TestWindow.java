package io.github.kortev.shootingstar.test;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * A test client's window is a real one, on the desktop of whoever is using the computer it runs on: a key pressed or a
 * mouse moved while it has the focus would turn the view, open a menu or pause the game, and resizing it (maximizing it
 * by accident) would spoil the recording. The tests drive the game themselves, so the window is left deaf to all of it.
 */
final class TestWindow {
	private static boolean sealed;
	/**
	 * Set by a reel that digs with the game's own mining (a held attack key breaks blocks only while the game thinks the mouse is
	 * captured): the flag is set in the game, never the real cursor, so it's left alone.
	 */
	static boolean mining;

	/** The game told the mouse is captured, without capturing anything: only mining reads it. */
	static void mining(MinecraftClient client, boolean on) {
		mining = on;
		try {
			java.lang.reflect.Field field = net.minecraft.client.Mouse.class.getDeclaredField("cursorLocked");
			field.setAccessible(true);
			field.setBoolean(client.mouse, on);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("the mouse's capture flag", e);
		}
	}

	private TestWindow() {
	}

	/** Once: no keys, no mouse, no resizing. Every tick: the cursor let go, should the game have captured it again. */
	static void keepToItself(MinecraftClient client) {
		long handle = client.getWindow().getHandle();
		if (!sealed) {
			sealed = true;
			GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
			GLFW.glfwSetKeyCallback(handle, null);
			GLFW.glfwSetCharModsCallback(handle, null);
			GLFW.glfwSetMouseButtonCallback(handle, null);
			GLFW.glfwSetCursorPosCallback(handle, null);
			GLFW.glfwSetScrollCallback(handle, null);
			GLFW.glfwSetDropCallback(handle, null);
		}
		if (client.mouse.isCursorLocked() && !mining) {
			client.mouse.unlockCursor();
		}
	}
}
