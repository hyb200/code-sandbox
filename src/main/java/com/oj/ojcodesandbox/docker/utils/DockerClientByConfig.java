package com.oj.ojcodesandbox.docker.utils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.time.Duration;
import java.util.List;

public class DockerClientByConfig {
    public DockerClient connect() {
        DefaultDockerClientConfig config = DockerConfig.getConfig();

        // 创建DockerHttpClient
        DockerHttpClient httpClient = new ApacheDockerHttpClient
                .Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);

        return dockerClient;
    }

    public static void main(String[] args) {
        DockerClientByConfig dockerClientByConfig = new DockerClientByConfig();
        DockerClient dockerClient = dockerClientByConfig.connect();

        List<Image> images = dockerClient.listImagesCmd().withShowAll(true).exec();
        for (Image image : images) {
            System.out.println(image.toString());
        }
    }
}
