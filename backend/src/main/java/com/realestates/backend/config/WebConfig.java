package com.realestates.backend.config;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer 
{
    
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) 
    {
        configurer.addPathPrefix("/api", clazz -> 
        {
            if (!clazz.isAnnotationPresent(RestController.class)) 
            {
                return false;
            }
            RequestMapping mapping = clazz.getAnnotation(RequestMapping.class);
            if (mapping == null || mapping.value().length == 0) 
            {
                return true;
            }
            return Arrays.stream(mapping.value())
                    .noneMatch(path -> path.startsWith("/api") || path.startsWith("/auth"));
        });
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) 
    {
        registry.addMapping("/**")
                .allowedOrigins("https://localhost", "http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() 
    {
        return new ForwardedHeaderFilter();
    }
}
