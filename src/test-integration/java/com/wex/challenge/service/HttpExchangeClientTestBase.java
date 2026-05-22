package com.wex.challenge.service;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for HTTP Interface (@HttpExchange) client slices. Builds the declarative
 * proxy over a {@link RestClient} bound to a {@link MockRestServiceServer}, so a
 * test can stub the upstream HTTP exchange without WireMock or a Spring context —
 * exercising the real serialization, URL building and error handling of the
 * client under test.
 */
public abstract class HttpExchangeClientTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    protected MockRestServiceServer server;

    protected <T> T buildClient(Class<T> clientClass) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        server = MockRestServiceServer.bindTo(builder).build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(builder.build()))
                .build()
                .createClient(clientClass);
    }

    protected void mockGet(String urlContains, Object response) {
        server.expect(requestTo(containsString(urlContains)))
                .andRespond(withSuccess(toJson(response), MediaType.APPLICATION_JSON));
    }

    protected void mockGet(String urlContains, HttpStatus status) {
        server.expect(requestTo(containsString(urlContains)))
                .andRespond(withStatus(status));
    }

    protected static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
