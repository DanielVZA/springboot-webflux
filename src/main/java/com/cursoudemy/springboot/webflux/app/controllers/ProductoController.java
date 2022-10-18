package com.cursoudemy.springboot.webflux.app.controllers;

import com.cursoudemy.springboot.webflux.app.models.documents.Categoria;
import com.cursoudemy.springboot.webflux.app.models.documents.Producto;
import com.cursoudemy.springboot.webflux.app.models.services.ProductoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.spring5.context.webflux.ReactiveDataDriverContextVariable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@SessionAttributes("producto")
@Controller
@RequestMapping("/productos")
public class ProductoController {
    @Value("${config.uploads.path}")
    private String path;
    @Autowired
    private ProductoService service;

    private static final Logger log = LoggerFactory.getLogger(ProductoController.class);

    @ModelAttribute("categorias")
    public Flux<Categoria> categorias() {
        return service.findAllCategoria();
    }

    @GetMapping("/uploads/img/{nombreFoto:.+}")
    public Mono<ResponseEntity<Resource>> getFoto(@PathVariable String nombreFoto) throws MalformedURLException {
        Path ruta = Paths.get(path).resolve(nombreFoto).toAbsolutePath();
        log.info(ruta.toString());
        Resource imagen = new UrlResource(ruta.toUri());
        log.info(imagen.toString());
        return Mono.just(ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + imagen.getFilename() + "\"")
                .body(imagen)
        );
    }

    @GetMapping("/show/{id}")
    public Mono<String> show(Model model, @PathVariable String id) {
        return service.findById(id)
                .doOnNext(p -> {
                    model.addAttribute("titulo", "Detalle Producto");
                    model.addAttribute("producto", p);
                }).switchIfEmpty(Mono.just(new Producto()))
                .flatMap(p -> {
                    if (p.getId() == null) return Mono.error(new InterruptedException("No existe el producto"));
                    return Mono.just(p);
                }).then(Mono.just("show"))
                .onErrorResume(ex -> Mono.just("redirect:/productos?error=No+existe+el+producto"));
    }

    @GetMapping
    public Mono<String> index(Model model) {
        Flux<Producto> productos = service.findAllConNombreUpperCase();
        productos.subscribe(p -> log.info(p.getNombre()));

        model.addAttribute("productos", productos);
        model.addAttribute("titulo", "Listado de Productos");

        return Mono.just("index");
    }

    @GetMapping("/create")
    public Mono<String> create(Model model) {
        model.addAttribute("producto", new Producto());
        model.addAttribute("titulo", "Crear Producto");

        return Mono.just("create");
    }

    @GetMapping("/V2/edit/{id}")
    public Mono<String> editV2(@PathVariable String id, Model model) {
        return service.findById(id)
                .doOnNext(p -> {
                    model.addAttribute("producto", p);
                    model.addAttribute("titulo", "Editar Producto");
                    log.info(p.getNombre());
                })
                .defaultIfEmpty(new Producto())
                .flatMap(p -> {
                    if (p.getId() == null) return Mono.error(new InterruptedException("No existe el producto"));
                    return Mono.just(p);
                })
                .then(Mono.just("create"))
                .onErrorResume(ex -> Mono.just("redirect:/productos?error=no+existe+el+producto"));
//                .thenReturn("create");
    }

    @GetMapping("/edit/{id}")
    public Mono<String> edit(@PathVariable String id, Model model) {
        Mono<Producto> productoMono = service.findById(id)
                .doOnNext(p -> log.info(p.getNombre()))
                .defaultIfEmpty(new Producto());

        model.addAttribute("producto", productoMono);
        model.addAttribute("titulo", "Editar Producto");

        return Mono.just("create");
    }

    @PostMapping
    public Mono<String> save(@Valid Producto producto, BindingResult result, Model model, @RequestPart(name = "file") FilePart file) {
//        status.setComplete();
        if (result.hasErrors()) {
            model.addAttribute("titulo", "Errores en formulario producto");
            log.info(result.toString());
            return Mono.just("create");
        }

        String action = producto.getId() != null ? "actualizado" : "creado";
        Mono<Categoria> categoria = service.findCategoriaById(producto.getCategoria().getId());

        return categoria.flatMap(c -> {
                    if (producto.getCreatedAt() == null) producto.setCreatedAt(new Date());
                    if (!file.filename().isEmpty()) producto.setFoto(UUID.randomUUID().toString() + "-" + file.filename()
                            .replaceAll("_", "")
                            .replaceAll(":", "")
                            .replaceAll("/\\/", "")
                            .replaceAll("/", ""));

                    producto.setCategoria(c);
                    return service.save(producto);
                }).doOnNext(p -> {
                    log.info("Categoria asignada: " + p.getCategoria().getNombre());
                    log.info("Producto Guardado: " + p.getNombre() + " ID: " + p.getId());
                }).flatMap(p -> {
                    if (!file.filename().isEmpty()) return file.transferTo(new File(path + p.getFoto()));
                    return Mono.empty();
                })
                .thenReturn("redirect:/productos?status=Producto+" + action + "+con+exito");
//        }).then(Mono.just("redirect:/productos");
    }

    @GetMapping("/delete/{id}")
    public Mono<String> delete(@PathVariable String id) {
        return service.findById(id)
                .defaultIfEmpty(new Producto())
                .flatMap(p -> {
                    if (p.getId() == null)
                        return Mono.error(new InterruptedException("No existe el producto a eliminar!"));

                    return Mono.just(p);
                })
                .flatMap(service::delete)
                .then(Mono.just("redirect:/productos?status=Producto+eliminado+con+exito"))
                .onErrorResume(ex -> Mono.just("redirect:/productos?error=No+existe+el+producto+a+eliminar"));
    }

    @GetMapping("/index-datadriver")
    public String indexDataDriver(Model model) {
        Flux<Producto> productos = service.findAllConDelay(Duration.ofSeconds(1));

        productos.subscribe(p -> log.info(p.getNombre()));

        model.addAttribute("productos", new ReactiveDataDriverContextVariable(productos, 2));
        model.addAttribute("titulo", "Listado de Productos");

        return "index";
    }

    @GetMapping("/index-full")
    public String indexFull(Model model) {
        Flux<Producto> productos = service.findAllConNombreUpperCaseRepeat(5000L);

        model.addAttribute("productos", new ReactiveDataDriverContextVariable(productos, 2));
        model.addAttribute("titulo", "Listado de Productos");

        return "index";
    }

    @GetMapping("/index-chunked")
    public String indexChunked(Model model) {
        Flux<Producto> productos = service.findAllConNombreUpperCaseRepeat(5000L);

        model.addAttribute("productos", new ReactiveDataDriverContextVariable(productos, 2));
        model.addAttribute("titulo", "Listado de Productos");

        return "indexChunked";
    }
}
