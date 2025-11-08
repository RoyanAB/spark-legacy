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

package me.lucko.spark.fabric.plugin;

import me.lucko.spark.common.monitor.ping.PlayerPingProvider;
import me.lucko.spark.common.platform.PlatformInfo;
import me.lucko.spark.common.platform.serverconfig.ServerConfigProvider;
import me.lucko.spark.common.platform.world.WorldInfoProvider;
import me.lucko.spark.common.sampler.ThreadDumper;
import me.lucko.spark.common.tick.TickHook;
import me.lucko.spark.common.tick.TickReporter;
import me.lucko.spark.fabric.FabricPlatformInfo;
import me.lucko.spark.fabric.FabricPlayerPingProvider;
import me.lucko.spark.fabric.FabricServerCommandSender;
import me.lucko.spark.fabric.FabricServerConfigProvider;
import me.lucko.spark.fabric.FabricSparkMod;
import me.lucko.spark.fabric.FabricTickHook;
import me.lucko.spark.fabric.FabricTickReporter;
import me.lucko.spark.fabric.FabricWorldInfoProvider;
import me.lucko.spark.fabric.mixin.MinecraftServerAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.Command;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class FabricServerSparkPlugin extends FabricSparkPlugin implements Command {

    public static FabricServerSparkPlugin register(FabricSparkMod mod, MinecraftServer server) {
        FabricServerSparkPlugin plugin = new FabricServerSparkPlugin(mod, server);
        plugin.enable();
        return plugin;
    }

    private final MinecraftServer server;
    private final ThreadDumper gameThreadDumper;
    private final FabricTickHook tickHook;
    private final FabricTickReporter tickReporter;

    public FabricServerSparkPlugin(FabricSparkMod mod, MinecraftServer server) {
        super(mod);
        this.server = server;
        this.gameThreadDumper = new ThreadDumper.Specific(((MinecraftServerAccessor) server).spark_getThread());

        this.tickHook = new FabricTickHook();
        this.tickReporter =  new FabricTickReporter();
    }

    @Override
    public void enable() {
        super.enable();
    }

    @Override
    public Stream<FabricServerCommandSender> getCommandSenders() {
        return Stream.concat(
                this.server.getPlayerManager().getAll().stream(),
                Stream.of(this.server)
        ).map(FabricServerCommandSender::new);
    }

    @Override
    public void executeSync(Runnable task) {
        this.server.submit(task);
    }

    @Override
    public ThreadDumper getDefaultThreadDumper() {
        return this.gameThreadDumper;
    }

    public FabricTickHook getTickHook() {
        return this.tickHook;
    }

    @Override
    public TickHook createTickHook() {
        return this.getTickHook();
    }

    public FabricTickReporter getTickReporter() {
        return this.tickReporter;
    }

    @Override
    public TickReporter createTickReporter() {
        return this.getTickReporter();
    }
    @Override
    public PlayerPingProvider createPlayerPingProvider() {
        return new FabricPlayerPingProvider(this.server);
    }

    @Override
    public ServerConfigProvider createServerConfigProvider() {
        return new FabricServerConfigProvider();
    }

    @Override
    public WorldInfoProvider createWorldInfoProvider() {
        return new FabricWorldInfoProvider.Server(this.server);
    }

    @Override
    public PlatformInfo getPlatformInfo() {
        return new FabricPlatformInfo(PlatformInfo.Type.SERVER);
    }

    @Override
    public String getCommandName() {
        return "spark";
    }

    //Command
    @Override
    public String getName() {
        return getCommandName();
    }

    @Override
    public String getUsage(CommandSource source) {
        return "/" + getCommandName();
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList(getCommandName());
    }

    @Override
    public void run(MinecraftServer server, CommandSource source, String[] args) throws CommandException {
        String[] proc = processArgs(args, false);

        this.platform.executeCommand(new FabricServerCommandSender(source), proc);
    }

    @Override
    public boolean canUse(MinecraftServer server, CommandSource source) {
        return this.platform.hasPermissionForAnyCommand(new FabricServerCommandSender(source));
    }

    @Override
    public List<String> getSuggestions(MinecraftServer server, CommandSource source, String[] args, @Nullable BlockPos pos) {
        String[] proc = processArgs(args, true);
        return generateSuggestions(new FabricServerCommandSender(source), proc);
    }

    @Override
    public boolean hasTargetSelectorAt(String[] args, int index) {
        return false;
    }

    @Override
    public int compareTo(@NotNull Command o) {
        return getCommandName().compareTo(o.getName());
    }
}
