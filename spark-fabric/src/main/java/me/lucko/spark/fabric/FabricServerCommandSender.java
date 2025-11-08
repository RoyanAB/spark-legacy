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

import me.lucko.spark.common.command.sender.AbstractCommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import java.util.UUID;

public class FabricServerCommandSender extends AbstractCommandSender<CommandSource> {
    public FabricServerCommandSender(CommandSource commandSource) {
        super(commandSource);
    }

    @Override
    public String getName() {
        String name = this.delegate.getName();
        if (this.delegate.asEntity() != null && name.equals("Server")) {
            return "Console";
        }
        return name;
    }

    @Override
    public UUID getUniqueId() {
        Entity entity = this.delegate.asEntity();
        return entity != null ? entity.getUuid() : null;
    }

    @Override
    public void sendMessage(Component message) {
        Text component = LiteralText.Serializer.fromJson(GsonComponentSerializer.gson().serialize(message));
        this.delegate.sendMessage(component);
    }

    @Override
    public boolean hasPermission(String permission) {
        if (this.delegate instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity)this.delegate;
            MinecraftServer server = this.delegate.getServer();
            if(server.getPlayerManager().isOp(player.getGameProfile()))
                return true;
            else {
                String serverOwner = server.getUsername();
                if(player.getGameProfile().getName() != null && serverOwner != null)
                    return serverOwner.equals(player.getGameProfile().getName());
                else
                    return false;
            }
        } else {
            return true;
        }
    }

    @Override
    protected Object getObjectForComparison() {
        UUID uniqueId = getUniqueId();
        if (uniqueId != null) {
            return uniqueId;
        }
        Entity entity = this.delegate.asEntity();
        if (entity != null) {
            return entity;
        }
        return getName();
    }
}
