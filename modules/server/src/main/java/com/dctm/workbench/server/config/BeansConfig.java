package com.dctm.workbench.server.config;

import com.dctm.workbench.core.SessionFactory;
import com.dctm.workbench.dfc.live.LiveDfcSessionFactory;
import com.dctm.workbench.dfc.mock.MockDfcSessionFactory;
import com.dctm.workbench.dfs.DfsSessionFactory;
import com.dctm.workbench.otcs.mock.MockOtcsSessionFactory;
import com.dctm.workbench.otcs.rest.OtcsRestSessionFactory;
import com.dctm.workbench.rest.DctmRestSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class BeansConfig {

    @Bean
    MockDfcSessionFactory mockDfcSessionFactory() {
        return new MockDfcSessionFactory();
    }

    @Bean
    MockOtcsSessionFactory mockOtcsSessionFactory() {
        return new MockOtcsSessionFactory();
    }

    @Bean
    DctmRestSessionFactory dctmRestSessionFactory() {
        return new DctmRestSessionFactory();
    }

    @Bean
    OtcsRestSessionFactory otcsRestSessionFactory() {
        return new OtcsRestSessionFactory();
    }

    @Bean
    LiveDfcSessionFactory liveDfcSessionFactory() {
        return new LiveDfcSessionFactory();
    }

    @Bean
    DfsSessionFactory dfsSessionFactory() {
        return new DfsSessionFactory();
    }

    @Bean
    CompositeSessionFactory compositeSessionFactory(List<SessionFactory> factories) {
        return new CompositeSessionFactory(factories);
    }

    @Bean
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                        .allowedMethods("*")
                        .allowedHeaders("*");
            }
        };
    }
}
