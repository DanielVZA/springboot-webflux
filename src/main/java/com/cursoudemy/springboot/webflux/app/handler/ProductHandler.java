package com.cursoudemy.springboot.webflux.app.handler;

import com.cursoudemy.springboot.webflux.app.models.documents.Categoria;
import com.cursoudemy.springboot.webflux.app.models.documents.Producto;
import com.cursoudemy.springboot.webflux.app.models.services.ProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.net.URI;
import java.util.Date;
import java.util.UUID;

@Component
public class ProductHandler {
    @Autowired
    private ProductoService service;

    @Value("${config.uploads.path}")
    private String path;

    @Autowired
    private Validator validator;

    public Mono<ServerResponse> index(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.findAll(), Producto.class);
    }

    public Mono<ServerResponse> show(ServerRequest request) {
        String id = request.pathVariable("id");
        return service.findById(id)
                .flatMap(p -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(p))
                        .switchIfEmpty(ServerResponse.notFound().build())
                );
    }

    public Mono<ServerResponse> save(ServerRequest request) {
        Mono<Producto> producto = request.bodyToMono(Producto.class);
        return producto.flatMap(p -> {
            Errors errors = new BeanPropertyBindingResult(p, Producto.class.getName());
            validator.validate(p, errors);

            if (errors.hasErrors()) return Flux.fromIterable(errors.getFieldErrors())
                    .map(fieldError -> "El campo " + fieldError.getField() + " " + fieldError.getDefaultMessage())
                    .collectList()
                    .flatMap(list -> ServerResponse.badRequest().body(BodyInserters.fromValue(list)));

            if (p.getCreatedAt() == null) p.setCreatedAt(new Date());
            return service.save(p).flatMap(productoDb -> ServerResponse
                    .created(URI.create("/api/v2/productos/".concat(productoDb.getId())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(productoDb)));
        });
    }

    public Mono<ServerResponse> update(ServerRequest request) {
        Mono<Producto> producto = request.bodyToMono(Producto.class);
        String id = request.pathVariable("id");
        Mono<Producto> productoDb = service.findById(id);

        return productoDb.zipWith(producto, (db, req) -> {
            db.setNombre(req.getNombre());
            db.setPrecio(req.getPrecio());
            db.setCategoria(req.getCategoria());

            return db;
        }).flatMap(p -> ServerResponse
                .created(URI.create("/api/v2/productos/".concat(p.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .body(service.save(p), Producto.class)
        ).switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> delete(ServerRequest request) {
        String id = request.pathVariable("id");
        Mono<Producto> productoDb = service.findById(id);

        return productoDb.flatMap(p -> service.delete(p).then(ServerResponse.noContent().build()))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> uploadImage(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.multipartData().map(multipart -> multipart.toSingleValueMap().get("file"))
                .cast(FilePart.class)
                .flatMap(file -> service.findById(id)
                        .flatMap(p -> {
                            p.setFoto(UUID.randomUUID().toString() + "-" + file.filename()
                                    .replaceAll(" ", "_")
                                    .replaceAll(":", "")
                                    .replaceAll("/\\/", "")
                                    .replaceAll("/", ""));
                            return file.transferTo(new File(path + p.getFoto())).then(service.save(p));
                        })).flatMap(p -> ServerResponse.created(URI.create("/api/v2/productos/".concat(p.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(p))
                ).switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> saveWithImage(ServerRequest request) {
        Mono<Producto> productoMono = request.multipartData()
                .map(multipart -> {
                    FormFieldPart nombre = (FormFieldPart) multipart.toSingleValueMap().get("nombre");
                    FormFieldPart precio = (FormFieldPart) multipart.toSingleValueMap().get("precio");
                    FormFieldPart categoriaId = (FormFieldPart) multipart.toSingleValueMap().get("categoria.id");
                    FormFieldPart categoriaNombre = (FormFieldPart) multipart.toSingleValueMap().get("categoria.nombre");

                    Categoria categoria = new Categoria(categoriaNombre.value());
                    categoria.setId(categoriaId.value());
                    return new Producto(nombre.value(), Double.parseDouble(precio.value()), categoria);
                });

        return request.multipartData().map(multipart -> multipart.toSingleValueMap().get("file"))
                .cast(FilePart.class)
                .flatMap(file -> productoMono
                        .flatMap(p -> {
                            p.setFoto(UUID.randomUUID().toString() + "-" + file.filename()
                                    .replaceAll(" ", "_")
                                    .replaceAll(":", "")
                                    .replaceAll("/\\/", "")
                                    .replaceAll("/", ""));
                            p.setCreatedAt(new Date());
                            return file.transferTo(new File(path + p.getFoto())).then(service.save(p));
                        })).flatMap(p -> ServerResponse.created(URI.create("/api/v2/productos/".concat(p.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(p))
                );
    }
}
