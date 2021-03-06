/*
 * Copyright 2016 John Grosh (jagrosh).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spectra.commands;

import net.dv8tion.jda.entities.Guild;
import net.dv8tion.jda.entities.TextChannel;
import net.dv8tion.jda.entities.VoiceChannel;
import net.dv8tion.jda.events.message.MessageReceivedEvent;
import net.dv8tion.jda.utils.InviteUtil;
import spectra.Argument;
import spectra.Command;
import spectra.FeedHandler;
import spectra.PermLevel;
import spectra.Sender;
import spectra.SpConst;
import spectra.Spectra;
import spectra.datasources.Feeds;
import spectra.tempdata.Statistics;
import spectra.utils.FormatUtil;

/**
 *
 * @author John Grosh (jagrosh)
 */
public class SystemCmd extends Command {
    final Spectra spectra;
    final Feeds feeds;
    final Statistics statistics;
    public SystemCmd(Spectra spectra, Feeds feeds, Statistics statistics)
    {
        this.spectra = spectra;
        this.feeds = feeds;
        this.statistics = statistics;
        this.command = "system";
        this.help = "commands for controlling the bot";
        this.longhelp = "These commands are for controlling the inner-workings of the bot";
        this.level = PermLevel.JAGROSH;
        this.children = new Command[]{
            new SystemDebug(),
            new SystemIdle(),
            new SystemReady(),
            new SystemInvite(),
            new SystemSafe(),
            new SystemShutdown()
        };
    }
    
    private class SystemInvite extends Command
    {
        private SystemInvite()
        {
            this.command = "serverinvite";
            this.help = "gets an invite for a server";
            this.longhelp = "";
            this.level = PermLevel.JAGROSH;
            this.arguments = new Argument[]{
                new Argument("id",Argument.Type.SHORTSTRING,true)
            };
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event) {
            String id = (String)args[0];
            Guild guild = event.getJDA().getGuildById(id);
            if(guild==null)
            {
                Sender.sendResponse(SpConst.ERROR+"Guild not found", event);
                return false;
            }
            String code = null;
            try{
                code = guild.getInvites().get(0).getCode();
            } catch (Exception e){}
            if(code==null)
            {
                for(TextChannel tc : guild.getTextChannels())
                    try{
                        code = InviteUtil.createInvite(tc).getCode();
                        break;
                    }catch(Exception e){}
            }
            if(code==null)
            {
                for(VoiceChannel tc : guild.getVoiceChannels())
                    try{
                        code = InviteUtil.createInvite(tc).getCode();
                        break;
                    }catch(Exception e){}
            }
            if(code==null)
            {
                Sender.sendResponse(SpConst.WARNING+"Invites could not be found nor created.", event);
                return false;
            }
            else
            {
                Sender.sendResponse("http://discord.gg/"+code, event);
                return true;
            }
        }
    }
    
    private class SystemIdle extends Command
    {
        private SystemIdle()
        {
            this.command = "idle";
            this.help = "prevents the bot from receiving commands";
            this.longhelp = "This command prevents the bot from using any commands except those by the bot owner.";
            this.level = PermLevel.JAGROSH;
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event) {
            if(spectra.isIdling())
            {
                Sender.sendResponse(SpConst.ERROR+"**"+SpConst.BOTNAME+"** is already `IDLING`", event);
                return false;
            }
            spectra.setIdling(true);
            event.getJDA().getAccountManager().setIdle(true);
            Sender.sendResponse("\uD83D\uDCF4 **"+SpConst.BOTNAME+"** is now `IDLING`", event);
            return true;
        }
    }
    
    private class SystemReady extends Command
    {
        private SystemReady()
        {
            this.command = "ready";
            this.help = "allows the bot to recieve commands";
            this.longhelp = "This disables idle mode";
            this.level = PermLevel.JAGROSH;
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event) {
            if(!spectra.isIdling())
            {
                Sender.sendResponse(SpConst.ERROR+"**"+SpConst.BOTNAME+"** is already `READY`", event);
                return false;
            }
            spectra.setIdling(false);
            event.getJDA().getAccountManager().setIdle(false);
            Sender.sendResponse(SpConst.SUCCESS+"**"+SpConst.BOTNAME+"** is now `READY`", event);
            return true;
        }
    }
    
    private class SystemDebug extends Command
    {
        private SystemDebug()
        {
            this.command = "debug";
            this.help = "sets debug mode";
            this.longhelp = "This command toggles debug mode in console";
            this.level = PermLevel.JAGROSH;
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event) {
            if(spectra.isDebug())
            {
                spectra.setDebug(false);
                Sender.sendResponse(SpConst.SUCCESS+"**"+SpConst.BOTNAME+"** is no longer in Debug Mode", event);
                return true;
            }
            else
            {
                spectra.setDebug(true);
                Sender.sendResponse("\uD83D\uDCDF **"+SpConst.BOTNAME+"** is now in Debug Mode", event);
                return true;
            }
        }
    }
    
    private class SystemSafe extends Command
    {
        private SystemSafe()
        {
            this.command = "safe";
            this.help = "sets safe mode";
            this.longhelp = "This command toggles safe mode";
            this.level = PermLevel.JAGROSH;
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event) {
            if(spectra.isSafety())
            {
                spectra.setSafeMode(false);
                Sender.sendResponse(SpConst.SUCCESS+"**"+SpConst.BOTNAME+"** is no longer in Safe Mode", event);
                return true;
            }
            else
            {
                spectra.setSafeMode(true);
                Sender.sendResponse("\uD83D\uDCDF **"+SpConst.BOTNAME+"** is now in Safe Mode", event);
                return true;
            }
        }
    }
    
    private class SystemShutdown extends Command
    {
        private SystemShutdown()
        {
            this.command = "shutdown";
            this.help = "shuts down the bot safely";
            this.longhelp = "This command shuts the bot down safely, closing any threadpools. Must be idling first.";
            this.level = PermLevel.JAGROSH;
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event) {
            if(!spectra.isIdling())
            {
                Sender.sendResponse(SpConst.ERROR+"Cannot shutdown if not idling!", event);
                return false;
            }
            try{
            event.getJDA().getTextChannelById(feeds.feedForGuild(event.getJDA().getGuildById(SpConst.JAGZONE_ID), Feeds.Type.BOTLOG)[Feeds.CHANNELID])
                    .sendMessage(FeedHandler.botlogFormat(SpConst.ERROR+"**"+SpConst.BOTNAME+"** is going <@&182294168083628032>"+"\nRuntime: "+FormatUtil.secondsToTime(statistics.getUptime())));
            event.getChannel().sendMessage("\uD83D\uDCDF Shutting down...");}catch(Exception e){System.err.println(e);}
            spectra.shutdown();
            return true;
        }
    }
    }
