package com.cursoudemy.springboot.webflux.app.models.services;

import com.cursoudemy.springboot.webflux.app.models.documents.Categoria;
import com.cursoudemy.springboot.webflux.app.models.documents.Producto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

public interface ProductoService {
    public Flux<Producto> findAll();

    public Flux<Producto> findAllConDelay(Duration duration);

    public Flux<Producto> findAllConNombreUpperCase();

    public Flux<Producto> findAllConNombreUpperCaseRepeat(Long repeat);

    public Mono<Producto> findById(String id);

    public Mono<Producto> save(Producto producto);

    public Mono<Void> delete(Producto producto);

    public Flux<Categoria> findAllCategoria();

    public Mono<Categoria> findCategoriaById(String id);

    public Mono<Categoria> saveCategoria(Categoria categoria);
}
