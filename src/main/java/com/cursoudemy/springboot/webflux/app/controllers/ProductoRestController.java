package com.cursoudemy.springboot.webflux.app.controllers;

import com.cursoudemy.springboot.webflux.app.models.dao.ProductoDao;
import com.cursoudemy.springboot.webflux.app.models.documents.Producto;
import com.cursoudemy.springboot.webflux.app.models.services.ProductoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.io.File;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/productos")
public class ProductoRestController {
    @Autowired
    private ProductoService service;

    private static final Logger log = LoggerFactory.getLogger(ProductoController.class);

    @Value("${config.uploads.path}")
    private String path;

    @PostMapping("/upload/{id}")
    public Mono<ResponseEntity<Producto>> uploadImage(@PathVariable String id, @RequestPart FilePart file) {
        return service.findById(id).flatMap(p -> {
                    p.setFoto(UUID.randomUUID().toString() + "-" + file.filename()
                            .replaceAll(" ", "")
                            .replaceAll(":", "")
                            .replaceAll("/\\/", ""));

                    return file.transferTo(new File(path + p.getFoto())).then(service.save(p));
                }).map(p -> ResponseEntity.ok(p))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Mono<ResponseEntity<Flux<Producto>>> index() {
        return Mono.just(
                ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(service.findAll())
        );
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Producto>> show(@PathVariable String id) {
        return service.findById(id).map(p -> ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(p)
        ).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> save(@Valid @RequestBody Mono<Producto> monoProducto) {
        Map<String, Object> response = new HashMap<>();
        return monoProducto.flatMap(producto -> {
            if (producto.getCreatedAt() == null) producto.setCreatedAt(new Date());

            return service.save(producto)
                    .map(p -> {
                                response.put("producto", p);
                                response.put("mensaje", "Producto creado con exito");
                                response.put("timestamp", new Date());
                                return ResponseEntity.created(URI.create("/api/productos/".concat(p.getId())))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(response);
                            }
                    );
        }).onErrorResume(throwable -> Mono.just(throwable).cast(WebExchangeBindException.class)
                .flatMap(error -> Mono.just(error.getFieldErrors()))
                .flatMapMany(Flux::fromIterable)
                .map(fieldError -> "El campo " + fieldError.getField() + " " + fieldError.getDefaultMessage())
                .collectList()
                .flatMap(list -> {
                    response.put("errors", list);
                    response.put("status", HttpStatus.BAD_REQUEST.value());
                    response.put("timestamp", new Date());
                    return Mono.just(ResponseEntity.badRequest().body(response));
                })
        );

    }

    @PostMapping("/v2")
    public Mono<ResponseEntity<Producto>> saveWithImage(Producto producto, @RequestPart FilePart file) {
        if (producto.getCreatedAt() == null) producto.setCreatedAt(new Date());
        if (!file.filename().isEmpty()) producto.setFoto(UUID.randomUUID().toString() + "-" + file.filename()
                .replaceAll(" ", "_")
                .replaceAll(":", "")
                .replaceAll("/\\/", "")
                .replaceAll("/", ""));

        return file.transferTo(new File(path + producto.getFoto()))
                .then(service.save(producto))
                .map(p -> ResponseEntity
                        .created(URI.create("/api/productos/".concat(p.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(p)
                );
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Producto>> update(@RequestBody Producto producto, @PathVariable String id) {
        return service.findById(id).flatMap(p -> {
            p.setNombre(producto.getNombre());
            p.setPrecio(producto.getPrecio());
            p.setCategoria(producto.getCategoria());

            return service.save(p);
        }).map(p -> ResponseEntity.created(URI.create("/api/productos/".concat(p.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .body(p)
        ).defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String id) {
        return service.findById(id).flatMap(p -> {
            return service.delete(p).then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
        }).defaultIfEmpty(new ResponseEntity<Void>(HttpStatus.NOT_FOUND));
    }
}
