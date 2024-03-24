package com.oj.ojcodesandbox.docker.utils;

import com.github.dockerjava.core.DefaultDockerClientConfig;

public class DockerConfig {

    private static final String DockerHost = "tcp://42.194.235.114:2376";

    private static final String API_VERSION = "1.43";

    public static DefaultDockerClientConfig getConfig() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .withDockerHost(DockerHost)
                .withDockerTlsVerify(true)
                .withDockerCertPath("E:\\docker")
                .withApiVersion(API_VERSION)
                .build();
        return config;
    }
}
