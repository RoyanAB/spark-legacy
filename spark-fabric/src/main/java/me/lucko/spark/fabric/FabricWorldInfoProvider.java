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

import me.lucko.spark.common.platform.world.AbstractChunkInfo;
import me.lucko.spark.common.platform.world.CountMap;
import me.lucko.spark.common.platform.world.WorldInfoProvider;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeInstanceMultiMap;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public abstract class FabricWorldInfoProvider implements WorldInfoProvider {

    @Override
    public Collection<DataPackInfo> pollDataPacks() {
        return null;
    }

    public static final class Server extends FabricWorldInfoProvider {
        private final MinecraftServer server;

        public Server(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public CountsResult pollCounts() {
            int players = this.server.getPlayerCount();
            int entities = 0;
            int chunks = 0;
            int tileEntities = 0;

            for (ServerWorld world : this.server.worlds) {
                entities += world.entities.size();
                tileEntities += world.blockEntities.size();

                chunks += world.getChunkSource().getLoadedChunks().size();
            }

            return new CountsResult(players, entities, tileEntities, chunks);
        }

        @Override
        public ChunksResult<FabricChunkInfo> pollChunks() {
            ChunksResult<FabricChunkInfo> data = new ChunksResult<>();

            for (ServerWorld world : this.server.worlds) {
                ArrayList<FabricChunkInfo> list = new ArrayList<>();
                for(WorldChunk chunk : world.getChunkSource().getLoadedChunks()) {
                    list.add(new FabricChunkInfo(chunk));
                }
                data.put(world.dimension.getType().toString(), list);
            }

            return data;
        }

        @Override
        public GameRulesResult pollGameRules() {
            return null;
        }
    }

    static final class FabricChunkInfo extends AbstractChunkInfo<Entity> {
        private final CountMap<Entity> entityCounts;

        FabricChunkInfo(WorldChunk chunk) {
            super(chunk.chunkX, chunk.chunkZ);

            this.entityCounts = new CountMap.Simple<>(new HashMap<>());
            for(TypeInstanceMultiMap<Entity> entityList : chunk.getEntities()) {
                entityList.forEach(entity -> {
                    this.entityCounts.increment(entity);
                });
            }
        }

        @Override
        public CountMap<Entity> getEntityCounts() {
            return this.entityCounts;
        }

        @Override
        public String entityTypeName(Entity entity) {
            return nonNullName(Entities.getKey(entity), entity);
        }

        private static String nonNullName(Identifier res, Entity type) {
            return (res != null) ? res.toString() : type.getName();
        }
    }
}

