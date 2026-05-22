package com.wex.challenge.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared base for the web-layer integration slices. Holds the auto-configured
 * {@link MockMvc} so each {@code @WebMvcTest} can drive it through a
 * {@code RestTestClient} without re-declaring the wiring.
 */
public abstract class WebTestBase {

    @Autowired
    protected MockMvc mockMvc;
}
