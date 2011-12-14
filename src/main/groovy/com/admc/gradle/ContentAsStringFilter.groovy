package com.admc.gradle;

class ContentAsStringFilter extends FilterReader {
private static int counter = 0;
    private StringReader stringReader;
    private def stringToStringClosure;

    ContentAsStringFilter(Reader inR) { super(inR); }

    public void setClosure(closure) { this.stringToStringClosure = closure; }

    void initIfNeeded() {
        try  {
            if (stringReader == null) initSource();
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    void initSource() throws IOException {
        if (stringReader != null)
            throw new IllegalStateException(
                    'ContentAsStringFilter already initialized');
        if (stringToStringClosure == null)
            throw new IllegalStateException(
                    "'closure' property (a String-to-String closure) not set");
        StringWriter stringWriter = new StringWriter();
        char[] buffer = new char[10240];
        int i;
        while ((i = super.read(buffer, 0, buffer.length)) > 0)
            stringWriter.write(buffer, 0, i);
        stringReader = new StringReader(
                stringToStringClosure(stringWriter.toString()));
    }

    // Implementation of java.io.Reader
    int read(char[] cbuf) throws IOException {
        return read(cbuf, 0, cbuf.length);
    }
    int read(char[] cbuf, int off, int len) throws IOException {
        initIfNeeded();
        return stringReader.read(cbuf, off, len);
    }
    // Implementation of java.io.FilterReader
    void close() throws IOException {
        initIfNeeded();
        stringReader.close();
    }
    void mark(int readAheadLimit) throws IOException {
        initIfNeeded();
        stringReader.mark(readAheadLimit);
    }
    boolean markSupported() {
        initIfNeeded();
        return stringReader.markSupported();
    }
    int read() throws IOException {
        initIfNeeded();
        return stringReader.read();
    }
    boolean ready() throws IOException {
        initIfNeeded();
        return stringReader.ready();
    }
    void reset() throws IOException {
        initIfNeeded();
        stringReader.reset();
    }
    long skip(long n) throws IOException {
        initIfNeeded();
        return stringReader.skip(n);
    }
}
