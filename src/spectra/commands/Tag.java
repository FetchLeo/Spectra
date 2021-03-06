/*
 * Copyright 2016 jagrosh.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.entities.Guild;
import net.dv8tion.jda.entities.User;
import net.dv8tion.jda.events.message.MessageReceivedEvent;
import spectra.Argument;
import spectra.Command;
import spectra.FeedHandler;
import spectra.JagTag;
import spectra.PermLevel;
import spectra.Sender;
import spectra.SpConst;
import spectra.datasources.Feeds;
import spectra.datasources.Overrides;
import spectra.datasources.Settings;
import spectra.datasources.Tags;

/**
 *
 * @author John Grosh (jagrosh)
 */
public class Tag extends Command{
    private final Tags tags;
    private final Overrides overrides;
    private final Settings settings;
    private final FeedHandler handler;
    public Tag(Tags tags, Overrides overrides, Settings settings, FeedHandler handler)
    {
        this.tags = tags;
        this.overrides = overrides;
        this.settings = settings;
        this.handler = handler;
        this.command = "tag";
        this.aliases = new String[]{"t"};
        this.help = "displays a tag; tag commands (try `"+SpConst.PREFIX+"tag help`)";
        this.longhelp = "This command is used to create, edit, and view \"tags\". "
                + "Tags are a method of storing text for easy recollection later. "
                + "Additionally, some scripting-esque elements can be used to provide "
                + "extra functionality. See <"+JagTag.URL+"> for available elements.";
        this.arguments = new Argument[]{
            new Argument("tagname",Argument.Type.SHORTSTRING,true),
            new Argument("tag arguments",Argument.Type.LONGSTRING,false)
        };
        this.children = new Command[]{
            new TagCreate(),
            new TagDelete(),
            new TagEdit(),
            new TagList(),
            new TagOwner(),
            new TagRandom(),
            new TagRaw(),
            new TagRaw2(),
            new TagSearch(),
            new TagOverride(),
            new TagRestore(),
            new TagImport(),
            new TagMode(),
            new TagUnimport()
        };
    }
    @Override
    protected boolean execute(Object[] args, MessageReceivedEvent event) 
    {
        String tagname = (String)(args[0]);
        String tagargs = args[1]==null?null:(String)(args[1]);
        boolean local = false;
        boolean nsfw = JagTag.isNSFWAllowed(event);
        if(!event.isPrivate())
            local = "local".equalsIgnoreCase(settings.getSettingsForGuild(event.getGuild().getId())[Settings.TAGMODE]);
        
        String[] tag = null;
        if(!event.isPrivate())
            tag = overrides.findTag(event.getGuild(), tagname, nsfw);
        if(tag==null)
            tag = tags.findTag(tagname, event.getGuild(), local, nsfw);
        if(tag==null)
        {
            Sender.sendResponse(SpConst.ERROR+"Tag \""+tagname+"\" could not be found", event);
            return false;
        }
        Sender.sendResponse("\u200B"+JagTag.convertText(tag[Tags.CONTENTS], tagargs, event.getAuthor(), event.getGuild(), event.getChannel()), event);
        return true;
    }
    
    //subcommands
    private class TagCreate extends Command
    {
        private TagCreate()
        {
            this.command = "create";
            this.aliases= new String[]{"add"};
            this.help = "saves a tag for later recollection";
            this.longhelp = "This command creates a new tag (if it doesn't exist). This "
                    + "tag is owned by the creator, and can only be edited or deleted by that user. "
                    + "Note: Anyone found creating offensive or not-safe-for-work tags (without marking "
                    + "them as NSFW using proper JagTag notation) is at risk of being blacklisted from "
                    + "using "+SpConst.BOTNAME;
            //this.longhelp = "";
            this.arguments = new Argument[]{
                new Argument("tagname",Argument.Type.SHORTSTRING,true,1,80),
                new Argument("tag contents",Argument.Type.LONGSTRING,true)
            };
            this.cooldown = 60;
            this.cooldownKey = (event) -> {return event.getAuthor().getId()+"tagcreate";};
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event)
        {
            String tagname = (String)(args[0]);
            String contents = (String)(args[1]);
            String[] tag = tags.findTag(tagname);
            if(tag==null)//good to make it
            {
                tags.set(new String[]{
                event.getAuthor().getId(),
                tagname,
                contents
                });
                Sender.sendResponse(SpConst.SUCCESS+"Tag \""+tagname+"\" created successfully.", event);
                ArrayList<Guild> guildlist = new ArrayList<>();
                event.getJDA().getGuilds().stream().filter((g) -> (g.isMember(event.getAuthor()) || g.getId().equals(SpConst.JAGZONE_ID))).forEach((g) -> {
                    guildlist.add(g);
                });
                handler.submitText(Feeds.Type.TAGLOG, guildlist, 
                        "\uD83C\uDFF7 **"+event.getAuthor().getUsername()+"** (ID:"+event.getAuthor().getId()+") created tag **"+tagname+"** "
                                +(event.isPrivate() ? "in a Direct Message":("on **"+event.getGuild().getName()+"**")));
                return true;
            }
            else
            {
                Sender.sendResponse(SpConst.ERROR+"Tag \""+tag[Tags.TAGNAME]+"\" already exists.", event);
                return false;
            }
        }
    }
    
    private class TagDelete extends Command
    {
        private TagDelete()
        {
            this.command = "delete";
            this.aliases = new String[]{"remove"};
            this.help = "deletes a tag if it belongs to you";
            this.longhelp = "This command deletes a tag if you own it. If you are a server moderator "
                    + "and don't want a tag to be displayed, consider using `tag override` to \"delete\" it "
                    + "from the current server.";
            //this.longhelp = "";
            this.arguments = new Argument[]{
                new Argument("tagname",Argument.Type.SHORTSTRING,true)
            };
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event)
        {
            String tagname = (String)(args[0]);
            String[] tag = tags.findTag(tagname);
            if(tag==null)//nothing to edit
            {
                Sender.sendResponse(SpConst.ERROR+"Tag \""+tagname+"\" could not be found", event);
                return false;
            }
            else if(tag[Tags.OWNERID].equals(event.getAuthor().getId()) || SpConst.JAGROSH_ID.equals(event.getAuthor().getId()))
            {
                tagname = tag[Tags.TAGNAME];
                tags.removeTag(tag[Tags.TAGNAME]);
                Sender.sendResponse(SpConst.SUCCESS+"Tag \""+tag[Tags.TAGNAME]+"\" deleted successfully.", event);
                ArrayList<Guild> guildlist = new ArrayList<>();
                event.getJDA().getGuilds().stream().filter((g) -> (g.isMember(event.getAuthor()) || g.getId().equals(SpConst.JAGZONE_ID))).forEach((g) -> {
                    guildlist.add(g);
                });
                handler.submitText(Feeds.Type.TAGLOG, guildlist, 
                        "\uD83C\uDFF7 **"+event.getAuthor().getUsername()+"** (ID:"+event.getAuthor().getId()+") deleted tag **"+tagname+"** "
                                +(event.isPrivate() ? "in a Direct Message":("on **"+event.getGuild().getName()+"**")));
                return true;
            }
            else
            {
                String owner;
                User u = event.getJDA().getUserById(tag[Tags.OWNERID]);
                if(u!=null)
                    owner = "**"+u.getUsername()+"**";
                //else if(tag[Tags.OWNERID].startsWith("g"))
                    //owner = "the server *"+event.getGuild().getName()+"*";
                else
                    owner = "an unknown user (ID:"+tag[Tags.OWNERID]+")";
                Sender.sendResponse(SpConst.ERROR+"You cannot delete tag \""+tag[Tags.TAGNAME]+"\" because it belongs to **"+owner+"**", event);
                return false;
            }
        }
    }
    
    private class TagEdit extends Command
    {
        private TagEdit()
        {
            this.command = "edit";
            this.help = "edits a tag if it belongs to you";
            this.longhelp = "This command edits a tag you own.";
            //this.longhelp = "";
            this.arguments = new Argument[]{
                new Argument("tagname",Argument.Type.SHORTSTRING,true),
                new Argument("new contents",Argument.Type.LONGSTRING,true)
            };
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event)
        {
            String tagname = (String)(args[0]);
            String contents = (String)(args[1]);
            String[] tag = tags.findTag(tagname);
            if(tag==null)//nothing to edit
            {
                Sender.sendResponse(SpConst.ERROR+"Tag \""+tagname+"\" could not be found", event);
                return false;
            }
            else if(tag[Tags.OWNERID].equals(event.getAuthor().getId()) || SpConst.JAGROSH_ID.equals(event.getAuthor().getId()))
            {
                tagname = tag[Tags.TAGNAME];
                tags.set(new String[]{
                tag[Tags.OWNERID],
                tag[Tags.TAGNAME],
                contents
                });
                Sender.sendResponse(SpConst.SUCCESS+"Tag \""+tag[Tags.TAGNAME]+"\" edited successfully.", event);
                ArrayList<Guild> guildlist = new ArrayList<>();
                event.getJDA().getGuilds().stream().filter((g) -> (g.isMember(event.getAuthor()) || g.getId().equals(SpConst.JAGZONE_ID))).forEach((g) -> {
                    guildlist.add(g);
                });
                handler.submitText(Feeds.Type.TAGLOG, guildlist, 
                        "\uD83C\uDFF7 **"+event.getAuthor().getUsername()+"** (ID:"+event.getAuthor().getId()+") edited tag **"+tagname+"** "
                                +(event.isPrivate() ? "in a Direct Message":("on **"+event.getGuild().getName()+"**")));
                return true;
            }
            else
            {
                String owner;
                User u = event.getJDA().getUserById(tag[Tags.OWNERID]);
                if(u!=null)
                    owner = "**"+u.getUsername()+"**";
                //else if(tag[Tags.OWNERID].startsWith("g"))
                    //owner = "the server *"+event.getGuild().getName()+"*";
                else
                    owner = "an unknown user (ID:"+tag[Tags.OWNERID]+")";
                Sender.sendResponse(SpConst.ERROR+"You cannot edit tag \""+tag[Tags.TAGNAME]+"\" because it belongs to **"+owner+"**", event);
                return false;
            }
        }
    }
    
    private class TagList extends Command
    {
        private TagList()
        {
            this.command = "list";
            this.help = "shows a list of all tags owned by you, or another user";
            this.longhelp = "This command shows the list of tags you have created, or the list "
                    + "a different user has created, if you specify a username.";
            //this.longhelp = "";
            this.arguments = new Argument[]{
                new Argument("username",Argument.Type.USER,false)
            };
            this.cooldown = 10;
            this.cooldownKey = (event) -> {return event.getAuthor().getId()+"taglist";};
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event)
        {
            User user = (User)(args[0]);
            if(user==null)
                user = event.getAuthor();
            boolean nsfw = JagTag.isNSFWAllowed(event);
            ArrayList<String> taglist = tags.findTagsByOwner(user, nsfw);
            Collections.sort(taglist);
            StringBuilder builder;
            builder = new StringBuilder(SpConst.SUCCESS+taglist.size()+" tags owned by **"+user.getUsername()+"**: \n");
            taglist.stream().forEach((tag) -> {
                builder.append(tag).append(" ");
            });
            Sender.sendResponse(builder.toString(), event);
            return true;
        }
    }
    
    private class TagOwner extends Command
    {
        private TagOwner()
        {
            this.command = "owner";
            this.help = "shows the owner of a tag";
            this.longhelp = "This command shows who the owner of a tag is. If a tag is "
                    + "overriden, the tag will appear to be \"owned\" by the server.";
            //this.longhelp = "";
            this.arguments = new Argument[]{
                new Argument("tagname",Argument.Type.SHORTSTRING,true)
            };
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event)
        {
            String tagname = (String)(args[0]);
            String[] tag = null;
            if(!event.isPrivate())
                tag = overrides.findTag(event.getGuild(), tagname, true);
            if(tag==null)
                tag = tags.findTag(tagname, event.getGuild(), false, true);
            if(tag==null)
            {
                Sender.sendResponse(SpConst.ERROR+"Tag \""+tagname+"\" could not be found", event);
                return false;
            }
            String owner;
            User u = event.getJDA().getUserById(tag[Tags.OWNERID]);
            if(u!=null)
                owner = "**"+u.getUsername()+"** #"+u.getDiscriminator();
            else if(tag[Tags.OWNERID].startsWith("g"))
                owner = "the server *"+event.getGuild().getName()+"*";
            else
                owner = "an unknown user (ID:"+tag[Tags.OWNERID]+")";
            Sender.sendResponse("Tag \""+tag[Tags.TAGNAME]+"\" belongs to "+owner, event);
            return true;
        }
    }
    
    private class TagRandom extends Command
    {
        private TagRandom()
        {
            this.command = "random";
            this.help = "shows a random tag";
            this.longhelp = "This command brings up a random tag from all of the existing "
                    + "tags (or all the tags by users on the server in local mode).";
            //this.longhelp = "";
            this.arguments = new Argument[]{
            new Argument("tag arguments",Argument.Type.LONGSTRING,false)
            };
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event)
        {
            String tagargs = args[0]==null?null:(String)(args[0]);
            boolean local = false;
            boolean nsfw = JagTag.isNSFWAllowed(event);
            if(!event.isPrivate())
                local = "local".equalsIgnoreCase(settings.getSettingsForGuild(event.getGuild().getId())[Settings.TAGMODE]);
            List<String[]> taglist = tags.findTags(null, event.getGuild(), local, nsfw);
            if(taglist.isEmpty())
            {
                Sender.sendResponse(SpConst.WARNING+"No tags found!", event);
                return false;
            }
            String[] tag = taglist.get((int)(Math.random()*taglist.size()));
            Sender.sendResponse("Tag \""+tag[Tags.TAGNAME]+"\":\n"
                    +JagTag.convertText(tag[Tags.CONTENTS], tagargs, event.getAuthor(), event.getGuild(), event.getChannel()),
                    event);
            return true;
        }
    }
    
    private class TagRaw extends Command
    {
        private TagRaw()
        {
            this.command = "raw";
            this.help = "shows the raw (non-dynamic) tag text";
            this.longhelp = "This command shows the contents of a tag without "
                    + "interpretting any of the scripting.";
            //this.longhelp = "";
            this.arguments = new Argument[]{
            new Argument("tagname",Argument.Type.SHORTSTRING,true)
            };
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event)
        {
            String tagname = (String)(args[0]);
            boolean local = false;
            boolean nsfw = JagTag.isNSFWAllowed(event);
            if(!event.isPrivate())
                local = "local".equalsIgnoreCase(settings.getSettingsForGuild(event.getGuild().getId())[Settings.TAGMODE]);
            String[] tag = null;
            if(!event.isPrivate())
                tag = overrides.findTag(event.getGuild(), tagname, nsfw);
            if(tag==null)
                tag = tags.findTag(tagname, event.getGuild(), local, nsfw);
            if(tag==null)
            {
                Sender.sendResponse(SpConst.ERROR+"Tag \""+tagname+"\" could not be found", event);
                return false;
            }
            Sender.sendResponse("\u200B"+tag[Tags.CONTENTS], event);
            return true;
        }
    }
    
    private class TagRaw2 extends Command
    {
        private TagRaw2()
        {
            this.command = "raw2";
            this.help = "shows the raw tag text in a code block";
            this.longhelp = "This command shows the contents of a tag without "
                    + "interpretting any of the scripting. It also puts it in a "
                    + "code block to escape most formatting.";
            //this.longhelp = "";
            this.arguments = new Argument[]{
            new Argument("tagname",Argument.Type.SHORTSTRING,true)
            };
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event)
        {
            String tagname = (String)(args[0]);
            boolean local = false;
            boolean nsfw = JagTag.isNSFWAllowed(event);
            if(!event.isPrivate())
                local = "local".equalsIgnoreCase(settings.getSettingsForGuild(event.getGuild().getId())[Settings.TAGMODE]);
            String[] tag = null;
            if(!event.isPrivate())
                tag = overrides.findTag(event.getGuild(), tagname, nsfw);
            if(tag==null)
                tag = tags.findTag(tagname, event.getGuild(), local, nsfw);
            if(tag==null)
            {
                Sender.sendResponse(SpConst.ERROR+"Tag \""+tagname+"\" could not be found", event);
                return false;
            }
            Sender.sendResponse("```\n"+tag[Tags.CONTENTS]+"```", event);
            return true;
        }
    }
    
    private class TagSearch extends Command
    {
        private TagSearch()
        {
            this.command = "search";
            this.help = "searches for tags that include the given query";
            this.longhelp = "This command searches for tag names that contain the "
                    + "given query. If no query is provided, the full tag list will be "
                    + "returned.";
            //this.longhelp = "";
            this.arguments = new Argument[]{
            new Argument("query",Argument.Type.SHORTSTRING,false)
            };
            this.cooldown = 10;
            this.cooldownKey = (event) -> {return event.getAuthor().getId()+"tagsearch";};
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event)
        {
            String query = args[0]==null?null:(String)(args[0]);
            boolean local = false;
            boolean nsfw = JagTag.isNSFWAllowed(event);
            if(!event.isPrivate())
                local = "local".equalsIgnoreCase(settings.getSettingsForGuild(event.getGuild().getId())[Settings.TAGMODE]);
            List<String[]> taglist = tags.findTags(query, event.getGuild(), local, nsfw);
            if(taglist.isEmpty())
            {
                Sender.sendResponse(SpConst.WARNING+"No tags found matching \""+query+"\"!", event);
                return false;
            }
            StringBuilder builder = new StringBuilder(SpConst.SUCCESS).append(taglist.size()).append(" tags found");
            if(query!=null)
                builder.append(" containing \"").append(query).append("\"");
            builder.append(":\n");
            if(taglist.size()<100)
                Collections.sort(taglist, (a,b)->{return a[Tags.TAGNAME].compareTo(b[Tags.TAGNAME]);});
            taglist.stream().forEach((tag) -> {
                builder.append(tag[Tags.TAGNAME]).append(" ");
            });
            Sender.sendResponse(builder.toString(),event);
            return true;
        }
    }
    
    private class TagOverride extends Command
    {
        private TagOverride()
        {
            this.command = "override";
            this.help = "creates an override for the current server";
            this.longhelp = "This command overrides a tag on the server. This can be used "
                    + "to \"disable\" unwanted tags, or to use an existing tag for something else.";
            //this.longhelp = "";
            this.arguments = new Argument[]{
                new Argument("tagname",Argument.Type.SHORTSTRING,true),
                new Argument("new contents",Argument.Type.LONGSTRING,false)
            };
            this.level = PermLevel.MODERATOR;
            this.availableInDM = false;
            this.children = new Command[]{
                new TagOverrideList()
            };
        }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event)
        {
            String tagname = (String)(args[0]);
            String contents = args[1]==null?null:(String)(args[1]);
            if(contents==null)
                contents = "This tag was removed by **"+event.getAuthor().getUsername()+"**";
            String[] tag = overrides.findTag(event.getGuild(), tagname, true);
            if(tag==null)
                tag = tags.findTag(tagname);
            if(tag==null)//nothing to edit
            {
                Sender.sendResponse(SpConst.ERROR+"Tag \""+tagname+"\" could not be found", event);
                return false;
            }
            else
            {
                tagname = tag[Tags.TAGNAME];
                overrides.setTag(new String[]{"g"+event.getGuild().getId(),tag[Overrides.TAGNAME],contents});
                Sender.sendResponse(SpConst.SUCCESS+"Tag \""+tag[Tags.TAGNAME]+"\" overriden successfully.", event);
                handler.submitText(Feeds.Type.TAGLOG, event.getGuild(), 
                        "\uD83C\uDFF7 **"+event.getAuthor().getUsername()+"** (ID:"+event.getAuthor().getId()+") overrode tag **"+tagname
                                +"** on **"+event.getGuild().getName()+"**");
                return true;
            }
        }
        
        private class TagOverrideList extends Command
        {
            private TagOverrideList()
            {
                this.command = "list";
                this.help = "lists tag overrides on the current server";
                this.longhelp = "This command shows which tags have been overridden on the server.";
                this.level = PermLevel.MODERATOR;
                this.availableInDM = false;
            }
            @Override
            protected boolean execute(Object[] args, MessageReceivedEvent event)
            {
                List<String> list = overrides.findGuildTags(event.getGuild());
                if(list.isEmpty())
                    Sender.sendResponse(SpConst.WARNING+"No tags have been overriden on **"+event.getGuild().getName()+"**", event);
                else
                {
                    Collections.sort(list);
                    StringBuilder builder = new StringBuilder(SpConst.SUCCESS+list.size()+" tag overrides on **"+event.getGuild().getName()+"**:\n");
                    list.stream().forEach((tag) -> {
                        builder.append(tag).append(" ");
                    });
                    Sender.sendResponse(builder.toString(), event);
                }
                return true;
            }
        }
    }
    
    private class TagRestore extends Command
    {
        private TagRestore()
            {
                this.command = "restore";
                this.help = "restores a tag from overriden state";
                this.longhelp = "This command restores a tag from being overridden.";
                this.arguments = new Argument[]{
                    new Argument("tagname",Argument.Type.SHORTSTRING,true),
                };
                this.level = PermLevel.MODERATOR;
                this.availableInDM = false;
            }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event) {
            String tagname = (String)(args[0]);
            String[] tag = overrides.findTag(event.getGuild(), tagname, true);
            if(tag==null)
            {
                Sender.sendResponse(SpConst.ERROR+"Tag \""+tagname+"\" is not currently overriden", event);
                return false;
            }
            else
            {
                tagname = tag[Tags.TAGNAME];
                overrides.removeTag(tag);
                Sender.sendResponse(SpConst.SUCCESS+"Tag \""+tag[Tags.TAGNAME]+"\" restored successfully.", event);
                handler.submitText(Feeds.Type.TAGLOG, event.getGuild(), 
                        "\uD83C\uDFF7 **"+event.getAuthor().getUsername()+"** (ID:"+event.getAuthor().getId()+") restored tag **"+tagname
                                +"** on **"+event.getGuild().getName()+"**");
                return true;
            }
        }
    }
    
    private class TagMode extends Command 
    {
        private TagMode()
            {
                this.command = "mode";
                this.help = "sets the tag mode to local or global";
                this.longhelp = "This command sets the tag mode. In global mode, all existing tags are "
                        + "available on the server. In local mode, the only available tags will be those that "
                        + "were created by users on the server.";
                this.arguments = new Argument[]{
                    new Argument("mode",Argument.Type.SHORTSTRING,true),
                };
                this.level = PermLevel.ADMIN;
                this.availableInDM = false;
            }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event) {
            String mode = (String)(args[0]);
            if(mode.equalsIgnoreCase("local") || mode.equalsIgnoreCase("global"))
            {
                settings.setSetting(event.getGuild().getId(), Settings.TAGMODE, mode.toUpperCase());
                Sender.sendResponse(SpConst.SUCCESS+"Tag mode has been set to `"+mode.toUpperCase()+"`", event);
                return true;
            }
            else
            {
                Sender.sendResponse(SpConst.ERROR+"Valid tag modes are `LOCAL` and `GLOBAL`", event);
                return false;
            }
        }
    }
    
    private class TagImport extends Command
    {
        private TagImport()
            {
                this.command = "import";
                this.help = "imports a tag from a tag command";
                this.longhelp = "This command imports a tag as a psuedo-command on the server. "
                        + "For example, if the tag `hug` was imported, you would be able to use `"
                        + SpConst.PREFIX+"hug` instead of `"+SpConst.PREFIX+"tag hug`";
                this.arguments = new Argument[]{
                    new Argument("tagname",Argument.Type.SHORTSTRING,true),
                };
                this.children = new Command[]{
                    new TagImportList()
                };
                this.level = PermLevel.ADMIN;
                this.availableInDM = false;
            }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event) {
            String tagname = (String)(args[0]);
            String[] imports = Settings.tagCommandsFromList(settings.getSettingsForGuild(event.getGuild().getId())[Settings.TAGIMPORTS]);
            boolean found = false;
            for(String tag : imports)
                if(tag.equalsIgnoreCase(tagname))
                    found = true;
            if(!found)
            {
                String[] tag = overrides.findTag(event.getGuild(), tagname, true);
                if(tag==null)
                    tag = tags.findTag(tagname);
                if(tag==null)//nothing to import
                {
                    Sender.sendResponse(SpConst.ERROR+"Tag \""+tagname+"\" could not be found", event);
                    return false;
                }
                else
                {
                    tagname = tag[Tags.TAGNAME];
                    String cmds = settings.getSettingsForGuild(event.getGuild().getId())[Settings.TAGIMPORTS];
                    settings.setSetting(event.getGuild().getId(), Settings.TAGIMPORTS, cmds==null?tag[Tags.TAGNAME]:cmds+" "+tag[Tags.TAGNAME]);
                    Sender.sendResponse(SpConst.SUCCESS+"Tag \""+tagname+"\" has been added a tag command", event);
                    handler.submitText(Feeds.Type.TAGLOG, event.getGuild(), 
                        "\uD83C\uDFF7 **"+event.getAuthor().getUsername()+"** (ID:"+event.getAuthor().getId()+") imported tag **"+tagname
                                +"** on **"+event.getGuild().getName()+"**");
                    return true;
                }
            }
            Sender.sendResponse(SpConst.ERROR+"Tag \""+tagname+"\" is already a tag command!", event);
            return false;
        }
        private class TagImportList extends Command
        {
            private TagImportList()
            {
                this.command = "list";
                this.help = "lists tag imports on the current server";
                this.longhelp = "This command shows the list of tags that have been imported to the server as commands. "
                        + "There are some tags that are imported by default; you can unimport these if you wish.";
                this.level = PermLevel.MODERATOR;
                this.availableInDM = false;
            }
            @Override
            protected boolean execute(Object[] args, MessageReceivedEvent event)
            {
                String importlist= settings.getSettingsForGuild(event.getGuild().getId())[Settings.TAGIMPORTS];
                if(importlist==null || importlist.equals(""))
                {
                    Sender.sendResponse(SpConst.WARNING+"No tags have been imported on **"+event.getGuild().getName()+"**", event);
                    return true;
                }
                String[] imports = importlist.split("\\s+");
                ArrayList<String> list = new ArrayList<>(Arrays.asList(imports));
                Collections.sort(list);
                StringBuilder builder = new StringBuilder(SpConst.SUCCESS+list.size()+" tag imports on **"+event.getGuild().getName()+"**:\n");
                list.stream().forEach((tag) -> {
                    builder.append(tag).append(" ");
                });
                Sender.sendResponse(builder.toString(), event);
                return true;
            }
        }
    }
    
    private class TagUnimport extends Command
    {
        private TagUnimport()
            {
                this.command = "unimport";
                this.help = "un-imports a tag from a tag command";
                this.longhelp = "This command un-imports a tag from being a pseudo-command.";
                this.arguments = new Argument[]{
                    new Argument("tagname",Argument.Type.SHORTSTRING,true),
                };
                this.level = PermLevel.ADMIN;
                this.availableInDM = false;
            }
        @Override
        protected boolean execute(Object[] args, MessageReceivedEvent event) {
            String tagname = (String)(args[0]);
            String[] imports = Settings.tagCommandsFromList(settings.getSettingsForGuild(event.getGuild().getId())[Settings.TAGIMPORTS]);
            boolean found = false;
            StringBuilder builder = new StringBuilder();
            for(String tag : imports)
            {
                if(tag.equalsIgnoreCase(tagname))
                {
                    found = true;
                    tagname = tag;
                }
                else
                    builder.append(tag).append(" ");
            }
            if(found)
            {
                settings.setSetting(event.getGuild().getId(), Settings.TAGIMPORTS, builder.toString().trim());
                Sender.sendResponse(SpConst.SUCCESS+"Tag \""+tagname+"\" is no longer a tag command", event);
                handler.submitText(Feeds.Type.TAGLOG, event.getGuild(), 
                        "\uD83C\uDFF7 **"+event.getAuthor().getUsername()+"** (ID:"+event.getAuthor().getId()+") unimported tag **"+tagname
                                +"** on **"+event.getGuild().getName()+"**");
                return true;
            }
            Sender.sendResponse(SpConst.ERROR+"Tag \""+tagname+"\" is not currently a tag command!", event);
            return false;
        }
    }
}
