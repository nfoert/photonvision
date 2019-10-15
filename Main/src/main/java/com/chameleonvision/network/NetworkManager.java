package com.chameleonvision.network;


import com.chameleonvision.settings.ConnectionType;
import com.chameleonvision.settings.NetworkSettings;
import com.chameleonvision.settings.Platform;
import com.chameleonvision.settings.SettingsManager;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NetworkManager {
	private NetworkManager() {}

	protected static SysNetworking networking;
	protected static NetworkInterface botInterface = null;
	private static boolean isManaged = false;

	public static void initialize(boolean manage) {
		isManaged = manage;
		if (!isManaged) {
			return;
		}

		Platform platform = Platform.getCurrentPlatform();

		if (platform.isLinux()) {
			networking = new LinuxNetworking();
		} else if (platform.isWindows()) {
//			networking = new WindowsNetworking();
            System.out.println("Windows networking is not yet supported. Running unmanaged.");
            return;
		}

		if (networking == null) {
			throw new RuntimeException("Failed to detect platform!");
		}

		List<java.net.NetworkInterface> interfaces = new ArrayList<>();
		List<NetworkInterface> goodInterfaces = new ArrayList<>();

		try {
			interfaces = networking.getNetworkInterfaces();
		} catch (SocketException e) {
			e.printStackTrace();
		}

        var teamBytes = NetworkSettings.GetTeamNumberIPBytes(SettingsManager.GeneralSettings.teamNumber);

        if (interfaces.size() > 0) {
			for (var inetface : interfaces) {
                for (var inetfaceAddr : inetface.getInterfaceAddresses()) {
                    var rawAddr = inetfaceAddr.getAddress().getAddress();
                    if (rawAddr.length > 4) continue;
                    if (rawAddr[1] == teamBytes[0] && rawAddr[2] == teamBytes[1]) {
                        goodInterfaces.add(new NetworkInterface(inetface, inetfaceAddr));
                    }
                }
			}

			if (goodInterfaces.size() == 0) {
				isManaged = false;
				System.err.println("No valid network interfaces found! Staying unmanaged.");
				return;
			}

			botInterface = goodInterfaces.get(0);
			networking.setNetworkInterface(botInterface);
		} else {
        	isManaged = false;
			System.err.println("No valid network interfaces found! Staying unmanaged.");
			return;
		}

		if(!loadFromGeneralSettings()) {
			isManaged = false;
			System.err.println("Failed to load network settings. Staying unmanaged!");
		}
	}

	private static boolean loadFromGeneralSettings() {
		if (!isManaged) {
			return true;
		}

		var genSettings = SettingsManager.GeneralSettings;
		boolean isStatic = genSettings.connectionType.equals(ConnectionType.Static);

		if (isStatic) {
			var splitIPAddr = genSettings.ip.split("\\.");
			splitIPAddr[3] = "255";
			var broadcast = String.join(".", splitIPAddr);
			if (!setStatic(genSettings.ip, genSettings.netmask, genSettings.gateway, broadcast)) {
				return false;
			}
		} else {
			if (!setDHCP()) {
				return false;
			}
		}

		return setHostname(genSettings.hostname);
	}

	private static boolean setDHCP() {
		if (!isManaged) {
			return true;
		}
	    return networking.setDHCP();
    }

    private static boolean setStatic(String ipAddress, String netmask, String gateway, String broadcast) {
		if (!isManaged) {
			return true;
		}
	    return networking.setStatic(ipAddress, netmask, gateway, broadcast);
    }

    private static boolean setHostname(String hostname) {
		if (!isManaged) {
			return true;
		}
	    return networking.setHostname(hostname);
    }
}
