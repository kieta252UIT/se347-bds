package com.realestates.backend.controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"https://localhost", "http://localhost:5173"}, allowCredentials = "true")
public class HelloController 
{

    @GetMapping("/hello")
    public String hello() {
        return "Hello from Spring Boot!";
    }
}