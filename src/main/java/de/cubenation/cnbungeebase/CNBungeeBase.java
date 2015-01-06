package de.cubenation.cnbungeebase;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;

import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.Favicon;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.ServerPing.Protocol;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public class CNBungeeBase extends Plugin implements Listener {
    
    HashMap<String, Integer> serverFails = new HashMap<String, Integer>();
    HashMap<String, Integer> serverCounts = new HashMap<String, Integer>();
    HashMap<String, Integer> serverMaxs = new HashMap<String, Integer>();
    HashMap<String, String> serverMotds = new HashMap<String, String>();
    HashMap<String, Favicon> serverFavIcons = new HashMap<String, Favicon>();
    
    private static String loadingIcon = null;
    private static String offlineIcon = null;
    
    private ScheduledTask task;

    public void onEnable() {
        
        try {
            CNBungeeBase.loadingIcon = "data:image/png;base64," + BaseEncoding.base64().encode( Files.toByteArray( new File( "loading.png" ) ) );
            CNBungeeBase.offlineIcon = "data:image/png;base64," + BaseEncoding.base64().encode( Files.toByteArray( new File( "offline.png" ) ) );
        } catch (IOException e) {
        }
        
        ProxyServer.getInstance().getPluginManager().registerListener(this, this);
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new Lobby());
        ProxyServer.getInstance().registerChannel("cnt_summon");
        
        this.task = ProxyServer.getInstance().getScheduler().schedule(this, new Runnable() {

			@Override
			public void run() {
				System.out.println("Updating Serverinfos ...");

                Map<String, ServerInfo> servers = ProxyServer.getInstance().getServers();
                for (final String server : servers.keySet()) {
                	updateServer(servers.get(server));
                }
                
                System.out.println("----------------");
                System.out.println("Updated Servers:");
                System.out.println("Servercounts:" + serverCounts);
                System.out.println("ServerMotds:" + serverMotds);
                System.out.println("ServerMaxs:" + serverMaxs);
                System.out.println("ServerFails:" + serverFails);
                System.out.println("----------------");

			}
        	
        }, 0, 10, TimeUnit.SECONDS);

    	ProxyServer.getInstance().getPluginManager().registerCommand(this, new Command("sendall", "cnabase.sendall"){

			@Override
			public void execute(CommandSender sender, String[] args) {
				if (args.length != 2) {
					sender.sendMessage("Check documentation!");
					return;
				}
				
				sendPlayersFromTo(args[0], args[1]);
			}
    	});

    }
    
    protected void updateServer(ServerInfo serverInfo) {
    	final String server = serverInfo.getName();
        System.out.println("[ServerPing] " + server + " ...");
    	serverInfo.ping(new Callback<ServerPing>() {

            public void done(ServerPing ping, Throwable arg1) {
                
                if (ping != null) {
                    System.out.println("[ServerPing] " + server + " - Response: [" + ping.getDescription() + " | " + ping.getVersion() + " | " + ping.getPlayers() + "]");
                    
                    if (serverFails.containsKey(server)) {
                        System.out.println("[ServerPing] " + server + " - Finally got a pong with: " + ping);
                    }
                    
                    serverCounts.put(server, ping.getPlayers().getOnline());
                    serverMaxs.put(server, ping.getPlayers().getMax());
                    serverMotds.put(server, ping.getDescription());
                    serverFavIcons.put(server, ping.getFaviconObject());
                    
                    serverFails.remove(server);
                    
                } else {
                    System.out.println("[ServerPing] " + server + " - Failed - Throwable: " + arg1);
 
                    if (serverFails.containsKey(server)) {
                        int last = serverFails.get(server);
                        if (last >= 2) {
                            if (!serverCounts.containsKey(server) || serverCounts.get(server) != -1) {
                                System.out.println("[ServerPing] " + server + " - Failed ping 3 times. Assuming down!");
                                serverCounts.put(server, -1);
                            }
                            
                        } else {
                            System.out.println("[ServerPing] " + server + " - Failed ping " + (last+1) + " times. Checking again...");
                            serverFails.put(server, last+1);
                            
                        }
                        
                    } else {
                        System.out.println("[ServerPing] " + server + " - Failed ping the first time");
                        serverFails.put(server, 1);
                        
                    }
                    
                }
                
//                updatesRunning--;
            }
            
        });
    }

	public void onDisable() {
        task.cancel();
    }

    
	@EventHandler (priority = EventPriority.LOWEST)
    public void onServerKick(ServerKickEvent event) {
        if (ChatColor.stripColor(event.getKickReason()).equalsIgnoreCase("fallback")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPluginMessageEvent (PluginMessageEvent event) {
    	
        if (event.getTag().equals("cnt_summon")) {
        	
            String pluginMessage = new String(event.getData());
            System.out.println(pluginMessage);
            
            // Split the message to get server/gate
            String[] data = pluginMessage.replaceAll("\\[","").replaceAll("\\]","").split("@");
            
        	if (data.length < 2) {
                System.out.println("Faulty plugin message");
                event.setCancelled(true);
            	return;
            }
            String fromServerName = data[0].trim();
            String toServerName = data[1].trim();
            
            sendPlayersFromTo(fromServerName, toServerName);
            
        } else if (event.getTag().equals("cnt_alert")) {
            String pluginMessage = new String(event.getData());

            ProxyServer.getInstance().broadcast(pluginMessage);
        }
    }
    
    private void sendPlayersFromTo(String fromServerName, String toServerName) {
        final ServerInfo toServer = ProxyServer.getInstance().getServerInfo(toServerName);
        ServerInfo fromServer = ProxyServer.getInstance().getServerInfo(fromServerName);

        if (fromServer == null) {
            System.out.println("Requested to send players from '" + fromServerName + "' to '" + toServerName + "', but source server is offline?!");
            return;
        } else if (toServer == null) {
            System.out.println("Requested to send players from '" + fromServerName + "' to '" + toServerName + "', but target server is offline?!");
            return;
        } else if (fromServer == toServer) {
            System.out.println("Requested to send players from and to the same server (" + toServerName + "). Cancelling...");
            return;
        }

        System.out.println("Requested to send players from '" + fromServerName + "' to '" + toServerName + "'");
        for (final ProxiedPlayer pPlayer : fromServer.getPlayers().toArray(new ProxiedPlayer[0])) {

        	// just to be sure
        	if (pPlayer.getServer().getInfo() != fromServer) continue;
        	
            System.out.println("Sending player '" + pPlayer.getDisplayName() + "' from '" + fromServerName + "' to '" + toServerName + "'");
            pPlayer.connect(toServer);
        }		
	}

	@EventHandler
    public void onProxyPing (ProxyPingEvent ev) {

	    /*
        System.out.println("--------");
        System.out.println("Ping back for: " + ev.getConnection().getListener().getDefaultServer());
        System.out.println("hn: " + ev.getConnection().getListener().getHost().getHostName());
        System.out.println("vh: " + ev.getConnection().getVirtualHost().getHostName());
        System.out.println("fb: " + ev.getConnection().getListener().getFallbackServer());
        System.out.println("fh: " + ev.getConnection().getListener().getForcedHosts());
        System.out.println("motd: " + ev.getResponse().getDescription());        
        System.out.println("--------");
        */
        
	    String defaultServer = ev.getConnection().getListener().getDefaultServer();
	    boolean isGlobal = false;

        if (defaultServer.equals("hub")) {
            isGlobal = ev.getConnection().getVirtualHost().getHostName().equalsIgnoreCase("cube-nation.de");
            
            for (ServerInfo info : ProxyServer.getInstance().getServers().values()) {
                for (String modPart : info.getMotd().split(",")) {
                    if (modPart.equalsIgnoreCase(ev.getConnection().getVirtualHost().getHostName())) {
                        defaultServer = info.getName();
                        break;
                    }
                }
                if (!defaultServer.equals("hub")) break;
            }
        }
        Protocol protocol = ev.getResponse().getVersion();
        
        System.out.println("Pingback for: " + ev.getConnection().getVirtualHost().getHostName() + " (" + defaultServer + ")");
        
        if (serverCounts.containsKey(defaultServer)) {
            int playerCount = serverCounts.get(defaultServer);

            if (playerCount != -1) {
                int playerMax = serverMaxs.get(defaultServer);
                String serverMotd = serverMotds.get(defaultServer);
                Favicon favIcon = serverFavIcons.get(defaultServer);
                
                if (isGlobal) {
                    playerCount = ProxyServer.getInstance().getOnlineCount();
                    serverMotd = "\u00A7bDer Server mit Wohlf\u00FChleffekt";
                    playerMax = 100;
                }
                
                ServerPing ping = new ServerPing(
                		protocol,
                        new ServerPing.Players( playerMax, playerCount, null ),
                        serverMotd,
                        favIcon);
                
                ev.setResponse(ping);
            	
            } else {
            	
                ServerPing ping = new ServerPing(
                		protocol,
                        new ServerPing.Players( 0, 0, null ),
                        "§4Wartungsarbeiten / Offline",
                        offlineIcon);
                
                ev.setResponse(ping);

            }
            
        } else {
        	
            ServerPing ping = new ServerPing(
            		protocol,
                    new ServerPing.Players( 0, 0, null ),
                    "§6Serverinfos werden gerade geladen",
                    loadingIcon);
            
            ev.setResponse(ping);

        }

    }
	
	@EventHandler
    public void onPlayerVote(VotifierEvent e){
        Vote v = e.getVote();
        System.out.println("Got Vote: " + v.getAddress() + " | " + v.getServiceName() + " | " + v.getTimeStamp() + " | " + v.getUsername());
        
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        
        try {
            DataOutputStream out = new DataOutputStream(b);
            
            out.writeUTF("Bungeefier"); // Subchannel
            
            ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
            DataOutputStream msgout = new DataOutputStream(msgbytes);
            
            msgout.writeUTF(v.getAddress());
            msgout.writeUTF(v.getServiceName());
            msgout.writeUTF(v.getTimeStamp());
            msgout.writeUTF(v.getUsername());
            
            out.writeShort(msgbytes.toByteArray().length);
            out.write(msgbytes.toByteArray());

        } catch (IOException e1) {
            e1.printStackTrace();
        }
        
                
        for (ServerInfo server : ProxyServer.getInstance().getServers().values()) {
            server.sendData("BungeeCord", b.toByteArray());
        }
        
    }

}