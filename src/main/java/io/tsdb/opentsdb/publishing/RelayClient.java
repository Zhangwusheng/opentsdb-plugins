package io.tsdb.opentsdb.publishing;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import net.opentsdb.core.TSDB;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

public class RelayClient {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(RelayClient.class);
    private static final boolean SSL = System.getProperty("ssl") != null;
    private static final String HOST = System.getProperty("host", "127.0.0.1");
    private static final int PORT = Integer.parseInt(System.getProperty("port", "8009"));
    private static RelayClientHandler clientHandler = new RelayClientHandler();
    private EventLoopGroup group = new NioEventLoopGroup();

    public RelayClient(TSDB tsdb) {

        // Configure SSL.
        final SslContext sslCtx;

        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            if (SSL) {
                                try {
                                    final SslContext sslCtx = getSSLContext();
                                    LOGGER.debug("Adding SSL Handler to Pipeline");
                                    p.addLast("ssl", sslCtx.newHandler(ch.alloc(), HOST, PORT));
                                } catch (IOException | GeneralSecurityException e) {
                                    LOGGER.warn("Failed to establish SSL Context");
                                    LOGGER.debug("Failed to establish SSL Context", e);
                                    ch.writeAndFlush("Failed to establish SSH Context");
                                    ch.close();
                                }
                            }
                            p.addLast(clientHandler);
                        }
                    });

            // Make the connection attempt.
            ChannelFuture f = b.connect(HOST, PORT).sync();

            // Wait until the connection is closed.
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

    public void writeMessage(String msg) {
        clientHandler.writeMessage(msg + "\r\n");
    }

    public void shutdown() {
        group.shutdownGracefully();
    }

    private static SslContext getSSLContext() throws IOException, GeneralSecurityException {
        try {
            final String privateKeyFile = "keys/server.pkcs8.key";
            final String certificateFile = "keys/server.crt";
            final String rootCAFile = "keys/rootCA.pem";

            final PrivateKey privateKey = loadPrivateKey(privateKeyFile);
            final X509Certificate certificate = loadX509Cert(certificateFile);
            final X509Certificate rootCA = loadX509Cert(rootCAFile);

            return SslContextBuilder.forClient()
                    .sslProvider(SslProvider.JDK)
                    .trustManager(rootCA)
                    .keyManager(privateKey, certificate)
                    .build();

        } catch (IOException | GeneralSecurityException e) {
            LOGGER.warn("Failed to establish SSL Context");
            LOGGER.debug("Failed to establish SSL Context", e);
            throw e;
        }
    }

    private static InputStream getExternalStream(String filename) {
        try {
            LOGGER.debug("looking for " + filename + " externally");
            InputStream is = new FileInputStream(filename);
            if (is == null) {
                LOGGER.debug("Could not load configuration file from external path: " + filename);
            } else {
                LOGGER.debug("Found it!");
            }
            return is;
        } catch (Throwable e) {
            LOGGER.warn("Could not load configuration file from external path: " + e.getMessage());
            return null;
        }
    }

    public static PrivateKey loadPrivateKey(String fileName)
            throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        PrivateKey key = null;
        InputStream is = null;
        try {
            is = getExternalStream(fileName);
            if (is == null) {
                throw new FileNotFoundException("Key file could not be found via path or classpath");
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder builder = new StringBuilder();
            boolean inKey = false;
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                if (!inKey) {
                    if (line.startsWith("-----BEGIN ") && line.endsWith(" PRIVATE KEY-----")) {
                        inKey = true;
                    }
                } else {
                    if (line.startsWith("-----END ") && line.endsWith(" PRIVATE KEY-----")) {
                        inKey = false;
                        break;
                    }
                    builder.append(line);
                }
            }
            //
            byte[] encoded = DatatypeConverter.parseBase64Binary(builder.toString());
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            key = kf.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException e) {
            LOGGER.error("Could not load Private Key: " + e.getMessage());
            throw e;
        } finally {
            closeSilent(is);
        }
        return key;
    }

    private static void closeSilent(final InputStream is) {
        if (is == null)
            return;
        try {
            is.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * Loads an X.509 certificate from the classpath resources in
     * src/main/resources/keys.
     *
     * @param fileName name of a file in src/main/resources/keys.
     */
    public static X509Certificate loadX509Cert(String fileName) throws CertificateException, IOException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        try (InputStream in = getExternalStream(fileName)) {
            return (X509Certificate) cf.generateCertificate(in);
        }
    }
}
