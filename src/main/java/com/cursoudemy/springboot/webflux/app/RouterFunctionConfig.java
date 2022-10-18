package com.cursoudemy.springboot.webflux.app;

import com.cursoudemy.springboot.webflux.app.handler.ProductHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterFunctionConfig {
    @Bean
    public RouterFunction<ServerResponse> routes(ProductHandler handler) {
        return route(GET("/api/v2/productos").or(GET("/api/v3/productos")), handler::index)
                .andRoute(GET("/api/v2/productos/{id}").and(contentType(MediaType.APPLICATION_JSON)), handler::show)
                .andRoute(POST("/api/v2/productos").and(contentType(MediaType.APPLICATION_JSON)), handler::save)
                .andRoute(PUT("/api/v2/productos/{id}").and(contentType(MediaType.APPLICATION_JSON)), handler::update)
                .andRoute(DELETE("/api/v2/productos/{id}").and(contentType(MediaType.APPLICATION_JSON)), handler::delete)
                .andRoute(POST("/api/v2/productos/upload/{id}"), handler::uploadImage)
                .andRoute(POST("/api/v2/productos/save-with-image"), handler::saveWithImage);

    }
}
