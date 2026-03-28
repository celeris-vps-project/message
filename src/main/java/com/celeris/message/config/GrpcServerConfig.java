package com.celeris.message.config;

import org.springframework.context.annotation.Configuration;

/**
 * gRPC server configuration.
 * The grpc-server-spring-boot-starter auto-configures the gRPC server
 * based on application.yml properties (grpc.server.port).
 * Add custom interceptors or server configuration here if needed.
 */
@Configuration
public class GrpcServerConfig {
    // Auto-configured by grpc-server-spring-boot-starter
}
