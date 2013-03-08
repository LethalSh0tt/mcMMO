package com.gmail.nossr50.commands.player;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.database.Leaderboard;
import com.gmail.nossr50.database.queuemanager.McRankAsync;
import com.gmail.nossr50.datatypes.McMMOPlayer;
import com.gmail.nossr50.datatypes.PlayerProfile;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.skills.utilities.SkillTools;
import com.gmail.nossr50.skills.utilities.SkillType;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.Users;

public class McrankCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (args.length) {
        case 0:
            if (!Permissions.mcrank(sender)) {
                sender.sendMessage(command.getPermissionMessage());
                return true;
            }

            if (!(sender instanceof Player)) {
                return false;
            }

            if (Config.getInstance().getUseMySQL()) {
                sqlDisplay(sender, sender.getName());
            }
            else {
                Leaderboard.updateLeaderboards(); // Make sure the information is up to date
                flatfileDisplay(sender, sender.getName());
            }

            return true;

        case 1:
            if (!Permissions.mcrankOthers(sender)) {
                sender.sendMessage(command.getPermissionMessage());
                return true;
            }

            McMMOPlayer mcMMOPlayer = Users.getPlayer(args[0]);

            if (mcMMOPlayer == null) {
                PlayerProfile profile = new PlayerProfile(args[0], false); //Temporary Profile

                if (!profile.isLoaded()) {
                    sender.sendMessage(LocaleLoader.getString("Commands.DoesNotExist"));
                    return true;
                }

                if (sender instanceof Player && !Permissions.mcrankOffline(sender)) {
                    sender.sendMessage(LocaleLoader.getString("Inspect.Offline"));
                    return true;
                }
            }
            else {
                Player target = mcMMOPlayer.getPlayer();

                if (sender instanceof Player && !Misc.isNear(((Player) sender).getLocation(), target.getLocation(), 5.0) && !Permissions.mcrankFar(sender)) {
                    sender.sendMessage(LocaleLoader.getString("Inspect.TooFar"));
                    return true;
                }
            }

            if (Config.getInstance().getUseMySQL()) {
                sqlDisplay(sender, args[0]);
            }
            else {
                Leaderboard.updateLeaderboards(); // Make sure the information is up to date
                flatfileDisplay(sender, args[0]);
            }

            return true;

        default:
            return false;
        }
    }

    private void flatfileDisplay(CommandSender sender, String playerName) {
        sender.sendMessage(LocaleLoader.getString("Commands.mcrank.Heading"));
        sender.sendMessage(LocaleLoader.getString("Commands.mcrank.Player", playerName));

        for (SkillType skillType : SkillType.values()) {
            int[] rankInts = Leaderboard.getPlayerRank(playerName, skillType);

            if (skillType.isChildSkill()) {
                continue;
            }

            if (rankInts[1] == 0) {
                sender.sendMessage(LocaleLoader.getString("Commands.mcrank.Skill", SkillTools.getSkillName(skillType), LocaleLoader.getString("Commands.mcrank.Unranked"))); // Don't bother showing ranking for players without skills
            }
            else {
                sender.sendMessage(LocaleLoader.getString("Commands.mcrank.Skill", SkillTools.getSkillName(skillType), rankInts[0]));
            }
        }

        // Show the powerlevel ranking
        int[] rankInts = Leaderboard.getPlayerRank(playerName);

        if (rankInts[1] == 0) {
            sender.sendMessage(LocaleLoader.getString("Commands.mcrank.Overall", LocaleLoader.getString("Commands.mcrank.Unranked"))); // Don't bother showing ranking for players without skills
        }
        else {
            sender.sendMessage(LocaleLoader.getString("Commands.mcrank.Overall", rankInts[0]));
        }
    }

    private void sqlDisplay(CommandSender sender, String playerName) {
        if (mcMMO.queueManager.contains(sender.getName()) || !mcMMO.queueManager.queue(new McRankAsync(playerName, sender))) {
            //Tell user to wait until their first command goes through
        }
    }
}
