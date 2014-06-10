package org.lantern.geoip;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.lantern.JsonUtils;
import org.lantern.LanternUtils;
import org.lantern.http.HttpUtils;
import org.lantern.util.StaticHttpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;

/**
 * Service for GeoLocating. Uses MaxMind's {@link LookupService} internally.
 */
@Singleton
public class GeoIpLookupService {

    private static final Logger LOG = LoggerFactory
            .getLogger(GeoIpLookupService.class);

    private final Map<String, GeoData> cache =
            new ConcurrentHashMap<String, GeoData>();

    private static final String geoServeUrl = "http://geo.getiantem.org/lookup/";

    public GeoData getGeoData(final InetAddress ipAddress) {
        return getGeoData(ipAddress.getHostAddress());
    }
    
    public GeoData getGeoData(String ipAddress) {
        GeoData result = cache.get(ipAddress);
        if (result == null) {
            result = this.queryGeoServe(ipAddress);
            cache.put(ipAddress, result);
        }
        return result;
    }

    private GeoData queryGeoServe(final String ipAddress) {
        final HttpClient proxied = LanternUtils.isFallbackProxy() ?
                StaticHttpClientFactory.newDirectClient() :
                StaticHttpClientFactory.newProxiedClient();
        final HttpGet get = new HttpGet(geoServeUrl + ipAddress);
        InputStream is = null;
        try {
            final HttpResponse response = proxied.execute(get);
            final int status = response.getStatusLine().getStatusCode();
            if (status != 200) {
                LOG.error(
                        "Error on proxied request. No proxies working? {}, {}",
                        response.getStatusLine(),
                        HttpUtils.httpHeaders(response));
                return new GeoData();
            }
            is = response.getEntity().getContent();
            final String geoStr = IOUtils.toString(is);
            LOG.debug("Geo lookup response " + geoStr);
            return JsonUtils.OBJECT_MAPPER.readValue(geoStr, GeoData.class);
        } catch (ClientProtocolException e) {
            LOG.warn("Error connecting to geo lookup service " +
                    e.getMessage(), e);
        } catch (IOException e) {
            LOG.warn("Error parsing JSON from geo lookup " +
                    e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(is);
            get.reset();
        }
        return new GeoData();
    }
}
