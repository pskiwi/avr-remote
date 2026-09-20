/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.pskiwi.avrremote.scan;

import java.net.Inet4Address;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import de.pskiwi.avrremote.log.Logger;

/**
 * Einzige Quelle für den Zustand des lokalen Netzes: ob ein WLAN da ist, welches
 * {@link Network} dazu gehört und welche IPv4-Adresse das Gerät darin hat.
 *
 * Löst WiFiInfo und IFConfig ab. WiFiInfo hing an getDhcpInfo() und
 * getAllNetworks() - beide seit API 31 deprecated -, IFConfig rief als Notnagel
 * "ifconfig" über Runtime.exec() auf, was auf keiner unterstützten
 * Android-Version mehr etwas liefert. Beides beantwortet LinkProperties direkt,
 * und zwar auch außerhalb von DHCP, wo getDhcpInfo() nur Nullen zurückgab.
 *
 * Der zweite Grund für die Klasse: unter Local Network Protection (Pflicht ab
 * Android 17) muss jeder Socket ins lokale Netz an genau dieses Network gebunden
 * werden, nicht nur der Suchlauf. getNetwork() ist die Quelle dafür.
 */
public final class LocalNetwork {

	/** Wechsel des WLAN-Zustands, gemeldet auf dem Main-Thread. */
	public interface IWiFiListener {
		void wifiChanged(boolean connected);
	}

	public LocalNetwork(Context ctx) {
		this.connectivity = (ConnectivityManager) ctx
				.getSystemService(Context.CONNECTIVITY_SERVICE);
	}

	/**
	 * Einmalig beim Start registrieren.
	 *
	 * Bewusst ohne addCapability(NET_CAPABILITY_INTERNET): NetworkRequest.Builder
	 * setzt per Default nur NOT_RESTRICTED/TRUSTED/NOT_VPN, und
	 * registerNetworkCallback meldet alle passenden Netze, nicht nur das
	 * Default-Netz. Damit bleibt genau die Eigenschaft erhalten, die der alte
	 * Zwei-Schritt aus getActiveNetwork() + getAllNetworks() mühsam nachgebildet
	 * hat: ein WLAN ohne Internet - Router mit totem WAN, Captive Portal, bewusst
	 * isoliertes AV-Netz - zählt neben aktivem Mobilfunk als verbunden. Der
	 * Receiver ist dann da, auch wenn das WLAN nicht das Default-Netz ist.
	 *
	 * Der Listener läuft über den Main-Handler, weil der abgelöste
	 * BroadcastReceiver in onReceive() auch auf dem Main-Thread lief und
	 * EnableManager.setStatus() ein unsynchronisiertes read-modify-write ist. Die
	 * Handler-Überladung von registerNetworkCallback() wäre der direktere Weg,
	 * ist aber erst API 26 und damit oberhalb von minSdk 24 - handler.post() im
	 * Callback leistet dasselbe ohne Versionsweiche.
	 */
	public void register(Handler handler, IWiFiListener listener) {
		this.handler = handler;
		this.listener = listener;
		final NetworkRequest request = new NetworkRequest.Builder()
				.addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
		connectivity.registerNetworkCallback(request, callback);
	}

	/** Ist ein WLAN mit brauchbarer IPv4-Adresse da ? */
	public boolean isConnected() {
		return network != null && getIPv4() != null;
	}

	/** Das WLAN, an das Sockets zu binden sind, oder null. */
	public Network getNetwork() {
		return network;
	}

	/** Eigene IPv4-Adresse samt Prefix-Länge, oder null. */
	public LinkAddress getIPv4() {
		final LinkProperties properties = linkProperties;
		if (properties == null) {
			return null;
		}
		for (LinkAddress address : properties.getLinkAddresses()) {
			if (address.getAddress() instanceof Inet4Address) {
				return address;
			}
		}
		return null;
	}

	/** "wlan0" und ähnlich, nur für das Log. */
	public String getInterfaceName() {
		final LinkProperties properties = linkProperties;
		return properties == null ? null : properties.getInterfaceName();
	}

	public String getErrorCause() {
		if (network == null) {
			return "WiFi not connected";
		}
		if (getIPv4() == null) {
			return "no IPv4 address";
		}
		return "no error";
	}

	@Override
	public String toString() {
		final LinkAddress address = getIPv4();
		return "LocalNetwork [" + getInterfaceName() + "] "
				+ (address == null ? "no address" : address.getAddress()
						.getHostAddress() + "/" + address.getPrefixLength());
	}

	/**
	 * Mehrere gleichzeitige WLAN-Netze sind praktisch ausgeschlossen, deshalb
	 * gewinnt schlicht das zuletzt verfügbare, und onLost räumt nur auf, wenn es
	 * das gehaltene Netz trifft.
	 */
	private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {

		@Override
		public void onAvailable(Network n) {
			network = n;
			linkProperties = connectivity.getLinkProperties(n);
			notifyListener(true);
		}

		@Override
		public void onLinkPropertiesChanged(Network n, LinkProperties lp) {
			if (n.equals(network)) {
				linkProperties = lp;
			}
		}

		@Override
		public void onLost(Network n) {
			if (n.equals(network)) {
				network = null;
				linkProperties = null;
				notifyListener(false);
			}
		}
	};

	private void notifyListener(final boolean connected) {
		Logger.info("LocalNetwork: WiFi " + (connected ? "available" : "lost")
				+ " " + this);
		final Handler h = handler;
		final IWiFiListener l = listener;
		if (h == null || l == null) {
			return;
		}
		h.post(new Runnable() {
			public void run() {
				l.wifiChanged(connected);
			}
		});
	}

	private final ConnectivityManager connectivity;
	// Auf dem Binder-Thread des Callbacks geschrieben, von jedem Thread gelesen.
	private volatile Network network;
	private volatile LinkProperties linkProperties;
	private volatile Handler handler;
	private volatile IWiFiListener listener;
}
