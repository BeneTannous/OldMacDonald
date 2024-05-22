package me.mooseman06.oldmacdonald;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class OldMacDonaldTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("help");
            completions.add("addfarm");
            completions.add("removefarm");
            completions.add("listfarms");
            completions.add("feedback");
            completions.add("info");
            if (sender.hasPermission("oldmacdonald.setmaxradius")) {
                completions.add("setmaxradius");
            }
            if (sender.hasPermission("oldmacdonald.setdefaultradius")) {
                completions.add("setdefaultradius");
            }
            if (sender.hasPermission("oldmacdonald.setmaxmobs")) {
                completions.add("setmaxmobs");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("addfarm")) {
            completions.add("Current");
            completions.add("<x> <y> <z>");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("addfarm") && args[1].equalsIgnoreCase("current")) {
            completions.add("<Farm Name>");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("addfarm") && args[1].equalsIgnoreCase("current")) {
            completions.add("<radius>");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("addfarm") && !args[1].equalsIgnoreCase("current")) {
            completions.add("<y> <z>");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("addfarm") && !args[1].equalsIgnoreCase("current")) {
            completions.add("<z>");
        } else if (args.length == 5 && args[0].equalsIgnoreCase("addfarm") && !args[1].equalsIgnoreCase("current")) {
            completions.add("<Farm Name>");
        } else if (args.length == 6 && args[0].equalsIgnoreCase("addfarm") && !args[1].equalsIgnoreCase("current")) {
            completions.add("Radius");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("removefarm")) {
            completions.add("<List #>");
            completions.add("<Farm Name>");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setmaxradius") && sender.hasPermission("oldmacdonald.setmaxradius")) {
            completions.add("<radius (#of blocks)>");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setdefaultradius") && sender.hasPermission("oldmacdonald.setdefaultradius")) {
            completions.add("<radius (#of blocks)>");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setmaxmobs") && sender.hasPermission("oldmacdonald.setmaxmobs")) {
            completions.add("<# of mobs>");
        }
        return completions;
    }

}
