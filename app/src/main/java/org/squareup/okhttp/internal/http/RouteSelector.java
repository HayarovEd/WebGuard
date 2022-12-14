/*
 * Copyright (C) 2012 Square, Inc.
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
package org.squareup.okhttp.internal.http;

import org.squareup.okhttp.Address;
import org.squareup.okhttp.CertificatePinner;
import org.squareup.okhttp.Connection;
import org.squareup.okhttp.ConnectionSpec;
import org.squareup.okhttp.ConnectionPool;
import org.squareup.okhttp.OkHttpClient;
import org.squareup.okhttp.Request;
import org.squareup.okhttp.Route;
import org.squareup.okhttp.internal.Internal;
import org.squareup.okhttp.internal.Network;
import org.squareup.okhttp.internal.RouteDatabase;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSocketFactory;
import org.squareup.okio.VpnSocketEnumerator;

import static org.squareup.okhttp.internal.Util.getEffectivePort;

/**
 * Selects routes to connect to an origin server. Each connection requires a
 * choice of proxy server, IP address, and TLS mode. Connections may also be
 * recycled.
 */
public final class RouteSelector {
  private final Address address;
  private final URI uri;
  private final Network network;
  private final OkHttpClient client;
  private final ProxySelector proxySelector;
  private final ConnectionPool pool;
  private final RouteDatabase routeDatabase;
  private final Request request;

  /* The most recently attempted route. */
  private Proxy lastProxy;
  private InetSocketAddress lastInetSocketAddress;
  private ConnectionSpec lastSpec;

  /* State for negotiating the next proxy to use. */
  private List<Proxy> proxies = Collections.emptyList();
  private int nextProxyIndex;

  /* State for negotiating the next socket address to use. */
  private List<InetSocketAddress> inetSocketAddresses = Collections.emptyList();
  private int nextInetSocketAddressIndex;

  /* Specs to attempt with the connection. */
  private List<ConnectionSpec> connectionSpecs = Collections.emptyList();
  private int nextSpecIndex;

  /* State for negotiating failed routes */
  private final List<Route> postponedRoutes = new ArrayList<Route>();

  private RouteSelector(Address address, URI uri, OkHttpClient client, Request request) {
    this.address = address;
    this.uri = uri;
    this.client = client;
    this.proxySelector = client.getProxySelector();
    this.pool = client.getConnectionPool();
    this.routeDatabase = Internal.instance.routeDatabase(client);
    this.network = Internal.instance.network(client);
    this.request = request;

    resetNextProxy(uri, address.getProxy());
  }

  public static RouteSelector get(Request request, OkHttpClient client) throws IOException {
    String uriHost = request.url().getHost();
    if (uriHost == null || uriHost.length() == 0) {
      throw new UnknownHostException(request.url().toString());
    }

    SSLSocketFactory sslSocketFactory = null;
    HostnameVerifier hostnameVerifier = null;
    CertificatePinner certificatePinner = null;
    if (request.isHttps()) {
      sslSocketFactory = client.getSslSocketFactory();
      hostnameVerifier = client.getHostnameVerifier();
      certificatePinner = client.getCertificatePinner();
    }

    Address address = new Address(uriHost, getEffectivePort(request.url()),
        client.getSocketFactory(), sslSocketFactory, hostnameVerifier, certificatePinner,
        client.getAuthenticator(), client.getProxy(), client.getProtocols(),
        client.getConnectionSpecs());

    return new RouteSelector(address, request.uri(), client, request);
  }

  /**
   * Returns true if there's another route to attempt. Every address has at
   * least one route.
   */
  public boolean hasNext() {
    return hasNextConnectionSpec()
        || hasNextInetSocketAddress()
        || hasNextProxy()
        || hasNextPostponed();
  }

  /** Selects a route to attempt and connects it if it isn't already. */
  public Connection next(HttpEngine owner) throws IOException {
    Connection connection = nextUnconnected();
    Internal.instance.connectAndSetOwner(client, connection, owner, request);
    return connection;
  }

  /**
   * Returns the next connection to attempt.
   *
   * @throws NoSuchElementException if there are no more routes to attempt.
   */
  Connection nextUnconnected() throws IOException {
    // Always prefer pooled connections over new connections.
    for (Connection pooled; (pooled = pool.get(address)) != null; ) {
      if (request.method().equals("GET") || Internal.instance.isReadable(pooled)) return pooled;
      pooled.getSocket().close();
      VpnSocketEnumerator.unregisterSocket(pooled.getSocket());
    }

    // Compute the next route to attempt.
    if (!hasNextConnectionSpec()) {
      if (!hasNextInetSocketAddress()) {
        if (!hasNextProxy()) {
          if (!hasNextPostponed()) {
            throw new NoSuchElementException();
          }
          return new Connection(pool, nextPostponed());
        }
        lastProxy = nextProxy();
      }
      lastInetSocketAddress = nextInetSocketAddress();
    }
    lastSpec = nextConnectionSpec();

    Route route = new Route(address, lastProxy, lastInetSocketAddress, lastSpec);
    if (routeDatabase.shouldPostpone(route)) {
      postponedRoutes.add(route);
      // We will only recurse in order to skip previously failed routes. They will be tried last.
      return nextUnconnected();
    }

    return new Connection(pool, route);
  }

  /**
   * Clients should invoke this method when they encounter a connectivity
   * failure on a connection returned by this route selector.
   */
  public void connectFailed(Connection connection, IOException failure) {
    // If this is a recycled connection, don't count its failure against the route.
    if (Internal.instance.recycleCount(connection) > 0) return;

    Route failedRoute = connection.getRoute();
    if (failedRoute.getProxy().type() != Proxy.Type.DIRECT && proxySelector != null) {
      // Tell the proxy selector when we fail to connect on a fresh connection.
      proxySelector.connectFailed(uri, failedRoute.getProxy().address(), failure);
    }

    routeDatabase.failed(failedRoute);

    // If the previously returned route's problem was not related to the connection's spec, and the
    // next route only changes that, we shouldn't even attempt it. This suppresses it in both this
    // selector and also in the route database.
    if (!(failure instanceof SSLHandshakeException) && !(failure instanceof SSLProtocolException)) {
      while (nextSpecIndex < connectionSpecs.size()) {
        Route toSuppress = new Route(address, lastProxy, lastInetSocketAddress,
            connectionSpecs.get(nextSpecIndex++));
        routeDatabase.failed(toSuppress);
      }
    }
  }

  /** Prepares the proxy servers to try. */
  private void resetNextProxy(URI uri, Proxy proxy) {
    if (proxy != null) {
      // If the user specifies a proxy, try that and only that.
      proxies = Collections.singletonList(proxy);
    } else {
      // Try each of the ProxySelector choices until one connection succeeds. If none succeed
      // then we'll try a direct connection below.
      proxies = new ArrayList<Proxy>();
      List<Proxy> selectedProxies = proxySelector.select(uri);
      if (selectedProxies != null) proxies.addAll(selectedProxies);
      // Finally try a direct connection. We only try it once!
      proxies.removeAll(Collections.singleton(Proxy.NO_PROXY));
      proxies.add(Proxy.NO_PROXY);
    }
    nextProxyIndex = 0;
  }

  /** Returns true if there's another proxy to try. */
  private boolean hasNextProxy() {
    return nextProxyIndex < proxies.size();
  }

  /** Returns the next proxy to try. May be PROXY.NO_PROXY but never null. */
  private Proxy nextProxy() throws IOException {
    if (!hasNextProxy()) {
      throw new SocketException("No route to " + address.getUriHost()
          + "; exhausted proxy configurations: " + proxies);
    }
    Proxy result = proxies.get(nextProxyIndex++);
    resetNextInetSocketAddress(result);
    return result;
  }

  /** Prepares the socket addresses to attempt for the current proxy or host. */
  private void resetNextInetSocketAddress(Proxy proxy) throws UnknownHostException {
    // Clear the addresses. Necessary if getAllByName() below throws!
    inetSocketAddresses = new ArrayList<InetSocketAddress>();

    String socketHost;
    int socketPort;
    if (proxy.type() == Proxy.Type.DIRECT) {
      String hostIp = client.getHostIp();
      //socketHost = address.getUriHost();
      socketHost = (hostIp != null) ? hostIp : address.getUriHost();
      socketPort = getEffectivePort(uri);
    } else {
      SocketAddress proxyAddress = proxy.address();
      if (!(proxyAddress instanceof InetSocketAddress)) {
        throw new IllegalArgumentException(
            "Proxy.address() is not an " + "InetSocketAddress: " + proxyAddress.getClass());
      }
      InetSocketAddress proxySocketAddress = (InetSocketAddress) proxyAddress;
      socketHost = proxySocketAddress.getHostName();
      socketPort = proxySocketAddress.getPort();
    }

    // Try each address for best behavior in mixed IPv4/IPv6 environments.
    for (InetAddress inetAddress : network.resolveInetAddresses(socketHost)) {
      inetSocketAddresses.add(new InetSocketAddress(inetAddress, socketPort));
    }
    nextInetSocketAddressIndex = 0;
  }

  /** Returns true if there's another socket address to try. */
  private boolean hasNextInetSocketAddress() {
    return nextInetSocketAddressIndex < inetSocketAddresses.size();
  }

  /** Returns the next socket address to try. */
  private InetSocketAddress nextInetSocketAddress() throws IOException {
    if (!hasNextInetSocketAddress()) {
      throw new SocketException("No route to " + address.getUriHost()
          + "; exhausted inet socket addresses: " + inetSocketAddresses);
    }
    InetSocketAddress result = inetSocketAddresses.get(nextInetSocketAddressIndex++);
    resetConnectionSpecs();
    return result;
  }

  /** Prepares the connection specs to attempt. */
  private void resetConnectionSpecs() {
    connectionSpecs = new ArrayList<ConnectionSpec>();
    for (ConnectionSpec spec : address.getConnectionSpecs()) {
      if (request.isHttps() == spec.isTls()) {
        connectionSpecs.add(spec);
      }
    }
    nextSpecIndex = 0;
  }

  /** Returns true if there's another connection spec to try. */
  private boolean hasNextConnectionSpec() {
    return nextSpecIndex < connectionSpecs.size();
  }

  /** Returns the next connection spec to try. */
  private ConnectionSpec nextConnectionSpec() throws IOException {
    if (!hasNextConnectionSpec()) {
      throw new SocketException("No route to " + address.getUriHost()
          + "; exhausted connection specs: " + connectionSpecs);
    }
    return connectionSpecs.get(nextSpecIndex++);
  }

  /** Returns true if there is another postponed route to try. */
  private boolean hasNextPostponed() {
    return !postponedRoutes.isEmpty();
  }

  /** Returns the next postponed route to try. */
  private Route nextPostponed() {
    return postponedRoutes.remove(0);
  }
}
