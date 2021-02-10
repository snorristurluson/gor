/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2019 WuXi NextCode Inc.
 *  All Rights Reserved.
 *
 *  GORpipe is free software: you can redistribute it and/or modify
 *  it under the terms of the AFFERO GNU General Public License as published by
 *  the Free Software Foundation.
 *
 *  GORpipe is distributed "AS-IS" AND WITHOUT ANY WARRANTY OF ANY KIND,
 *  INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 *  NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE. See
 *  the AFFERO GNU General Public License for the complete license terms.
 *
 *  You should have received a copy of the AFFERO GNU General Public License
 *  along with GORpipe.  If not, see <http://www.gnu.org/licenses/agpl-3.0.html>
 *
 *  END_COPYRIGHT
 */

package org.gorpipe.gor.driver.providers.stream.sources.http;

import org.gorpipe.gor.driver.GorDriverConfig;
import org.gorpipe.gor.driver.adapters.PositionAwareInputStream;
import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.driver.meta.SourceType;
import org.gorpipe.gor.driver.providers.stream.RequestRange;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSource;
import org.gorpipe.gor.driver.providers.stream.sources.StreamSourceMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.gorpipe.gor.driver.providers.stream.StreamUtils.tryClose;

/**
 * Represent a data source accessible through a HTTP file/url
 * <p>
 * Created by villi on 21/08/15.
 */
public class HTTPSource implements StreamSource {
    private static final Logger log = LoggerFactory.getLogger(HTTPSource.class);

    private final URL url;
    private final SourceReference sourceReference;
    private StreamSourceMetadata sourceMetadata;

    private final GorDriverConfig config;
    private Boolean exists;
    private Long length;

    /**
     * Create HTTP source
     *
     * @param source A standard http or https url.
     */
    public HTTPSource(GorDriverConfig config, SourceReference source) throws IOException {
        this.sourceReference = source;
        this.url = new URL(sourceReference.getUrl());
        this.config = config;
    }

    @Override
    public String getName() {
        return sourceReference.getUrl();
    }

    @Override
    public InputStream open() throws IOException {
        return open(0);
    }

    @Override
    public InputStream open(long start) throws IOException {
        return open(RequestRange.fromFirstLength(start, length()));
    }

    @Override
    public InputStream open(long start, long minLength) throws IOException {
        return open(RequestRange.fromFirstLength(start, minLength));
    }

    // TODO: Check for actual range returned
    // It may be less than requested in which case read beyond the range will hang until timeout.
    // We can guard against that by wrapping the stream with one that knows the actual range and will prevent seeks beyond the limit.
    private InputStream open(RequestRange range) throws IOException {
        log.debug("HTTP Stream open: {}", range);
        HttpURLConnection urlc = createBaseUrlConnection();
        log.debug("HTTP request: {}", getName());
        if (range != null) {
            range = range.limitTo(length());
            if (range.isEmpty()) return new ByteArrayInputStream(new byte[0]);
            urlc.setRequestProperty("Range", "bytes=" + range.getFirst() + "-" + range.getLast());
            log.debug("Range: {}", range);
        }

        InputStream stream = urlc.getInputStream();
        try {
            if (urlc.getResponseCode() == HttpURLConnection.HTTP_OK) {
                if (!is200ResponseOk(range)) {
                    throw new IOException("Server did not return partial content for ranged request.  url:" + getName() + ", range:" + range + ", response code:" + urlc.getResponseCode());
                }
            } else if (urlc.getResponseCode() == HttpURLConnection.HTTP_PARTIAL) {
                if (!isPartialResponseOk(range)) {
                    throw new IOException("Expected 200 ok for non ranged request to:" + getName() + ", response code:" + urlc.getResponseCode());
                }
            } else {
                throw new IOException("Unexpected response from " + getName() + ", response code:" + urlc.getResponseCode() + " range: " + range);
            }
        } catch (final Throwable t) {
            tryClose(stream);
            throw t;
        }
        if (config.disconnectHttpStream()) {
            log.debug("Wrapping http stream with auto disconnecting stream");
            return new AutoDisconnectingInputStream(stream, urlc);
        }
        return stream;
    }

    /**
     * If the range requested is from the beginning of the file - then, regardless of the end range
     * some server will serve the whole file.  So in that case we'll get a 200 response
     */
    private boolean is200ResponseOk(RequestRange range) {
        return range == null || range.getFirst() == 0;
    }

    private boolean isPartialResponseOk(RequestRange range) {
        return range != null;
    }

    @Override
    public StreamSourceMetadata getSourceMetadata() throws IOException {
        if (sourceMetadata == null && exists == null) {
            HttpURLConnection urlc = createBaseUrlConnection();
            log.debug("Reading source metadata from {} using HEAD", url);
            try {
                urlc.setRequestMethod("HEAD");
                urlc.connect();
                int responseCode = urlc.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // If the response is "HTTP_OK" the exists is set to true and source metadata is returned
                    exists = true;
                    Long modified = urlc.getLastModified();
                    if (modified <= 0) {
                        modified = null;
                    }
                    long contentLength = getContentLength(urlc);
                    sourceMetadata = new StreamSourceMetadata(this, url.toString(), modified, contentLength, null, false);
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    // If the status is "Not Found" then exists is set to false and a file not found exception is thrown
                    exists = false;
                    throw new FileNotFoundException(url.toString());
                } else {
                    // For status not "Ok" or "Not Found" throw a new IOException
                    throw new IOException(url.toString());
                }
            } finally {
                urlc.disconnect();
            }
        }
        return sourceMetadata;
    }

    @Override
    public SourceReference getSourceReference() {
        return sourceReference;
    }

    /**
     * Some servers return content length that HttpURLConnection.getContentLength does not handle.
     */
    private long getContentLength(HttpURLConnection c) {
        long len = 0;
        Map<String, List<String>> hf = c.getHeaderFields();
        List<String> values = hf.get("Content-Length");
        if (values == null) {
            values = c.getHeaderFields().get("content-length");
        }
        if (values == null) return c.getContentLength();
        String sLength = values.get(0);
        if (sLength != null) {
            len = Long.parseLong(sLength);
        }
        return len;
    }

    @Override
    public SourceType getSourceType() {
        return HTTPSourceType.HTTP;
    }

    @Override
    public DataType getDataType() {
        return DataType.fromFileName(url.getFile());
    }

    @Override
    public boolean exists() throws IOException {
        if (exists == null) {
            try {
                getSourceMetadata();
            } catch (FileNotFoundException e) {
                // Ok
            }
        }
        return exists;
    }

    @Override
    public void close() {
        // No resources to free
    }

    private HttpURLConnection createBaseUrlConnection() throws IOException {
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setUseCaches(false);
        http.setInstanceFollowRedirects(true);
        http.setDoInput(true);
        http.setDoOutput(false);
        http.setConnectTimeout(120 * 1000);
        http.setReadTimeout(120 * 1000);
        http.setAllowUserInteraction(false);
        return http;
    }

    /**
     * Automatically calls disconnect on urlconnection when stream is closed.
     * May help free up resources.
     */
    private static class AutoDisconnectingInputStream extends PositionAwareInputStream {
        private final HttpURLConnection urlConnection;

        AutoDisconnectingInputStream(InputStream in, HttpURLConnection urlc) {
            super(in);
            this.urlConnection = urlc;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                disconnect();
            }
        }

        public int available() throws IOException {
            int estimate = super.available();
            return estimate == 0 ? 1 : estimate;
        }

        private void disconnect() {
            try {
                urlConnection.disconnect();
            } catch (Exception e) {
                log.warn("Got exception in url connection disconnect on {}: {}", urlConnection, e.getMessage(), e);
            }
        }
    }

    private Long length() throws IOException {
        if (length == null) {
            length = getSourceMetadata().getLength();
        }
        return length;
    }

    /**
     * If the length of the object is known beforehand - allow setting it here, making an extra HEAD request unnecessary
     */
    public void setLength(Long length) {
        this.length = length;
    }
}
