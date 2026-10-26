package cl.uss.ca.external.dspace;

import java.io.InputStream;

public final class DspaceBitstreamContent implements AutoCloseable {
    public final InputStream stream;
    public final String contentType;
    public final Long contentLength;

    public DspaceBitstreamContent(InputStream stream, String contentType, Long contentLength) {
        this.stream = stream;
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    @Override
    public void close() throws Exception {
        if (stream != null) {
            stream.close();
        }
    }
}
