/*
 * This file is part of spark.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.lucko.spark.fabric;

import me.lucko.spark.fabric.plugin.FabricServerSparkPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.handler.CommandRegistry;
import net.ornithemc.osl.entrypoints.api.ModInitializer;
import net.ornithemc.osl.lifecycle.api.server.MinecraftServerEvents;

import java.nio.file.Path;

public class FabricSparkMod implements ModInitializer {
    private static FabricSparkMod mod;

    private ModContainer container;
    private Path configDirectory;

    private FabricServerSparkPlugin activeServerPlugin = null;

    @Override
    public void init() {
        FabricSparkMod.mod = this;

        FabricLoader loader = FabricLoader.getInstance();
        this.container = loader.getModContainer("spark")
                .orElseThrow(() -> new IllegalStateException("Unable to get container for spark"));
        this.configDirectory = loader.getConfigDir().resolve("spark");

        // server event hooks
        MinecraftServerEvents.START.register(this::initializeServer);
        MinecraftServerEvents.STOP.register(this::onServerStopping);
        MinecraftServerEvents.TICK_END.register(this::ServerTickEvents_END_SERVER_TICK);
    }

    // server
    public void initializeServer(MinecraftServer server) {
        this.activeServerPlugin = FabricServerSparkPlugin.register(this, server);
        onServerCommandRegister(server);
        ServerTickEvents_START_SERVER_TICK(server);
    }

    public void onServerStopping(MinecraftServer stoppingServer) {
        if (this.activeServerPlugin != null) {
            this.activeServerPlugin.disable();
            this.activeServerPlugin = null;
        }
    }

    public void onServerCommandRegister(MinecraftServer server) {
        if (this.activeServerPlugin != null) {
            ((CommandRegistry)server.getCommandHandler()).register(this.activeServerPlugin);
        }
    }

    public String getVersion() {
        return this.container.getMetadata().getVersion().getFriendlyString();
    }

    public Path getConfigDirectory() {
        if (this.configDirectory == null) {
            throw new IllegalStateException("Config directory not set");
        }
        return this.configDirectory;
    }

    public void ServerTickEvents_START_SERVER_TICK(MinecraftServer server)
    {
        this.activeServerPlugin.getTickHook().onStartServerTick();
        this.activeServerPlugin.getTickReporter().onStartServerTick();
    }

    // fabric-api: ServerTickEvents.END_SERVER_TICK
    public void ServerTickEvents_END_SERVER_TICK(MinecraftServer server)
    {
        this.activeServerPlugin.getTickReporter().onEndServerTick();
    }

}
