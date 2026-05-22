package com.wex.challenge.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import com.wex.challenge.service.TreasuryRatesApi;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class RestClientConfig {

    private static final String BUNDLED_ROOT_ALIAS = "sectigo-r46";
    private static final String BUNDLED_ROOT_PATH = "certs/sectigo-r46.pem";

    @Bean
    public RestClient treasuryRestClient(TreasuryProperties props)
            throws IOException, GeneralSecurityException {

        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(treasurySslContext())
                .connectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    public TreasuryRatesApi treasuryRatesApi(RestClient treasuryRestClient) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(treasuryRestClient))
                .build()
                .createClient(TreasuryRatesApi.class);
    }

    private SSLContext treasurySslContext() throws IOException, GeneralSecurityException {
        KeyStore trustStore = loadDefaultTrustStore();

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream pem = new ClassPathResource(BUNDLED_ROOT_PATH).getInputStream()) {
            X509Certificate root = (X509Certificate) cf.generateCertificate(pem);
            trustStore.setCertificateEntry(BUNDLED_ROOT_ALIAS, root);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }

    private KeyStore loadDefaultTrustStore() throws IOException, GeneralSecurityException {
        Path cacerts = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = Files.newInputStream(cacerts)) {
            ks.load(in, "changeit".toCharArray());
        }
        return ks;
    }
}
