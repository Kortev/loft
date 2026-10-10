package io.github.kortev.shootingstar.test;

import io.github.kortev.shootingstar.ShootingStar;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

/** What every mod in this build ships as data, checked as the server loaded it. */
public class DataGameTests implements FabricGameTest {
	/**
	 * Every advancement file in the {@code shootingstar} namespace, whichever jar it came in, loaded. One that names an
	 * icon with no item (a block registered without one) is dropped at server start with only a line in the log.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "a_data", tickLimit = 20)
	public void everyAdvancementLoads(TestContext context) {
		MinecraftServer server = context.getWorld().getServer();
		ResourceFinder finder = ResourceFinder.json("advancement");
		List<Identifier> broken = new ArrayList<>();
		int shipped = 0;
		for (Identifier file : finder.findResources(server.getResourceManager()).keySet()) {
			if (file.getNamespace().equals(ShootingStar.MOD_ID)) {
				shipped++;
				Identifier id = finder.toResourceId(file);
				if (server.getAdvancementLoader().get(id) == null) {
					broken.add(id);
				}
			}
		}
		context.assertTrue(shipped > 0, "found no advancement files at all");
		context.assertTrue(broken.isEmpty(), "advancements that did not load: " + broken);
		context.complete();
	}
}
