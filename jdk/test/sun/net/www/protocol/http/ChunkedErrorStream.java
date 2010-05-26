/*
 * Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6488669 6595324
 * @run main/othervm ChunkedErrorStream
 * @summary Chunked ErrorStream tests
 */

import java.net.*;
import java.io.*;
import com.sun.net.httpserver.*;

/**
 * Part 1: 6488669
 * 1) Http server that responds with an error code (>=400)
 *    and a chunked response body. It also indicates that
 *    the connection will be closed.
 * 2) Client sends request to server and tries to
 *    getErrorStream(). Some data must be able to be read
 *    from the errorStream.
 *
 * Part 2: 6595324
 * 1) Http server that responds with an error code (>=400)
 *    and a chunked response body greater than
 *    sun.net.http.errorstream.bufferSize, 4K + 10 bytes.
 * 2) Client sends request to server and tries to
 *    getErrorStream(). 4K + 10 bytes must be read from
 *    the errorStream.
 */

public class ChunkedErrorStream
{
    com.sun.net.httpserver.HttpServer httpServer;

    static {
        // Enable ErrorStream buffering
        System.getProperties().setProperty("sun.net.http.errorstream.enableBuffering", "true");

        // No need to set this as 4K is the default
        // System.getProperties().setProperty("sun.net.http.errorstream.bufferSize", "4096");
    }

    public static void main(String[] args) {
        new ChunkedErrorStream();
    }

    public ChunkedErrorStream() {
        try {
            startHttpServer();
            doClient();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }  finally {
            httpServer.stop(1);
        }

    }

    void doClient() {
        for (int times=0; times<2; times++) {
            HttpURLConnection uc = null;
            try {
                InetSocketAddress address = httpServer.getAddress();
                String URLStr = "http://localhost:" + address.getPort() + "/test/";
                if (times == 0) {
                    URLStr += 6488669;
                } else {
                    URLStr += 6595324;
                }

                System.out.println("Trying " + URLStr);
                URL url = new URL(URLStr);
                uc = (HttpURLConnection)url.openConnection();
                uc.getInputStream();

                throw new RuntimeException("Failed: getInputStream should throw and IOException");
            }  catch (IOException e) {
                // This is what we expect to happen.
                InputStream es = uc.getErrorStream();
                byte[] ba = new byte[1024];
                int count = 0, ret;
                try {
                    while ((ret = es.read(ba)) != -1)
                        count += ret;
                    es.close();
                } catch  (IOException ioe) {
                    ioe.printStackTrace();
                }

                if (count == 0)
                    throw new RuntimeException("Failed: ErrorStream returning 0 bytes");

                if (times == 1 && count != (4096+10))
                    throw new RuntimeException("Failed: ErrorStream returning " + count +
                                                 " bytes. Expecting " + (4096+10));

                System.out.println("Read " + count + " bytes from the errorStream");
            }
        }
    }

    /**
     * Http Server
     */
    void startHttpServer() throws IOException {
        httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);

        // create HttpServer context
        HttpContext ctx1 = httpServer.createContext("/test/6488669", new Handler6488669());
        HttpContext ctx2 = httpServer.createContext("/test/6595324", new Handler6595324());

        httpServer.start();
    }

    class Handler6488669 implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            InputStream is = t.getRequestBody();
            byte[] ba = new byte[1024];
            while (is.read(ba) != -1);
            is.close();

            Headers resHeaders = t.getResponseHeaders();
            resHeaders.add("Connection", "close");
            t.sendResponseHeaders(404, 0);
            OutputStream os = t.getResponseBody();

            // actual data doesn't matter. Just send 2K worth.
            byte b = 'a';
            for (int i=0; i<2048; i++)
                os.write(b);

            os.close();
            t.close();
        }
    }

    class Handler6595324 implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            InputStream is = t.getRequestBody();
            byte[] ba = new byte[1024];
            while (is.read(ba) != -1);
            is.close();

            t.sendResponseHeaders(404, 0);
            OutputStream os = t.getResponseBody();

            // actual data doesn't matter. Just send more than 4K worth
            byte b = 'a';
            for (int i=0; i<(4096+10); i++)
                os.write(b);

            os.close();
            t.close();
        }
    }
}
