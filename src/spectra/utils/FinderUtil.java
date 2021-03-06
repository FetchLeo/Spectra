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
package spectra.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.JDA;
import net.dv8tion.jda.OnlineStatus;
import net.dv8tion.jda.entities.Guild;
import net.dv8tion.jda.entities.Role;
import net.dv8tion.jda.entities.TextChannel;
import net.dv8tion.jda.entities.User;
import net.dv8tion.jda.entities.impl.JDAImpl;
import net.dv8tion.jda.entities.impl.UserImpl;

/**
 *
 * @author jagrosh
 */
public class FinderUtil {
    
    public final static String USER_MENTION = "<@!?(\\d+)>";
    
    public static List<User> findUsers(String query, JDA jda)
    {
        String id;
        String discrim = null;
        if(query.matches(USER_MENTION))
        {
            id = query.replaceAll(USER_MENTION, "$1");
            if(id.equals("1"))
            {
                UserImpl clyde = new UserImpl("1", (JDAImpl) jda);
                clyde.setDiscriminator("0000");
                clyde.setIsBot(true);
                clyde.setUserName("Clyde");
                clyde.setOnlineStatus(OnlineStatus.ONLINE);
                return Collections.singletonList(clyde);
            }
            User u = jda.getUserById(id);
            if(u!=null)
                return Collections.singletonList(u);
        }
        else if(query.matches("^.*#\\d{4}$"))
        {
            discrim = query.substring(query.length()-4);
            query = query.substring(0,query.length()-5).trim();
        }
        ArrayList<User> exact = new ArrayList<>();
        ArrayList<User> wrongcase = new ArrayList<>();
        ArrayList<User> startswith = new ArrayList<>();
        ArrayList<User> contains = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for(User u: jda.getUsers())
        {
            if(discrim!=null && !u.getDiscriminator().equals(discrim))
                continue;
            if(u.getUsername().equals(query))
                exact.add(u);
            else if (exact.isEmpty() && u.getUsername().equalsIgnoreCase(query))
                wrongcase.add(u);
            else if (wrongcase.isEmpty() && u.getUsername().toLowerCase().startsWith(lowerQuery))
                startswith.add(u);
            else if (startswith.isEmpty() && u.getUsername().toLowerCase().contains(lowerQuery))
                contains.add(u);
        }
        if(!exact.isEmpty())
            return exact;
        if(!wrongcase.isEmpty())
            return wrongcase;
        if(!startswith.isEmpty())
            return startswith;
        return contains;
    }
    
    public static List<User> findUsers(String query, Guild guild)
    {
        String id;
        String discrim = null;
        if(query.matches(USER_MENTION))
        {
            id = query.replaceAll(USER_MENTION, "$1");
            User u = guild.getJDA().getUserById(id);
            if(guild.isMember(u))
                return Collections.singletonList(u);
        }
        else if(query.matches("^.*#\\d{4}$"))
        {
            discrim = query.substring(query.length()-4);
            query = query.substring(0,query.length()-5).trim();
        }
        ArrayList<User> exact = new ArrayList<>();
        ArrayList<User> wrongcase = new ArrayList<>();
        ArrayList<User> startswith = new ArrayList<>();
        ArrayList<User> contains = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for(User u: guild.getUsers())
        {
            String nickname = guild.getNicknameForUser(u);
            if(discrim!=null && !u.getDiscriminator().equals(discrim))
                continue;
            if(u.getUsername().equals(query) || (nickname!=null && nickname.equals(query)))
                exact.add(u);
            else if (exact.isEmpty() && (u.getUsername().equalsIgnoreCase(query) || (nickname!=null && nickname.equalsIgnoreCase(query))))
                wrongcase.add(u);
            else if (wrongcase.isEmpty() && (u.getUsername().toLowerCase().startsWith(lowerQuery) || (nickname!=null && nickname.toLowerCase().startsWith(lowerQuery))))
                startswith.add(u);
            else if (startswith.isEmpty() && (u.getUsername().toLowerCase().contains(lowerQuery) || (nickname!=null && nickname.toLowerCase().contains(lowerQuery))))
                contains.add(u);
        }
        if(!exact.isEmpty())
            return exact;
        if(!wrongcase.isEmpty())
            return wrongcase;
        if(!startswith.isEmpty())
            return startswith;
        return contains;
    }
    
    public static List<TextChannel> findTextChannel(String query, Guild guild)
    {
        String id;
        if(query.matches("<#\\d+>"))
        {
            id = query.replaceAll("<#(\\d+)>", "$1");
            TextChannel tc = guild.getJDA().getTextChannelById(id);
            if(tc!=null && tc.getGuild().equals(guild))
                return Collections.singletonList(tc);
        }
        ArrayList<TextChannel> exact = new ArrayList<>();
        ArrayList<TextChannel> wrongcase = new ArrayList<>();
        ArrayList<TextChannel> startswith = new ArrayList<>();
        ArrayList<TextChannel> contains = new ArrayList<>();
        String lowerquery = query.toLowerCase();
        guild.getTextChannels().stream().forEach((tc) -> {
            if(tc.getName().equals(lowerquery))
                exact.add(tc);
            else if (tc.getName().equalsIgnoreCase(lowerquery) && exact.isEmpty())
                wrongcase.add(tc);
            else if (tc.getName().toLowerCase().startsWith(lowerquery) && wrongcase.isEmpty())
                startswith.add(tc);
            else if (tc.getName().toLowerCase().contains(lowerquery) && startswith.isEmpty())
                contains.add(tc);
        });
        if(!exact.isEmpty())
            return exact;
        if(!wrongcase.isEmpty())
            return wrongcase;
        if(!startswith.isEmpty())
            return startswith;
        return contains;
    }
    
    public static List<Role> findRole(String query, List<Role> roles)
    {
        String id;
        if(query.matches("<@&\\d+>"))
        {
            id = query.replaceAll("<@&(\\d+)>", "$1");
            for(Role role: roles)
                if(role.getId().equals(id))
                    return Collections.singletonList(role);
        }
        if(query.matches("[Ii][Dd]\\s*:\\s*\\d+"))
        {
            id = query.replaceAll("[Ii][Dd]\\s*:\\s*(\\d+)", "$1");
            for(Role role: roles)
                if(role.getId().equals(id))
                    return Collections.singletonList(role);
        }
        ArrayList<Role> exact = new ArrayList<>();
        ArrayList<Role> wrongcase = new ArrayList<>();
        ArrayList<Role> startswith = new ArrayList<>();
        ArrayList<Role> contains = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        roles.stream().forEach((role) -> {
            if(role.getName().equals(query))
                exact.add(role);
            else if (role.getName().equalsIgnoreCase(query) && exact.isEmpty())
                wrongcase.add(role);
            else if (role.getName().toLowerCase().startsWith(lowerQuery) && wrongcase.isEmpty())
                startswith.add(role);
            else if (role.getName().toLowerCase().contains(lowerQuery) && startswith.isEmpty())
                contains.add(role);
        });
        if(!exact.isEmpty())
            return exact;
        if(!wrongcase.isEmpty())
            return wrongcase;
        if(!startswith.isEmpty())
            return startswith;
        return contains;
    }
}
