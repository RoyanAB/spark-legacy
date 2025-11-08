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

import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.common.SparkPlugin;
import me.lucko.spark.common.sampler.source.ClassSourceLookup;
import me.lucko.spark.common.sampler.source.SourceMetadata;
import me.lucko.spark.common.util.SparkThreadFactory;
import me.lucko.spark.fabric.FabricClassSourceLookup;
import me.lucko.spark.fabric.FabricServerCommandSender;
import me.lucko.spark.fabric.FabricSparkMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.Person;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.stream.Collectors;

public abstract class FabricSparkPlugin implements SparkPlugin {

    private final FabricSparkMod mod;
    private final Logger logger;
    protected final ScheduledExecutorService scheduler;

    protected SparkPlatform platform;

    protected FabricSparkPlugin(FabricSparkMod mod) {
        this.mod = mod;
        this.logger = LogManager.getLogger("spark");
        this.scheduler = Executors.newScheduledThreadPool(4, new SparkThreadFactory());
    }

    public void enable() {
        this.platform = new SparkPlatform(this);
        this.platform.enable();
    }

    public void disable() {
        this.platform.disable();
        this.scheduler.shutdown();
    }

    @Override
    public String getVersion() {
        return this.mod.getVersion();
    }

    @Override
    public Path getPluginDirectory() {
        return this.mod.getConfigDirectory();
    }

    @Override
    public void executeAsync(Runnable task) {
        this.scheduler.execute(task);
    }

    @Override
    public void log(Level level, String msg) {
        if (level.intValue() >= 1000) { // severe
            this.logger.error(msg);
        } else if (level.intValue() >= 900) { // warning
            this.logger.warn(msg);
        } else {
            this.logger.info(msg);
        }
    }

    @Override
    public void log(Level level, String msg, Throwable throwable) {
        if (level.intValue() >= 1000) { // severe
            this.logger.error(msg, throwable);
        } else if (level.intValue() >= 900) { // warning
            this.logger.warn(msg, throwable);
        } else {
            this.logger.info(msg, throwable);
        }
    }

    @Override
    public ClassSourceLookup createClassSourceLookup() {
        return new FabricClassSourceLookup(createClassFinder());
    }

    @Override
    public Collection<SourceMetadata> getKnownSources() {
        return SourceMetadata.gather(
                FabricLoader.getInstance().getAllMods(),
                mod -> mod.getMetadata().getId(),
                mod -> mod.getMetadata().getVersion().getFriendlyString(),
                mod -> mod.getMetadata().getAuthors().stream()
                        .map(Person::getName)
                        .collect(Collectors.joining(", ")),
                mod -> mod.getMetadata().getDescription()
        );
    }

    protected List<String> generateSuggestions(FabricServerCommandSender sender, String[] args) {
        return this.platform.tabCompleteCommand(sender, args);
    }

    protected static String[] processArgs(String[] args, boolean tabComplete) {
        String[] split = Arrays.stream(args).filter(c -> tabComplete || (!c.trim().isEmpty())).toArray(String[]::new);
        return split;
    }
}
