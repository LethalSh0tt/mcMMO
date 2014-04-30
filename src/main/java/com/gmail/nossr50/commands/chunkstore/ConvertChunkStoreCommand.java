package com.gmail.nossr50.commands.chunkstore;

import java.io.IOException;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.gmail.nossr50.mcMMO;

public class ConvertChunkStoreCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage("[mcMMO] This command cannot can only be executed from the console");
            return false;
        }

        switch (args.length) {
            case 2:
                // TODO: Make this less lazy, I'm a hypocrite -nossr50
                if (args[1].equalsIgnoreCase("chunkstore")) {
                    try {
                        System.out.println("[mcMMO] Starting the ChunkStore conversion process...");
                        System.out.println("[mcMMO] This may take a while depending on your world size.");
                        mcMMO.getPlaceStore().convertChunkFormat();
                        System.out.println("[mcMMO] Finished...");
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return true;
                }
                else {
                    return false;
                }
            default:
                return false;
        }
    }
}
